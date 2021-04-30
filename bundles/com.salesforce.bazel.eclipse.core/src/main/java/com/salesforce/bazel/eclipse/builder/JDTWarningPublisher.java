package com.salesforce.bazel.eclipse.builder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;

import com.salesforce.bazel.sdk.model.BazelProblem;

/**
 * This class publishes JDT based code warnings to the Problems View.
 *
 * @author stoens
 * @since Summer 2020
 *
 */
class JDTWarningPublisher implements IElementChangedListener {

    // maps a project name to a map of filePath -> BazelProblems in that file
    private final ConcurrentHashMap<String, Map<String, List<BazelProblem>>> projectNameToProblems =
            new ConcurrentHashMap<>();

    @Override
    public void elementChanged(ElementChangedEvent event) {
        if (event.getType() == ElementChangedEvent.POST_RECONCILE) {
            IJavaElementDelta delta = event.getDelta();
            CompilationUnit ast = delta.getCompilationUnitAST();
            if (ast != null) {
                List<BazelProblem> warnings = getWarnings(ast);
                IJavaElement element = delta.getElement();
                String filePath = getFilePath(element);
                if (filePath != null) {
                    IProject project = element.getJavaProject().getProject();
                    Map<String, List<BazelProblem>> filePathToWarnings = new HashMap<>();
                    filePathToWarnings.put(filePath, warnings);
                    projectNameToProblems.merge(project.toString(), filePathToWarnings, (currentValue, newValue) -> {
                        currentValue.put(filePath, warnings);
                        return currentValue;
                    });
                }
            }
        }
    }

    void publish(Collection<IProject> projects, IProgressMonitor monitor) {
        for (IProject project : projects) {
            Map<String, List<BazelProblem>> filePathToWarnings = projectNameToProblems.remove(project.toString());
            if (filePathToWarnings != null) {
                for (String filePath : filePathToWarnings.keySet()) {
                    String ownerId = this.getClass().getName() + "__" + filePath;
                    BazelProblemMarkerManager mgr = new BazelProblemMarkerManager(ownerId);
                    mgr.clearAndPublish(filePathToWarnings.get(filePath), project, monitor);
                }
            }
        }
    }

    private static List<BazelProblem> getWarnings(CompilationUnit ast) {
        IProblem[] problems = ast.getProblems();
        List<BazelProblem> warnings = new ArrayList<>();
        for (IProblem problem : problems) {
            if (!problem.isWarning()) {
                continue;
            }
            String path = new String(problem.getOriginatingFileName());
            path = removeLeadingProjectName(path);
            warnings.add(BazelProblem.createWarning(path, problem.getSourceLineNumber(), problem.getMessage()));

        }
        return warnings;
    }

    private static String getFilePath(IJavaElement el) {
        IPath path;
        try {
            path = el.getCorrespondingResource().getFullPath();
        } catch (JavaModelException ex) {
            return null;
        }
        return removeLeadingProjectName(path.toOSString());
    }

    private static String removeLeadingProjectName(String path) {
        if (path.startsWith(File.separator)) {
            path = path.substring(1);
        }
        int i = path.indexOf(File.separator);
        if (i != -1) {
            path = path.substring(i + 1);
        }
        return path;
    }
}
