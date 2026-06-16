package com.salesforce.bazel.eclipse.core.model.discovery;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BUILDPATH_PROBLEM_MARKER;
import static java.lang.String.format;
import static java.nio.file.Files.isRegularFile;
import static java.util.stream.Collectors.joining;
import static org.eclipse.core.resources.IResource.DEPTH_ZERO;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.salesforce.bazel.eclipse.core.classpath.BazelClasspathScope;
import com.salesforce.bazel.eclipse.core.classpath.CompileAndRuntimeClasspath;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.buildfile.FunctionCall;
import com.salesforce.bazel.eclipse.core.model.discovery.analyzers.starlark.StarlarkMacroCallAnalyzer;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.libs.ExternalLibrariesDiscovery;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaProjectInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaSourceEntry;
import com.salesforce.bazel.eclipse.core.util.trace.TracingSubMonitor;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * Implementation of {@link TargetProvisioningStrategy} which provisions projects based on parsing <code>BUILD</code>
 * files directly.
 * <p>
 * This strategy implements behavior which intentionally deviates from Bazel dominated strategies in favor of a better
 * developer experience in IDEs.
 * <ul>
 * <li><code>BUILD</code> files are parsed and macro/function calls translated into projects.</li>
 * <li>The macro translation is extensible so translators for custom macros can be provided and included in the
 * analysis.</li>
 * <li>A heuristic is used to merge <code>java_*</code> targets in the same package into a single Eclipse project.</li>
 * <li>Projects are created directly in the package location.</li>
 * <li>The root (empty) package <code>//</code> is not supported.</li>
 * </ul>
 * </p>
 */
public class BuildfileDrivenProvisioningStrategy extends ProjectPerPackageProvisioningStrategy {

    public static final String STRATEGY_NAME = "project-per-buildfile";

    private static Logger LOG = LoggerFactory.getLogger(BuildfileDrivenProvisioningStrategy.class);

    private final TargetDiscoveryAndProvisioningExtensionLookup extensionLookup =
            new TargetDiscoveryAndProvisioningExtensionLookup();

    @Override
    public Map<BazelProject, CompileAndRuntimeClasspath> computeClasspaths(Collection<BazelProject> bazelProjects,
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

            Map<BazelProject, CompileAndRuntimeClasspath> classpathsByProject = new HashMap<>();
            for (BazelProject bazelProject : bazelProjects) {
                monitor.subTask("Analyzing: " + bazelProject);
                monitor.checkCanceled();

                // cleanup markers from previous runs
                bazelProject.getProject().deleteMarkers(BUILDPATH_PROBLEM_MARKER, true, DEPTH_ZERO);

                // compute the classpath
                var classpath = new LinkedHashSet<>(externalLibraries);

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

                classpathsByProject
                        .put(bazelProject, new CompileAndRuntimeClasspath(classpath, Collections.emptyList()));
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
    protected List<BazelProject> doProvisionProjects(Collection<TargetExpression> targetsOrPackages,
            BazelWorkspace workspace, TracingSubMonitor monitor) throws CoreException {

        // extract package paths from label to provision
        var packages = targetsOrPackages.parallelStream().map(this::extractPackagePath).distinct().toList();

        // load macro analyzers specified in project view
        Map<String, StarlarkMacroCallAnalyzer> starlarkAnalyzers = new HashMap<>();
        for (var settingsEntry : workspace.getBazelProjectView().targetProvisioningSettings().entrySet()) {
            if (settingsEntry.getKey().startsWith("macro:")) {
                try {
                    var macroName = settingsEntry.getKey().substring("macro:".length());
                    var sclFile = new WorkspacePath(settingsEntry.getValue());
                    var analyzer = new StarlarkMacroCallAnalyzer(workspace, sclFile);
                    starlarkAnalyzers.put(macroName, analyzer);
                } catch (Exception e) {
                    throw new CoreException(
                            Status.error(
                                format(
                                    "Error loading macro call analyzer for setting '%s': %s",
                                    settingsEntry,
                                    e.getMessage()),
                                e));
                }
            }
        }

        monitor.beginTask("Provisioning projects", packages.size() * 3);
        var result = new ArrayList<BazelProject>();
        for (Path packagePath : packages) {
            var bazelPackage = workspace.getBazelPackage(IPath.fromPath(packagePath));

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
            var relevantTargets = new ArrayList<TargetName>();
            for (FunctionCall macroCall : topLevelMacroCalls) {
                var relevant = processMacroCall(macroCall, javaInfo, starlarkAnalyzers);
                if (!relevant) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Skipping not relevant macro call '{}'.", macroCall);
                    }
                    continue;
                }
                var name = macroCall.getName();
                if (name != null) {
                    relevantTargets.add(TargetName.create(name));
                }
            }

            // create the project for the package
            var project = provisionPackageProject(bazelPackage, monitor.slice(1));

            // remember/update the targets to build for the project
            project.setBazelTargetNames(relevantTargets, monitor.slice(1));

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
                            relevantTargets.stream().map(TargetName::toString).collect(joining(", ")))));
            }

            // configure links
            linkGeneratedSourcesIntoProject(project, javaInfo, monitor.slice(1));

            // configure classpath
            configureRawClasspath(project, javaInfo, monitor.slice(1));

            result.add(project);
        }
        return result;
    }

    private Path extractPackagePath(TargetExpression targetExpression) {
        var targetExpressionStr = targetExpression.toString();
        var startIndex = targetExpressionStr.indexOf("//") + "//".length();
        var colonIndex = targetExpressionStr.lastIndexOf(':');
        return Path.of(targetExpressionStr.substring(startIndex, colonIndex));
    }

    private boolean processMacroCall(FunctionCall macroCall, JavaProjectInfo javaInfo,
            Map<String, ? extends MacroCallAnalyzer> projectViewAnalyzers) throws CoreException {
        // get the analyzers to check
        List<MacroCallAnalyzer> analyzers;
        var projectViewAnalyzer = projectViewAnalyzers.get(macroCall.getResolvedFunctionName());
        if (projectViewAnalyzer != null) {
            // any analyzer from project view takes precedences
            analyzers = List.of(projectViewAnalyzer);
        } else {
            analyzers = extensionLookup.createMacroCallAnalyzers(macroCall.getResolvedFunctionName());
            if (analyzers.isEmpty()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("No analyzers available for function '{}'", macroCall.getResolvedFunctionName());
                }
                return false; // no analyzers
            }
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
