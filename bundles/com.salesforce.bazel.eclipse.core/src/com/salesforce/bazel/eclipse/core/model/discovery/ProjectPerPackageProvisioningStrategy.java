package com.salesforce.bazel.eclipse.core.model.discovery;

import static java.lang.String.format;
import static java.nio.file.Files.isRegularFile;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaSourceEntry;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects.OutputGroup;
import com.salesforce.bazel.sdk.command.BazelBuildWithIntelliJAspectsCommand;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * Legacy implementation of {@link TargetProvisioningStrategy} which provisions a project for all targets in the same
 * package.
 * <p>
 * This strategy implements the BEF behavior in versions 1.x.
 * <ul>
 * <li>All <code>java_*</code> targets in the same package are merged into a single Eclipse project.</li>
 * <li>The build path is merged so Eclipse does not have proper visibility in potentially unsupported imports.</li>
 * <li>Projects are created directly in the package location.</li>
 * <li>The root (empty) package <code>//</code> is not supported.</li>
 * </ul>
 * </p>
 */
public class ProjectPerPackageProvisioningStrategy extends BaseProvisioningStrategy {

    public static final String STRATEGY_NAME = "project-per-package";

    private static Logger LOG = LoggerFactory.getLogger(ProjectPerPackageProvisioningStrategy.class);

    @Override
    public Map<BazelProject, Collection<ClasspathEntry>> computeClasspaths(Collection<BazelProject> bazelProjects,
            BazelWorkspace workspace, BazelClasspathScope scope, IProgressMonitor progress) throws CoreException {
        LOG.debug("Computing classpath for projects: {}", bazelProjects);
        try {
            var monitor = SubMonitor.convert(progress, "Computing classpaths...", 1 + bazelProjects.size());

            List<BazelLabel> targetsToBuild = new ArrayList<>(bazelProjects.size());
            Map<BazelProject, List<String>> activeTargetsPerProject = new HashMap<>();
            for (BazelProject bazelProject : bazelProjects) {
                monitor.checkCanceled();

                if (!bazelProject.isPackageProject()) {
                    throw new CoreException(
                            Status.error(
                                format(
                                    "Unable to compute classpath for project '%s'. Please check the setup. This is not a Bazel package project created by the project per package strategy.",
                                    bazelProject)));
                }

                var projectTargetsToBuild = bazelProject.getBazelTargets();
                if (projectTargetsToBuild.isEmpty()) {
                    // brute force build all targets
                    LOG.warn(
                        "Targets to build not properly set for project '{}'. Building all targets for computing the classpath, which may be too expensive!",
                        bazelProject);
                    bazelProject.getBazelPackage()
                            .getBazelTargets()
                            .stream()
                            .map(BazelTarget::getLabel)
                            .forEach(targetsToBuild::add);
                    activeTargetsPerProject.put(
                        bazelProject,
                        bazelProject.getBazelPackage()
                                .getBazelTargets()
                                .stream()
                                .map(BazelTarget::getTargetName)
                                .collect(toList()));
                    continue;
                }

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found targets for project '{}': {}", bazelProject, targetsToBuild);
                }

                List<String> packageTargets = new ArrayList<>();
                for (BazelTarget target : projectTargetsToBuild) {
                    packageTargets.add(target.getName());
                    targetsToBuild.add(target.getLabel());
                }
                activeTargetsPerProject.put(bazelProject, packageTargets);
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
                    onlyDirectDeps);

            monitor.subTask("Running Bazel...");
            var result = workspace.getCommandExecutor()
                    .runDirectlyWithWorkspaceLock(
                        command,
                        bazelProjects.stream().map(BazelProject::getProject).collect(toList()),
                        monitor.split(1));

            // populate map from result
            Map<BazelProject, Collection<ClasspathEntry>> classpathsByProject = new HashMap<>();
            var aspectsInfo = new JavaAspectsInfo(result, workspace);
            for (BazelProject bazelProject : bazelProjects) {
                monitor.subTask("Analyzing: " + bazelProject);
                monitor.checkCanceled();

                // build index of classpath info
                var classpathInfo = new JavaAspectsClasspathInfo(aspectsInfo, workspace);

                // add the targets
                List<String> targetNames = requireNonNull(
                    activeTargetsPerProject.get(bazelProject),
                    () -> format("programming error: not targets for project: %s", bazelProject));
                for (String targetName : targetNames) {
                    classpathInfo.addTarget(bazelProject.getBazelPackage().getBazelTarget(targetName));
                }

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
        // group into packages
        Map<BazelPackage, List<BazelTarget>> targetsByPackage =
                targets.stream().filter(this::isSupported).collect(groupingBy(BazelTarget::getBazelPackage));

        monitor.beginTask("Provisioning projects", targetsByPackage.size() * 3);

        var result = new ArrayList<BazelProject>();
        for (Entry<BazelPackage, List<BazelTarget>> entry : targetsByPackage.entrySet()) {
            var bazelPackage = entry.getKey();
            var packageTargets = entry.getValue();

            // skip the root package (not supported)
            if (bazelPackage.isRoot()) {
                createBuildPathProblem(
                    bazelPackage.getBazelWorkspace().getBazelProject(),
                    Status.warning(
                        "The root package was skipped during sync because it's not supported by the project-per-package strategy. Consider excluding it in the .bazelproject file."));
                continue;
            }

            // create the project for the package
            var project = provisionPackageProject(bazelPackage, packageTargets, monitor.split(1));

            // build the Java information
            var javaInfo = collectJavaInfo(project, packageTargets, monitor.split(1));

            // sanity check
            var sourceInfo = javaInfo.getSourceInfo();
            if (sourceInfo.hasSourceFilesWithoutCommonRoot()) {
                for (JavaSourceEntry file : sourceInfo.getSourceFilesWithoutCommonRoot()) {
                    createBuildPathProblem(
                        project,
                        Status.warning(
                            format(
                                "File '%s' could not be mapped into a common source directory. The project may not build successful in Eclipse.",
                                file.getPath())));
                }
            }
            if (!sourceInfo.hasSourceDirectories()) {
                createBuildPathProblem(
                    project,
                    Status.error(
                        format(
                            "No source directories detected when analyzihng package '%s' using targets '%s'",
                            bazelPackage.getLabel().getPackagePath(),
                            packageTargets.stream()
                                    .map(BazelTarget::getLabel)
                                    .map(BazelLabel::getLabelPath)
                                    .collect(joining(", ")))));
            }

            // configure classpath
            configureRawClasspath(project, javaInfo, monitor.split(1));

            result.add(project);
        }
        return result;
    }

    private boolean isSupported(BazelTarget bazeltarget) {
        String ruleName;
        try {
            ruleName = bazeltarget.getRuleClass();
        } catch (CoreException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return switch (ruleName) {
            case "java_library", "java_import", "java_binary": {
                yield true;

            }
            default: {
                yield false;
            }
        };
    }

    protected BazelProject provisionPackageProject(BazelPackage bazelPackage, List<BazelTarget> targets,
            SubMonitor monitor) throws CoreException {
        if (!bazelPackage.hasBazelProject()) {
            // create project
            var packagePath = bazelPackage.getLabel().getPackagePath();
            var projectName = packagePath.isBlank() ? "ROOT" : packagePath.replace('/', '.');

            // create the project directly within the package (note, there can be at most one project per package with this strategy anyway)
            var projectLocation = bazelPackage.getLocation();
            createProjectForElement(projectName, projectLocation, bazelPackage, monitor);
        } else {
            // use existing project
            bazelPackage.getBazelProject().getProject();
        }

        // this call is no longer expected to fail now (unless we need to poke the element info cache manually here)
        var bazelProject = bazelPackage.getBazelProject();

        // remember/update the targets to build for the project
        bazelProject.setBazelTargets(targets);

        return bazelProject;
    }

}
