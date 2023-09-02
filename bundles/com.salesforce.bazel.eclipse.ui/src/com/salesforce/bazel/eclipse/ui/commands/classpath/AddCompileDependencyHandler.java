package com.salesforce.bazel.eclipse.ui.commands.classpath;

import static com.salesforce.bazel.eclipse.core.model.discovery.classpath.util.TypeLocator.findBazelInfo;
import static com.salesforce.bazel.eclipse.ui.utils.JavaSearchUtil.createScopeIncludingAllWorkspaceProjectsButSelected;
import static java.lang.String.format;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.internal.ui.util.BusyIndicatorRunnableContext;
import org.eclipse.jdt.ui.IJavaElementSearchConstants;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.ISources;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.idea.blaze.base.model.primitives.Label;
import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.edits.AddDependenciesJob;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.eclipse.ui.commands.BaseBazelProjectHandler;
import com.salesforce.bazel.eclipse.ui.utils.BazelProjectUtilitis;

@SuppressWarnings("restriction")
public class AddCompileDependencyHandler extends BaseBazelProjectHandler {

    private static Logger LOG = LoggerFactory.getLogger(AddCompileDependencyHandler.class);

    public static final String COMMAND_ADD_COMPILE_DEPENDENCY =
            "com.salesforce.bazel.eclipse.ui.commands.classpath.addCompileDependency";

    boolean collectLabelAndClasspathEntryForType(IType type, Set<Label> dependencyLabels,
            Set<ClasspathEntry> newClasspathEntries) throws CoreException {
        var bazelInfo = findBazelInfo(type);
        if (bazelInfo == null) {
            return false;
        }

        dependencyLabels.add(bazelInfo.originLabel());
        newClasspathEntries.add(bazelInfo.classpathEntry());
        return true;
    }

    void collectLabelAndClasspathEntryForTypeDependencies(IType type, Set<Label> labels,
            Set<ClasspathEntry> classpathEntries, BazelWorkspace bazelWorkspace, SubMonitor monitor)
            throws CoreException {
        Set<String> processedTypes = new HashSet<>();

        var parser = ASTParser.newParser(AST.getJLSLatest());

        if (type.getJavaProject() != null) {
            // resolve everything from the type's Java project if possible
            parser.setProject(type.getJavaProject());
        } else {
            // use the workspace project (assuming it's a 3pp library)
            parser.setProject(JavaCore.create(bazelWorkspace.getBazelProject().getProject()));
        }

        parser.setIgnoreMethodBodies(true);
        parser.setResolveBindings(true);
        var bindings = parser.createBindings(new IJavaElement[] { type }, monitor);

        for (IBinding binding : bindings) {
            collectLabelAndClasspathEntryFromTypeBinding(
                (ITypeBinding) binding,
                labels,
                classpathEntries,
                processedTypes,
                monitor);
        }
    }

    private void collectLabelAndClasspathEntryFromMethodBinding(IMethodBinding binding, Set<Label> labels,
            Set<ClasspathEntry> classpathEntries, Set<String> processedTypes, SubMonitor monitor) throws CoreException {
        if (monitor.isCanceled()) {
            throw new OperationCanceledException();
        }

        if (Modifier.isPrivate(binding.getModifiers()) || Modifier.isDefault(binding.getModifiers())) {
            return;
        }

        for (ITypeBinding parameterType : binding.getParameterTypes()) {
            collectLabelAndClasspathEntryFromTypeBinding(
                parameterType,
                labels,
                classpathEntries,
                processedTypes,
                monitor);
        }

        collectLabelAndClasspathEntryFromTypeBinding(
            binding.getReturnType(),
            labels,
            classpathEntries,
            processedTypes,
            monitor);
    }

    private void collectLabelAndClasspathEntryFromTypeBinding(ITypeBinding binding, Set<Label> labels,
            Set<ClasspathEntry> classpathEntries, Set<String> processedTypes, SubMonitor monitor) throws CoreException {
        if (monitor.isCanceled()) {
            throw new OperationCanceledException();
        }

        if (binding.isAnonymous() || binding.isPrimitive() || binding.isNullType() || binding.isGenericType()
                || binding.isTypeVariable() || binding.isCapture() || binding.isWildcardType()
                || binding.isRecovered()) {
            return;
        }

        if (binding.isArray()) {
            collectLabelAndClasspathEntryFromTypeBinding(
                binding.getElementType(),
                labels,
                classpathEntries,
                processedTypes,
                monitor);
            return;
        }

        if (binding.getPackage().getName().startsWith("java.")) {
            return;
        }

        if (processedTypes.contains(binding.getQualifiedName())) {
            return;
        }

        processedTypes.add(binding.getQualifiedName());
        monitor.subTask(binding.getQualifiedName());

        var collected =
                collectLabelAndClasspathEntryForType((IType) binding.getJavaElement(), labels, classpathEntries);
        if (!collected) {
            return; // abort early
        }

        for (ITypeBinding interfaceBinding : binding.getInterfaces()) {
            collectLabelAndClasspathEntryFromTypeBinding(
                interfaceBinding,
                labels,
                classpathEntries,
                processedTypes,
                monitor);
        }

        var superclass = binding.getSuperclass();
        if (superclass != null) {
            collectLabelAndClasspathEntryFromTypeBinding(superclass, labels, classpathEntries, processedTypes, monitor);
        }

        for (ITypeBinding typeParameter : binding.getTypeParameters()) {
            collectLabelAndClasspathEntryFromTypeBinding(
                typeParameter,
                labels,
                classpathEntries,
                processedTypes,
                monitor);
        }

        for (IMethodBinding methodBinding : binding.getDeclaredMethods()) {
            collectLabelAndClasspathEntryFromMethodBinding(
                methodBinding,
                labels,
                classpathEntries,
                processedTypes,
                monitor);
        }
    }

    @Override
    protected Job createJob(IProject project, ExecutionEvent event) throws CoreException {
        var activeShell = HandlerUtil.getActiveShell(event);

        var bazelProject = BazelCore.create(project);

        Set<Label> dependencyLabels = new LinkedHashSet<>();
        Set<ClasspathEntry> newClasspathEntries = new LinkedHashSet<>();

        IRunnableContext context = new BusyIndicatorRunnableContext();
        try {
            var scope = createScopeIncludingAllWorkspaceProjectsButSelected(bazelProject);
            var style = IJavaElementSearchConstants.CONSIDER_ALL_TYPES;
            var dialog = JavaUI.createTypeDialog(activeShell, context, scope, style, false, "");
            dialog.setTitle("Type Selection");
            dialog.setMessage("Choose type name:");
            if (dialog.open() == Window.OK) {
                var type = (IType) dialog.getResult()[0];

                collectLabelAndClasspathEntryForType(type, dependencyLabels, newClasspathEntries);

                collectLabelAndClasspathEntryForTypeDependencies(
                    type,
                    dependencyLabels,
                    newClasspathEntries,
                    bazelProject.getBazelWorkspace(),
                    null);

                if (!dependencyLabels.isEmpty()) {
                    return new AddDependenciesJob(bazelProject, dependencyLabels, newClasspathEntries);
                }

                MessageDialog.openWarning(
                    activeShell,
                    "Not supported!",
                    format("Unable to find source of '%s'! Is it located within a Bazel project?", type));

                return null;
            }
        } catch (CoreException e) {
            LOG.error("Add Dependency Failed: Error collecting dependency information. {}", e.getMessage(), e);
            MessageDialog.openError(activeShell, "Add Dependency Failed", "Please check the logs for more details!");
        }

        return null;
    }

    @Override
    public void setEnabled(Object evaluationContext) {
        var enabled = false;
        if ((evaluationContext instanceof IEvaluationContext context)) {
            var object = context.getVariable(ISources.ACTIVE_WORKBENCH_WINDOW_NAME);
            if (object instanceof IWorkbenchWindow) {
                var selectedProjects = BazelProjectUtilitis.findSelectedProjects((IWorkbenchWindow) object);
                enabled = selectedProjects.size() == 1;
            }
        }
        setBaseEnabled(enabled);
    }

}
