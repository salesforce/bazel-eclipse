package com.salesforce.bazel.eclipse.core.model.discovery;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.salesforce.bazel.eclipse.core.classpath.BazelClasspathScope;
import com.salesforce.bazel.eclipse.core.classpath.CompileAndRuntimeClasspath;
import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspaceBlazeInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaProjectInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaSourceEntry;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaSourceInfo;
import com.salesforce.bazel.eclipse.core.util.trace.TracingSubMonitor;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects;
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

    private static final String PROJECT_NAME_SEPARATOR_CHAR = "project_name_separator_char";
    private static final String JAVA_LIKE_RULES = "java_like_rules";

    public static final String STRATEGY_NAME = "project-per-package";

    private static Logger LOG = LoggerFactory.getLogger(ProjectPerPackageProvisioningStrategy.class);

    private final Set<String> additionalJavaLikeRules = new HashSet<>();

    @Override
    public Map<BazelProject, CompileAndRuntimeClasspath> computeClasspaths(Collection<BazelProject> bazelProjects,
            BazelWorkspace workspace, BazelClasspathScope scope, IProgressMonitor progress) throws CoreException {
        LOG.debug("Computing classpath for projects: {}", bazelProjects);
        try {
            var monitor =
                    TracingSubMonitor.convert(progress, "Computing Bazel project classpaths", 1 + bazelProjects.size());

            monitor.subTask("Collecting shards...");

            Map<BazelProject, Collection<BazelTarget>> activeTargetsPerProject = new HashMap<>();
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
                    activeTargetsPerProject.put(bazelProject, bazelProject.getBazelPackage().getBazelTargets());
                    continue;
                }

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found targets for project '{}': {}", bazelProject, projectTargetsToBuild);
                }
                activeTargetsPerProject.put(bazelProject, projectTargetsToBuild);
            }

            var targets = activeTargetsPerProject.values()
                    .stream()
                    .flatMap(Collection::stream)
                    .map(BazelTarget::getLabel)
                    .collect(toList());
            var allowedAdditionalCompileDependencies = queryForDepsWithClasspathDepth(workspace, targets);

            // collect classpaths by project
            Map<BazelProject, CompileAndRuntimeClasspath> classpathsByProject = new HashMap<>();

            var workspaceRoot = workspace.getLocation().toPath();

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

            // optimize parsing
            Interner<String> interner = Interners.newStrongInterner();

            // split into shards
            var shardsToBuild = createShards(activeTargetsPerProject, workspace);
            monitor.setWorkRemaining(5 * shardsToBuild.size());

            // run the build per shard
            var currentShardCount = 0;
            for (Map<BazelProject, Collection<BazelTarget>> shard : shardsToBuild) {
                currentShardCount++;

                var targetsToBuild = shard.values()
                        .stream()
                        .flatMap(Collection::stream)
                        .map(BazelTarget::getLabel)
                        .collect(Collectors.toList());
                var command = new BazelBuildWithIntelliJAspectsCommand(
                        workspaceRoot,
                        targetsToBuild,
                        outputGroupNames,
                        aspects,
                        new BazelWorkspaceBlazeInfo(workspace),
                        format(
                            "Running build with IDE aspects (shard %d of %d, %d targets)",
                            currentShardCount,
                            shardsToBuild.size(),
                            targetsToBuild.size()));
                // sync_flags
                command.addCommandArgs(workspace.getBazelProjectView().syncFlags());

                // optimize memory during parsing
                command.setInterner(interner);

                monitor.subTask(
                    format("Running build with IDE aspects (shard %d of %d)", currentShardCount, shardsToBuild.size()));
                var result = workspace.getCommandExecutor()
                        .runDirectlyWithinExistingWorkspaceLock(
                            command,
                            shard.keySet().stream().map(BazelProject::getProject).collect(toList()),
                            monitor.slice(3));

                // populate map from result
                var subMonitor = monitor.split(
                    2,
                    format(
                        "Analyze Bazel aspect info (shard %d of %d, %d targets)",
                        currentShardCount,
                        shardsToBuild.size(),
                        targetsToBuild.size()));
                var aspectsInfo = new JavaAspectsInfo(result, workspace, aspects);
                for (BazelProject bazelProject : shard.keySet()) {
                    subMonitor.subTask(bazelProject.getName());
                    subMonitor.checkCanceled();

                    // build index of classpath info
                    var classpathInfo = new JavaAspectsClasspathInfo(
                            aspectsInfo,
                            workspace,
                            allowedAdditionalCompileDependencies,
                            bazelProject);
                    var buildPathProblems = new ArrayList<IStatus>();

                    // add the targets
                    Collection<BazelTarget> projectTargets = requireNonNull(
                        activeTargetsPerProject.get(bazelProject),
                        () -> format("programming error: not targets for project: %s", bazelProject));
                    for (BazelTarget target : projectTargets) {
                        var status = classpathInfo.addTarget(target);
                        if (!status.isOK()) {
                            buildPathProblems.add(status);
                        }
                    }

                    // compute the classpath
                    var classpath = classpathInfo.compute();

                    // remove old marker
                    deleteClasspathContainerProblems(bazelProject);

                    // create problem markers for detected issues
                    for (IStatus problem : buildPathProblems) {
                        createClasspathContainerProblem(bazelProject, problem);
                    }

                    classpathsByProject.put(bazelProject, classpath);
                    subMonitor.worked(1);
                }
            }

            return classpathsByProject;
        } finally {
            if (progress != null) {
                progress.done();
            }
        }
    }

    @Override
    protected void configureRawClasspath(BazelProject project, JavaProjectInfo javaInfo, IProgressMonitor progress)
            throws CoreException {
        // log an error if we have sources without root - this is not wanted in this strategy
        if (javaInfo.getSourceInfo().hasSourceFilesWithoutCommonRoot()) {
            logSourceFilesWithoutCommonRoot(project, javaInfo.getSourceInfo(), false);
        }
        if (javaInfo.getTestSourceInfo().hasSourceFilesWithoutCommonRoot()) {
            logSourceFilesWithoutCommonRoot(project, javaInfo.getTestSourceInfo(), true);
        }
        // use default implementation
        super.configureRawClasspath(project, javaInfo, progress);
    }

    /**
     * This methods splits the given project and target map into multiple shards for separate execution based on the
     * project view settings.
     * <p>
     * Although there is no specific order, the implementation tries to be deterministic, i.e. for the same input the
     * same shards are returned.
     * </p>
     *
     * @param targetsByProjectMap
     * @param workspace
     * @return a list of shards (maybe just one in case sharding is disabled in the project view)
     * @throws CoreException
     */
    private List<Map<BazelProject, Collection<BazelTarget>>> createShards(
            Map<BazelProject, Collection<BazelTarget>> targetsByProjectMap, BazelWorkspace workspace)
            throws CoreException {
        if (!workspace.getBazelProjectView().shardSync()) {
            LOG.warn("Sharding is disabled. Please monitor system carefuly for memory issues during sync.");
            return List.of(targetsByProjectMap);
        }

        List<Map<BazelProject, Collection<BazelTarget>>> allShards = new ArrayList<>();

        // we then collect as many targets into a shard as possible
        var targetShardSize = workspace.getBazelProjectView().targetShardSize();

        // in order to be predictable we sort the project alphabetically
        SortedSet<BazelProject> sortedProjects = new TreeSet<>(Comparator.comparing(BazelProject::getName));
        sortedProjects.addAll(targetsByProjectMap.keySet());
        NEXT_PROJECT: for (BazelProject bazelProject : sortedProjects) {
            // again, in order to be predictable we sort the targets alphabetically
            SortedSet<BazelTarget> sortedTargets = new TreeSet<>(Comparator.comparing(BazelTarget::getTargetName));
            sortedTargets.addAll(targetsByProjectMap.get(bazelProject));

            // find room in an existing shard
            for (var existingShard : allShards) {
                int existingShardSize =
                        existingShard.values().stream().map(Collection::size).reduce(0, (a, b) -> a + b);
                if ((existingShardSize + sortedTargets.size()) <= targetShardSize) {
                    existingShard.put(bazelProject, sortedTargets);
                    continue NEXT_PROJECT;
                }
            }

            // add a new shard
            // note, we may have to exceed the target size because we never split targets belonging to the same project
            Map<BazelProject, Collection<BazelTarget>> newShard = new LinkedHashMap<>();
            newShard.put(bazelProject, sortedTargets);
            allShards.add(newShard);
        }

        return allShards;
    }

    private void createWarningsForFilesWithoutCommonRoot(BazelProject project, JavaSourceInfo sourceInfo)
            throws CoreException {
        for (JavaSourceEntry file : sourceInfo.getSourceFilesWithoutCommonRoot()) {
            createBuildPathProblem(
                project,
                Status.warning(
                    format(
                        "File '%s' could not be mapped into a common source directory. The project may not build successful in Eclipse.",
                        file.getPath())));
        }
    }

    @Override
    protected List<BazelProject> doProvisionProjects(Collection<BazelTarget> targets, TracingSubMonitor monitor)
            throws CoreException {
        // initialize the list of allowed java like rules
        var javaLikeRulesValue = getFileSystemMapper().getBazelWorkspace()
                .getBazelProjectView()
                .targetProvisioningSettings()
                .get(JAVA_LIKE_RULES);
        if (javaLikeRulesValue != null) {
            Stream.of(javaLikeRulesValue.split(","))
                    .map(String::trim)
                    .filter(not(String::isEmpty))
                    .forEach(additionalJavaLikeRules::add);
        }

        // group into packages
        Map<BazelPackage, List<BazelTarget>> targetsByPackage =
                targets.stream().filter(this::isSupported).collect(groupingBy(BazelTarget::getBazelPackage));

        monitor.setWorkRemaining(targetsByPackage.size() * 3);

        var result = new ArrayList<BazelProject>();
        for (Entry<BazelPackage, List<BazelTarget>> entry : targetsByPackage.entrySet()) {
            var bazelPackage = entry.getKey();
            var packageTargets = entry.getValue();

            monitor.subTask(bazelPackage.getName());

            // skip the root package (not supported)
            if (bazelPackage.isRoot()) {
                createBuildPathProblem(
                    bazelPackage.getBazelWorkspace().getBazelProject(),
                    Status.warning(
                        "The root package was skipped during sync because it's not supported by the project-per-package strategy. Consider excluding it in the .bazelproject file."));
                continue;
            }

            // create the project for the package
            var project = provisionPackageProject(bazelPackage, monitor.slice(1));

            // remember/update the targets to build for the project
            project.setBazelTargets(packageTargets, monitor.slice(1));

            // build the Java information
            var javaInfo = collectJavaInfo(project, packageTargets, monitor.slice(1));

            // sanity check
            if (javaInfo.getSourceInfo().hasSourceFilesWithoutCommonRoot()) {
                createWarningsForFilesWithoutCommonRoot(project, javaInfo.getSourceInfo());
            }
            if (javaInfo.getTestSourceInfo().hasSourceFilesWithoutCommonRoot()) {
                createWarningsForFilesWithoutCommonRoot(project, javaInfo.getTestSourceInfo());
            }
            if (!javaInfo.getSourceInfo().hasSourceDirectories()
                    && !javaInfo.getTestSourceInfo().hasSourceDirectories()) {
                createBuildPathProblem(
                    project,
                    Status.info(
                        format(
                            "No source directories detected when analyzing package '%s' using targets '%s'",
                            bazelPackage.getLabel().getPackagePath(),
                            packageTargets.stream()
                                    .map(BazelTarget::getLabel)
                                    .map(BazelLabel::getLabelPath)
                                    .collect(joining(", ")))));
            }

            // configure links
            linkGeneratedSourcesIntoProject(project, javaInfo, monitor.slice(1));

            // configure classpath
            configureRawClasspath(project, javaInfo, monitor.slice(1));

            result.add(project);
        }
        return result;
    }

    private char getProjectNameSeparatorChar(BazelPackage bazelPackage) throws CoreException {
        var separatorChar = bazelPackage.getBazelWorkspace()
                .getBazelProjectView()
                .targetProvisioningSettings()
                .get(PROJECT_NAME_SEPARATOR_CHAR);
        if (separatorChar == null) {
            return '.';
        }

        separatorChar = separatorChar.trim();
        if (separatorChar.length() != 1) {
            throw new CoreException(Status.error("Invalid 'project_name_separator_char' setting in project view!"));
        }

        return separatorChar.charAt(0);
    }

    @Override
    protected IStatus getProjectRecommendations(JavaProjectInfo javaInfo, IProgressMonitor monitor)
            throws CoreException {
        // with project-per-package we don't report additional java sources; the IDE will take care of it for us
        return javaInfo.analyzeProjectRecommendations(false, monitor);
    }

    private boolean isSupported(BazelTarget bazeltarget) {
        String ruleName;
        try {
            ruleName = bazeltarget.getRuleClass();
        } catch (CoreException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return switch (ruleName) {
            case "java_library", "java_import", "java_binary", "java_test": {
                yield true;

            }
            default: {
                yield requireNonNull(additionalJavaLikeRules, "additional java like rules not initialized")
                        .contains(ruleName);
            }
        };

    }

    private void logSourceFilesWithoutCommonRoot(BazelProject project, JavaSourceInfo sourceInfo, boolean isTestSources)
            throws CoreException {
        var sourceFilesInfo = new StringBuilder();
        for (JavaSourceEntry src : sourceInfo.getSourceFilesWithoutCommonRoot()) {
            sourceFilesInfo.append(" - ")
                    .append(src.getPath())
                    .append(": ")
                    .append(src.hasDetectedPackagePath() ? src.getPotentialSourceDirectoryRoot() : "<no package found>")
                    .append(System.lineSeparator());
        }
        LOG.error(
            "Unable to map {} completely to Bazel package '{}'. Please analyse sources for problems.\n\n{}",
            isTestSources ? "test sources" : "sources",
            project.getBazelPackage().getLabel(),
            sourceFilesInfo);
        createBuildPathProblem(
            project,
            Status.error(
                (isTestSources ? "Unable to map all test sources to a proper source directory."
                        : "Unable to map all sources to a proper source directory.")
                        + " Please check the error log and reach out for help."));
    }

    protected BazelProject provisionPackageProject(BazelPackage bazelPackage, IProgressMonitor monitor)
            throws CoreException {
        try {
            monitor.beginTask(format("Provisioning project for '//%s'", bazelPackage.getLabel().getPackagePath()), 2);
            if (!bazelPackage.hasBazelProject()) {
                // create project
                var packagePath = bazelPackage.getLabel().getPackagePath();
                var projectName = packagePath.isBlank() ? "__ROOT__"
                        : packagePath.replace('/', getProjectNameSeparatorChar(bazelPackage));

                // create the project directly within the package (note, there can be at most one project per package with this strategy anyway)
                var projectLocation = bazelPackage.getLocation();
                createProjectForElement(projectName, projectLocation, bazelPackage, monitor.slice(1));
            } else {
                // use existing project
                bazelPackage.getBazelProject().getProject();
            }

            return bazelPackage.getBazelProject();
        } catch (CoreException e) {
            throw new CoreException(
                    Status.error(format("Error provisioning project for package '%s'", bazelPackage), e));
        } finally {
            monitor.done();
        }
    }
}
