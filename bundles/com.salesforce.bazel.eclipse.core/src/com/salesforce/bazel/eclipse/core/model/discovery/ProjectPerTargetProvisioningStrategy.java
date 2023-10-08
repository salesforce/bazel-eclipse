package com.salesforce.bazel.eclipse.core.model.discovery;

import static java.lang.String.format;
import static java.nio.file.Files.isRegularFile;
import static java.util.stream.Collectors.toList;
import static org.eclipse.core.runtime.SubMonitor.SUPPRESS_ALL_LABELS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.salesforce.bazel.eclipse.core.classpath.BazelClasspathScope;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
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
    public Map<BazelProject, Collection<ClasspathEntry>> computeClasspaths(Collection<BazelProject> bazelProjects,
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

            // run the aspect to compute all required information
            var onlyDirectDeps = workspace.getBazelProjectView().deriveTargetsFromDirectories();
            var outputGroups = Set.of(OutputGroup.INFO, OutputGroup.RESOLVE);
            var languages = Set.of(LanguageClass.JAVA);
            var aspects = workspace.getParent().getModelManager().getIntellijAspects();
            var command = new BazelBuildWithIntelliJAspectsCommand(
                    workspaceRoot,
                    targetsToBuild,
                    outputGroups,
                    aspects,
                    languages,
                    onlyDirectDeps,
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
            Map<BazelProject, Collection<ClasspathEntry>> classpathsByProject = new HashMap<>();
            var aspectsInfo = new JavaAspectsInfo(result, workspace);
            for (BazelProject bazelProject : bazelProjects) {
                monitor.subTask(bazelProject.getName());
                monitor.checkCanceled();

                // build index of classpath info
                var classpathInfo = new JavaAspectsClasspathInfo(aspectsInfo, workspace);

                // add the target
                classpathInfo.addTarget(bazelProject.getBazelTarget());

                // compute the classpath
                var classpath = classpathInfo.compute();

                // check for non existing jars
                for (ClasspathEntry entry : classpath) {
                    if (entry.getEntryKind() != IClasspathEntry.CPE_LIBRARY) {
                        continue;
                    }

                    if (!isRegularFile(entry.getPath().toPath())) {
                        createBuildPathProblem(
                            bazelProject,
                            Status.error(
                                format(
                                    "Library '%s' is missing. Please consider running 'bazel fetch'",
                                    entry.getPath())));
                        break;
                    }
                }

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
    protected List<BazelProject> doProvisionProjects(Collection<BazelTarget> targets, SubMonitor monitor)
            throws CoreException {
        monitor.beginTask("Provisioning projects", targets.size());
        List<BazelProject> result = new ArrayList<>();
        for (BazelTarget target : targets) {
            monitor.subTask(target.getLabel().toString());

            // provision project
            var project = provisionProjectForTarget(target, monitor.split(1));
            if (project != null) {
                result.add(project);
            }
        }
        return result;
    }

    protected BazelProject provisionJavaBinaryProject(BazelTarget target, SubMonitor monitor) throws CoreException {

        // TODO: create a shared launch configuration

        return provisionJavaLibraryProject(target, monitor);
    }

    protected BazelProject provisionJavaImportProject(BazelTarget target, SubMonitor monitor) throws CoreException {
        // TODO Auto-generated method stub
        return null;
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
    protected BazelProject provisionJavaLibraryProject(BazelTarget target, SubMonitor monitor) throws CoreException {
        monitor.beginTask(format("Provision project for target %s", target.getLabel()), 4);

        var project = provisionTargetProject(target, monitor.split(1));

        // build the Java information
        var javaInfo = collectJavaInfo(project, List.of(target), monitor.split(1));

        // configure links
        linkSourcesIntoProject(project, javaInfo, monitor.split(1));

        // configure classpath
        configureRawClasspath(project, javaInfo, monitor.split(1));

        return project;
    }

    protected BazelProject provisionProjectForTarget(BazelTarget target, SubMonitor monitor) throws CoreException {
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
            default: {
                LOG.debug("{}: Skipping provisioning due to unsupported rule '{}'.", target, ruleName);
                yield null;
            }
        };
    }

    protected BazelProject provisionTargetProject(BazelTarget target, SubMonitor monitor) throws CoreException {
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
