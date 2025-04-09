package com.salesforce.bazel.eclipse.core.model.discovery;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.eclipse.core.runtime.SubMonitor.SUPPRESS_ALL_LABELS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.salesforce.bazel.eclipse.core.classpath.BazelClasspathScope;
import com.salesforce.bazel.eclipse.core.classpath.CompileAndRuntimeClasspath;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspaceBlazeInfo;
import com.salesforce.bazel.eclipse.core.util.trace.TracingSubMonitor;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects.OutputGroup;
import com.salesforce.bazel.sdk.command.BazelBuildWithIntelliJAspectsCommand;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * Default implementation of {@link TargetProvisioningStrategy} which provisions a single project per supported target.
 * <p>
 * <ul>
 * <li>One Eclipse project is created per supported <code>java_*</code> target per package.</li>
 * <li>The build path is setup specifically for that target, allowing for best support in the IDE.</li>
 * <li>Projects are created in a project area (<code>.eclipse/projects</code> folder inside the workspace) and files are
 * created as links. This makes SCM not really functional for these.</li>
 * <li>Targets inside the root (empty) package <code>//:*</code> are supported.</li>
 * </ul>
 * </p>
 *
 * @since 2.0
 */
public class ProjectPerTargetProvisioningStrategy extends BaseProvisioningStrategy {

    private static Logger LOG = LoggerFactory.getLogger(ProjectPerTargetProvisioningStrategy.class);

    public static final String STRATEGY_NAME = "project-per-target";

    @Override
    public Map<BazelProject, CompileAndRuntimeClasspath> computeClasspaths(Collection<BazelProject> bazelProjects,
            BazelWorkspace workspace, BazelClasspathScope scope, IProgressMonitor progress) throws CoreException {
        LOG.debug("Computing classpath for projects: {}", bazelProjects);
        try {
            var monitor = SubMonitor.convert(progress, "Computing Bazel project classpaths", 1 + bazelProjects.size());

            List<BazelLabel> targetsToBuild = new ArrayList<>(bazelProjects.size());
            for (BazelProject bazelProject : bazelProjects) {
                monitor.checkCanceled();

                if (!bazelProject.isTargetProject()) {
                    throw new CoreException(
                            Status.error(
                                format(
                                    "Unable to compute classpath for project '%s'. Please check the setup. This is not a Bazel target project created by the project per target strategy.",
                                    bazelProjects)));
                }

                targetsToBuild.add(bazelProject.getBazelTarget().getLabel());
            }

            var workspaceRoot = workspace.getLocation().toPath();

            var availableDependencies = queryForDepsWithClasspathDepth(workspace, targetsToBuild);

            // run the aspect to compute all required information
            var aspects = workspace.getParent().getModelManager().getIntellijAspects();
            var languages = Set.of(LanguageClass.JAVA);
            var onlyDirectDeps = workspace.getBazelProjectView().deriveTargetsFromDirectories();
            var outputGroups = Set.of(OutputGroup.INFO, OutputGroup.RESOLVE);
            var outputGroupNames = aspects.getOutputGroupNames(outputGroups, languages, onlyDirectDeps);
            if (scope == BazelClasspathScope.RUNTIME_CLASSPATH) {
                outputGroupNames = new HashSet<>(outputGroupNames);
                outputGroupNames.add(IntellijAspects.OUTPUT_GROUP_JAVA_RUNTIME_CLASSPATH);
            }
            var command = new BazelBuildWithIntelliJAspectsCommand(
                    workspaceRoot,
                    targetsToBuild,
                    outputGroupNames,
                    aspects,
                    new BazelWorkspaceBlazeInfo(workspace),
                    "Running build with IntelliJ aspects to collect classpath information");

            // sync_flags
            command.addCommandArgs(workspace.getBazelProjectView().syncFlags());

            monitor.subTask("Running Bazel build with aspects");
            var result = workspace.getCommandExecutor()
                    .runDirectlyWithinExistingWorkspaceLock(
                        command,
                        bazelProjects.stream().map(BazelProject::getProject).collect(toList()),
                        monitor.split(1, SUPPRESS_ALL_LABELS));

            // populate map from result
            Map<BazelProject, CompileAndRuntimeClasspath> classpathsByProject = new HashMap<>();
            var aspectsInfo = new JavaAspectsInfo(result, workspace, aspects);
            for (BazelProject bazelProject : bazelProjects) {
                monitor.subTask(bazelProject.getName());
                monitor.checkCanceled();

                // build index of classpath info
                var classpathInfo =
                        new JavaAspectsClasspathInfo(aspectsInfo, workspace, availableDependencies, bazelProject);

                // remove old marker
                deleteClasspathContainerProblems(bazelProject);

                // add the target
                var problem = classpathInfo.addTarget(bazelProject.getBazelTarget());
                if (!problem.isOK()) {
                    createClasspathContainerProblem(bazelProject, problem);
                }

                // compute the classpath
                var classpath = classpathInfo.compute();

                classpathsByProject.put(bazelProject, classpath);
                monitor.worked(1);
            }

            return classpathsByProject;
        } finally {
            if (progress != null) {
                progress.done();
            }
        }
    }

    @Override
    protected List<BazelProject> doProvisionProjects(Collection<BazelTarget> targets, TracingSubMonitor monitor)
            throws CoreException {
        monitor.setWorkRemaining(targets.size());
        List<BazelProject> result = new ArrayList<>();
        for (BazelTarget target : targets) {
            monitor.subTask(target.getLabel().toString());

            // provision project
            var project = provisionProjectForTarget(target, monitor);
            if (project != null) {
                result.add(project);
            }
        }
        return result;
    }

    protected BazelProject provisionJavaBinaryProject(BazelTarget target, TracingSubMonitor monitor)
            throws CoreException {
        // TODO: create a shared launch configuration
        return provisionJavaLibraryProject(target, monitor);
    }

    protected BazelProject provisionJavaImportProject(BazelTarget target, TracingSubMonitor monitor)
            throws CoreException {
        // java_import is implicitly supported
        return provisionJavaLibraryProject(target, monitor);
    }

    /**
     * Provisions a Java project for the specified {@link BazelTarget}
     *
     * @param target
     *            the <code>java_library</code> target
     * @param progress
     *            monitor for reporting progress and tracking cancellation
     * @return the provisioned project
     * @throws CoreException
     */
    protected BazelProject provisionJavaLibraryProject(BazelTarget target, TracingSubMonitor monitor)
            throws CoreException {
        monitor = monitor.split(1, "Provisioning Java project for target " + target.getLabel());

        var project = provisionTargetProject(target, monitor.slice(1));

        // build the Java information
        var javaInfo = collectJavaInfo(project, List.of(target), monitor.slice(1));

        // configure links
        linkSourcesIntoProject(project, javaInfo, monitor.slice(1));
        linkGeneratedSourcesIntoProject(project, javaInfo, monitor.slice(1));
        linkJarsIntoProject(project, javaInfo, monitor.slice(1));

        // configure classpath
        configureRawClasspath(project, javaInfo, monitor.slice(1));

        return project;
    }

    protected BazelProject provisionJavaTestProject(BazelTarget target, TracingSubMonitor monitor)
            throws CoreException {
        // there is a bug in Eclipse preventing execution of JUnit tests
        // https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/957
        return provisionJavaLibraryProject(target, monitor);
    }

    protected BazelProject provisionProjectForTarget(BazelTarget target, TracingSubMonitor monitor)
            throws CoreException {
        var ruleName = target.getRuleClass();
        return switch (ruleName) {
            case "java_library": {
                yield provisionJavaLibraryProject(target, monitor);
            }
            case "java_import": {
                yield provisionJavaImportProject(target, monitor);
            }
            case "java_binary": {
                yield provisionJavaBinaryProject(target, monitor);
            }
            case "java_test": {
                yield provisionJavaTestProject(target, monitor);
            }
            default: {
                LOG.debug("{}: Skipping provisioning due to unsupported rule '{}'.", target, ruleName);
                monitor.worked(1);
                yield null;
            }
        };
    }

    protected BazelProject provisionTargetProject(BazelTarget target, IProgressMonitor monitor) throws CoreException {
        if (target.hasBazelProject()) {
            return target.getBazelProject();
        }

        var label = target.getLabel();
        var projectName =
                format("%s:%s", label.getPackagePath().replace('/', '.'), label.getTargetName().replace('/', '.'));
        var projectLocation = getFileSystemMapper().getProjectsArea().append(projectName);

        createProjectForElement(projectName, projectLocation, target, monitor);

        // this call is no longer expected to fail now (unless we need to poke the element info cache manually here)
        return target.getBazelProject();
    }

}
