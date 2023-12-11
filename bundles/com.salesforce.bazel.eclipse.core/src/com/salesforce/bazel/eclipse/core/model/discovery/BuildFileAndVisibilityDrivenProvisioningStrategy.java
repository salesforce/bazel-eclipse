package com.salesforce.bazel.eclipse.core.model.discovery;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BUILDPATH_PROBLEM_MARKER;
import static java.lang.String.format;
import static java.nio.file.Files.isRegularFile;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;
import static org.eclipse.core.resources.IResource.DEPTH_ZERO;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WildcardTargetPattern;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.salesforce.bazel.eclipse.core.classpath.BazelClasspathScope;
import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.buildfile.FunctionCall;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.libs.ExternalLibrariesDiscovery;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaProjectInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaSourceEntry;
import com.salesforce.bazel.sdk.command.BazelQueryForLabelsCommand;
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

    public static class CircularDependenciesHelper {

        /** A single {@link TargetExpression} and associated information. */
        private static class TargetData {
            private final TargetExpression originalExpression;
            private final TargetExpression unexcludedExpression;
            private final WildcardTargetPattern wildcardPattern;

            TargetData(TargetExpression expression) {
                this.originalExpression = expression;
                this.unexcludedExpression = expression.isExcluded()
                        ? TargetExpression.fromStringSafe(expression.toString().substring(1)) : expression;
                this.wildcardPattern = WildcardTargetPattern.fromExpression(expression);
            }

            /** Returns true if the entire package is covered by this expression. */
            boolean coversPackage(WorkspacePath path) {
                return (wildcardPattern != null) && wildcardPattern.coversPackage(path);
            }

            boolean coversTarget(Label label) {
                return label.equals(unexcludedExpression) || coversPackage(label.blazePackage());
            }

            boolean coversTargetData(TargetData data) {
                if (data.wildcardPattern == null) {
                    return (data.unexcludedExpression instanceof Label)
                            && coversTarget(((Label) data.unexcludedExpression));
                }
                if (wildcardPattern == null) {
                    return false;
                }
                return data.wildcardPattern.isRecursive()
                        ? wildcardPattern.isRecursive()
                                && wildcardPattern.coversPackage(data.wildcardPattern.getBasePackage())
                        : wildcardPattern.coversPackage(data.wildcardPattern.getBasePackage());
            }

            boolean isExcluded() {
                return originalExpression.isExcluded();
            }
        }

        private final List<TargetData> reversedTargets;

        public CircularDependenciesHelper(Collection<TargetExpression> targetOrderHints) {
            ImmutableList<TargetData> targets =
                    targetOrderHints.stream().map(TargetData::new).collect(toImmutableList());

            // reverse list, removing trivially-excluded targets
            List<TargetData> excluded = new ArrayList<>();
            ImmutableList.Builder<TargetData> builder = ImmutableList.builder();
            for (TargetData target : targets.reverse()) {
                if (target.isExcluded()) {
                    excluded.add(target);
                    builder.add(target);
                    continue;
                }
                var drop = excluded.stream().anyMatch(excl -> excl.coversTargetData(target));
                if (!drop) {
                    builder.add(target);
                }
            }
            // the last target expression to cover a label overrides all previous expressions
            // that's why we use a reversed list
            this.reversedTargets = builder.build();
        }

        private int getPosition(BazelPackage toPackage) {
            var packagePath = toPackage.getLabel().packagePathAsPrimitive();
            var position = reversedTargets.size();
            for (TargetData target : reversedTargets) {
                position--;
                if (target.coversPackage(packagePath)) {
                    // if the toLabel is excluded, never allow the dependency
                    if (target.isExcluded()) {
                        return -1;
                    }

                    // accept the position
                    return position;
                }
            }

            // not found
            return -1;
        }

        public boolean isAllowedDependencyPath(BazelPackage fromPackage, BazelPackage toPackage) {
            // get position of target
            var toPosition = getPosition(fromPackage);
            if (toPosition < 0) {
                return false;
            }

            // get position of from
            var fromPosition = getPosition(toPackage);
            if (fromPosition < 0) {
                return false;
            }

            // special case if both are covered by same position
            if (toPosition == fromPosition) {
                // dependency from sub-package to parent package is ok
                // in order to prevent parent -> sub we simply check the segment count
                return toPackage.getWorkspaceRelativePath().isPrefixOf(fromPackage.getWorkspaceRelativePath())
                        && (toPackage.getWorkspaceRelativePath().segmentCount() < fromPackage.getWorkspaceRelativePath()
                                .segmentCount());
            }

            // the path is allowed if to-project is of higher order than from-project
            return toPosition > fromPosition;
        }

    }

    public static final String STRATEGY_NAME = "build-file-and-visibility-driven";

    private static Logger LOG = LoggerFactory.getLogger(BuildFileAndVisibilityDrivenProvisioningStrategy.class);

    private final TargetDiscoveryAndProvisioningExtensionLookup extensionLookup =
            new TargetDiscoveryAndProvisioningExtensionLookup();

    @Override
    public Map<BazelProject, Collection<ClasspathEntry>> computeClasspaths(Collection<BazelProject> bazelProjects,
            BazelWorkspace workspace, BazelClasspathScope scope, IProgressMonitor progress) throws CoreException {
        LOG.debug("Computing classpath for projects: {}", bazelProjects);
        try {
            var monitor = SubMonitor.convert(progress, "Computing classpaths...", 2 + bazelProjects.size());

            Map<BazelProject, List<BazelLabel>> activeTargetsPerProject = new HashMap<>();
            for (BazelProject bazelProject : bazelProjects) {
                monitor.checkCanceled();

                if (!bazelProject.isPackageProject()) {
                    throw new CoreException(
                            Status.error(
                                format(
                                    "Unable to compute classpath for project '%s'. Please check the setup. This is not a Bazel package project created by the project per package strategy.",
                                    bazelProject)));
                }

                var targetsToBuild = bazelProject.getBazelTargets();
                if (targetsToBuild.isEmpty()) {
                    throw new CoreException(
                            Status.error(
                                format(
                                    "Unable to compute classpath for project '%s'. Please check the setup. This Bazel package project is missing information about the relevant targets.",
                                    bazelProject)));
                }

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found targets for project '{}': {}", bazelProject, targetsToBuild);
                }

                var packageTargets = targetsToBuild.stream().map(BazelTarget::getLabel).toList();
                activeTargetsPerProject.put(bazelProject, packageTargets);
            }

            var jarResolver = new JavaClasspathJarLocationResolver(workspace);
            var externalLibrariesDiscovery = new ExternalLibrariesDiscovery(workspace);
            var externalLibraries = externalLibrariesDiscovery.query(monitor.split(1));

            var workspaceRoot = workspace.getLocation().toPath();

            // use the hints to avoid circular dependencies between projects in Eclipse
            var circularDependenciesHelper = new CircularDependenciesHelper(workspace.getBazelProjectView().targets());

            Map<BazelProject, Collection<ClasspathEntry>> classpathsByProject = new HashMap<>();
            for (BazelProject bazelProject : bazelProjects) {
                monitor.subTask("Analyzing: " + bazelProject);
                monitor.checkCanceled();

                // cleanup markers from previous runs
                bazelProject.getProject().deleteMarkers(BUILDPATH_PROBLEM_MARKER, true, DEPTH_ZERO);

                // query for rdeps to find classpath exclusions
                var projectTargets = activeTargetsPerProject.get(bazelProject);
                var rdeps = workspace.getCommandExecutor()
                        .runQueryWithoutLock(
                            new BazelQueryForLabelsCommand(
                                    workspaceRoot,
                                    format(
                                        "kind(java_library, rdeps(//..., %s))",
                                        projectTargets.stream().map(BazelLabel::toString).collect(joining(" + "))),
                                    true,
                                    format(
                                        "Querying for reverse dependencies of '%s' for classpath computation",
                                        bazelProject.getName())))
                        .stream()
                        .map(BazelLabel::new)
                        .collect(toSet());

                // get all accessible targets based on visibility
                Set<BazelLabel> allVisibleTargets = workspace.getCommandExecutor()
                        .runQueryWithoutLock(
                            new BazelQueryForLabelsCommand(
                                    workspaceRoot,
                                    format(
                                        "kind(java_library, visible(%s, //...))",
                                        projectTargets.stream().map(BazelLabel::toString).collect(joining(" + "))),
                                    true,
                                    format(
                                        "Querying for Java targets visibile to '%s' for classpath computation",
                                        bazelProject.getName())))
                        .stream()
                        .map(BazelLabel::new)
                        .collect(toCollection(LinkedHashSet::new));

                // ensure the workspace has all the packages open
                var allPackagesWithVisibleTargets =
                        allVisibleTargets.stream().map(workspace::getBazelPackage).distinct().toList();
                workspace.open(allPackagesWithVisibleTargets);

                // log a warning if the cache is too small
                var packagesNotOpen =
                        allPackagesWithVisibleTargets.stream().filter(not(BazelPackage::hasInfo)).toList();
                if (packagesNotOpen.size() > 0) {
                    LOG.warn(
                        "Classpath computation might be slow. The Bazel element cache is too small. Please increase the cache size by at least {}.",
                        packagesNotOpen.size() * 2);
                }

                // get the remaining list of visible deps based on workspace dependency graph
                var visibleDeps = new ArrayList<>(allVisibleTargets);
                visibleDeps.removeAll(rdeps); // exclude reverse deps
                visibleDeps.removeAll(projectTargets); // exclude project targets (avoid dependencies on itself)

                // compute the classpath
                var processedPackages = new HashSet<BazelProject>();
                var classpath = new LinkedHashSet<>(externalLibraries);
                for (BazelLabel visibleDep : visibleDeps) {
                    // connect to existing package project if possible
                    var bazelPackage = workspace.getBazelPackage(visibleDep.getPackageLabel());
                    if (bazelPackage.hasBazelProject()) {
                        var packageProject = bazelPackage.getBazelProject();
                        // check the hints if the project should actually go on the classpath
                        if (processedPackages.add(packageProject) && circularDependenciesHelper
                                .isAllowedDependencyPath(bazelProject.getBazelPackage(), bazelPackage)) {
                            classpath.add(ClasspathEntry.newProjectEntry(packageProject.getProject()));
                        }
                        continue; // next dependency
                    }

                    // lookup output jars from target
                    var target = workspace.getBazelTarget(visibleDep);
                    var ruleOutput = target.getRuleOutput();
                    var builder = LibraryArtifact.builder();
                    var foundClassJar = false;
                    for (IPath jar : ruleOutput) {
                        if (jar.lastSegment().endsWith("-src.jar")) {
                            builder.addSourceJar(jarResolver.generatedJarLocation(bazelPackage, jar));
                        } else if (jar.lastSegment().endsWith(".jar")) {
                            builder.setClassJar(jarResolver.generatedJarLocation(bazelPackage, jar));
                            foundClassJar = true;
                        } else {
                            LOG.warn("Unknown jar: '{}' (output of '{}')", jar, target);
                        }
                    }

                    if (foundClassJar) {
                        var jarLibrary = builder.build();
                        var jarEntry = jarResolver.resolveJar(jarLibrary);
                        if (jarEntry != null) {
                            if (isRegularFile(jarEntry.getPath().toPath())) {
                                classpath.add(jarEntry);
                            } else {
                                createBuildPathProblem(
                                    bazelProject,
                                    Status.error(
                                        format(
                                            "Jar '%s' not found. Please run bazel fetch or bazel build and refresh the classpath.",
                                            jarLibrary.getClassJar())));
                            }
                        } else {
                            createBuildPathProblem(
                                bazelProject,
                                Status.error(
                                    format(
                                        "Unable to resolve jar '%s'. Please open a bug with more details.",
                                        jarLibrary)));
                        }
                    } else {
                        createBuildPathProblem(
                            bazelProject,
                            Status.error(
                                format(
                                    "Unable to resolve rule output '%s'. Please open a bug with more details.",
                                    ruleOutput)));
                    }
                }

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
                targets.stream().collect(groupingBy(BazelTarget::getBazelPackage));

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
                        format(
                            "The root package was skipped during sync because it's not supported by the '%s' strategy. Consider excluding it in the .bazelproject file.",
                            STRATEGY_NAME)));
                continue;
            }

            // get the top-level macro calls
            var topLevelMacroCalls = bazelPackage.getBazelBuildFile().getTopLevelCalls();

            // build the project information as we traverse the macros
            var javaInfo = new JavaProjectInfo(bazelPackage);
            var relevantTargets = new ArrayList<BazelTarget>();
            for (FunctionCall macroCall : topLevelMacroCalls) {
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
                            "No source directories detected when analyzing package '%s' using targets '%s'",
                            bazelPackage.getLabel().getPackagePath(),
                            packageTargets.stream()
                                    .map(BazelTarget::getLabel)
                                    .map(BazelLabel::getLabelPath)
                                    .collect(joining(", ")))));
            }

            // configure links
            linkGeneratedSourcesIntoProject(project, javaInfo, monitor.split(1));

            // configure classpath
            configureRawClasspath(project, javaInfo, monitor.split(1));

            result.add(project);
        }
        return result;
    }

    private boolean processMacroCall(FunctionCall macroCall, JavaProjectInfo javaInfo) throws CoreException {
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
