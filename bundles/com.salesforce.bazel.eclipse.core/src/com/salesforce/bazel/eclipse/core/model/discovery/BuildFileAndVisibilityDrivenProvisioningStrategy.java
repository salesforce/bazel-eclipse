package com.salesforce.bazel.eclipse.core.model.discovery;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.PLUGIN_ID;
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
import org.eclipse.core.runtime.QualifiedName;
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
import com.salesforce.bazel.eclipse.core.model.buildfile.MacroCall;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaProjectInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaSourceEntry;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects.OutputGroup;
import com.salesforce.bazel.sdk.command.BazelBuildWithIntelliJAspectsCommand;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * Implementation of {@link TargetProvisioningStrategy} which provisions projects based on parsing <code>BUILD</code>
 * files directly and computing their classpath based on visibility in the build graph.
 * <p>
 * This strategy implements behavior which intentionally deviates from Bazel dominated strategies in favor of a better
 * developer experience in IDEs.
 * <ul>
 * <li><code>BUILD</code> files are parsed and macro/function calls translated into projects.</li>
 * <li>The macro translation is extensible so translators for custom macros can be provided and included in the
 * analysis.</li>
 * <li>A heuristic is used to merge <code>java_*</code> targets in the same package into a single Eclipse project.</li>
 * <li>The classpath is computed based on visibility, which eventually allows to compute the deps list by IDEs based on
 * actual use.</li>
 * <li>Projects are created directly in the package location.</li>
 * <li>The root (empty) package <code>//</code> is not supported.</li>
 * </ul>
 * </p>
 */
public class BuildFileAndVisibilityDrivenProvisioningStrategy extends ProjectPerPackageProvisioningStrategy {

    public static final String STRATEGY_NAME = "build-file-and-visibility-driven";

    public static final QualifiedName PROJECT_PROPERTY_TARGETS = new QualifiedName(PLUGIN_ID, "bazel_targets");

    private static Logger LOG = LoggerFactory.getLogger(BuildFileAndVisibilityDrivenProvisioningStrategy.class);

    private final TargetDiscoveryAndProvisioningExtensionLookup extensionLookup =
            new TargetDiscoveryAndProvisioningExtensionLookup();

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
                    throw new CoreException(Status.error(format(
                        "Unable to compute classpath for project '%s'. Please check the setup. This is not a Bazel package project created by the project per package strategy.",
                        bazelProject)));
                }

                var targetsToBuildValue = bazelProject.getProject().getPersistentProperty(PROJECT_PROPERTY_TARGETS);
                if (targetsToBuildValue == null) {
                    // brute force build all targets
                    LOG.warn(
                        "Targets to build not properly set for project '{}'. Building all targets for computing the classpath, which may be too expensive!",
                        bazelProject);
                    bazelProject.getBazelPackage()
                            .getBazelTargets()
                            .stream()
                            .map(BazelTarget::getLabel)
                            .forEach(targetsToBuild::add);
                    activeTargetsPerProject.put(bazelProject,
                        bazelProject.getBazelPackage()
                                .getBazelTargets()
                                .stream()
                                .map(BazelTarget::getTargetName)
                                .collect(toList()));
                    continue;
                }

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found targets for project '{}': {}", bazelProject,
                        targetsToBuildValue.replace(':', ','));
                }

                var packagePath = bazelProject.getBazelPackage().getLabel().getPackagePath();
                List<String> packageTargets = new ArrayList<>();
                for (String targetName : targetsToBuildValue.split(":")) {
                    packageTargets.add(targetName);
                    targetsToBuild.add(new BazelLabel(format("//%s:%s", packagePath, targetName)));
                }
                activeTargetsPerProject.put(bazelProject, packageTargets);
            }

            var workspaceRoot = workspace.getLocation().toPath();

            // run the aspect to compute all required information
            var onlyDirectDeps = workspace.getBazelProjectView().deriveTargetsFromDirectories();
            var outputGroups = Set.of(OutputGroup.INFO, OutputGroup.RESOLVE);
            var languages = Set.of(LanguageClass.JAVA);
            var aspects = workspace.getParent().getModelManager().getIntellijAspects();
            var command = new BazelBuildWithIntelliJAspectsCommand(workspaceRoot, targetsToBuild, outputGroups, aspects,
                    languages, onlyDirectDeps);

            monitor.subTask("Running Bazel...");
            var result = workspace.getCommandExecutor()
                    .runDirectlyWithWorkspaceLock(command,
                        bazelProjects.stream().map(BazelProject::getProject).collect(toList()), monitor.split(1));

            // populate map from result
            Map<BazelProject, Collection<ClasspathEntry>> classpathsByProject = new HashMap<>();
            var aspectsInfo = new JavaClasspathAspectsInfo(result, workspace);
            for (BazelProject bazelProject : bazelProjects) {
                monitor.subTask("Analyzing: " + bazelProject);
                monitor.checkCanceled();

                // build index of classpath info
                var classpathInfo = new JavaClasspathInfo(aspectsInfo, workspace);

                // add the targets
                List<String> targetNames = requireNonNull(activeTargetsPerProject.get(bazelProject),
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
                        createBuildPathProblem(bazelProject, Status.error(
                            format("Library '%s' is missing. Please consider running 'bazel fetch'", entry.getPath())));
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
                targets.stream().collect(groupingBy(BazelTarget::getBazelPackage));

        monitor.beginTask("Provisioning projects", targetsByPackage.size() * 3);

        var result = new ArrayList<BazelProject>();
        for (Entry<BazelPackage, List<BazelTarget>> entry : targetsByPackage.entrySet()) {
            var bazelPackage = entry.getKey();
            var packageTargets = entry.getValue();

            // skip the root package (not supported)
            if (bazelPackage.isRoot()) {
                createBuildPathProblem(bazelPackage.getBazelWorkspace().getBazelProject(), Status.warning(format(
                    "The root package was skipped during sync because it's not supported by the '%s' strategy. Consider excluding it in the .bazelproject file.",
                    STRATEGY_NAME)));
                continue;
            }

            // get the top-level macro calls
            var topLevelMacroCalls = bazelPackage.getBazelBuildFile().getTopLevelMacroCalls();

            // build the project information as we traverse the macros
            var javaInfo = new JavaProjectInfo(bazelPackage);
            var relevantTargets = new ArrayList<BazelTarget>();
            for (MacroCall macroCall : topLevelMacroCalls) {
                var relevant = processMacroCall(macroCall, javaInfo);
                if (!relevant) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Skipping not relevant macro call '{}'.", macroCall);
                    }
                    continue;
                }
                var name = macroCall.getName();
                if (name != null) {
                    packageTargets.stream().filter(t -> t.getTargetName().equals(name)).forEach(t -> {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Found relevant target '{}' for macro call '{}'", t, macroCall);
                        }
                        relevantTargets.add(t);
                    });
                }
            }

            // create the project for the package
            var project = provisionPackageProject(bazelPackage, packageTargets, monitor.split(1));

            // create markers
            analyzeProjectInfo(project, javaInfo, monitor);

            // sanity check
            var sourceInfo = javaInfo.getSourceInfo();
            if (sourceInfo.hasSourceFilesWithoutCommonRoot()) {
                for (JavaSourceEntry file : sourceInfo.getSourceFilesWithoutCommonRoot()) {
                    createBuildPathProblem(project, Status.warning(format(
                        "File '%s' could not be mapped into a common source directory. The project may not build successful in Eclipse.",
                        file.getPath())));
                }
            }
            if (!sourceInfo.hasSourceDirectories()) {
                createBuildPathProblem(project,
                    Status.error(format("No source directories detected when analyzing package '%s' using targets '%s'",
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

    private boolean processMacroCall(MacroCall macroCall, JavaProjectInfo javaInfo) throws CoreException {
        var analyzers = extensionLookup.createMacroCallAnalyzers(macroCall.getResolvedFunctionName());
        if (analyzers.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No analyzers available for function '{}'", macroCall.getResolvedFunctionName());
            }
            return false; // no analyzers
        }

        for (MacroCallAnalyzer analyzer : analyzers) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Processing macro call '{}' with analyzer '{}'", macroCall, analyzer);
            }
            var wasAnalyzed = analyzer.analyze(macroCall, javaInfo);
            if (wasAnalyzed) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Analyzer '{}' successfully processed macro call '{}'", analyzer.getClass(), macroCall);
                }
                return true; // stop processing
            }
        }

        return false; // not analyzed
    }

}
