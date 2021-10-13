/**
 * Copyright (c) 2020, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.bazel.sdk.lang.jvm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.aspect.jvm.JVMAspectOutputJarSet;
import com.salesforce.bazel.sdk.aspect.jvm.JVMAspectTargetInfo;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelBuildFile;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.project.BazelProjectTargets;
import com.salesforce.bazel.sdk.util.SimplePerfRecorder;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

/**
 * Computes a JVM classpath for a BazelProject based on the dependencies defined in Bazel, including resolving file
 * paths to jar files and accounting for references to BazelProjects.
 * <p>
 * This classpath implementation collapses the dependencies for all Java rules in the package into a single classpath,
 * with entries being either 'main' or 'test' entries. This may not be precise enough for all use cases. Also, targets
 * within the package are excluded from the classpath, as they are presumed to be represented by source code found in
 * source folders.
 */
public class BazelJvmClasspath implements JvmClasspath {
    // TODO make classpath cache timeout configurable
    private static final long CLASSPATH_CACHE_TIMEOUT_MS = 300000;

    protected final BazelWorkspace bazelWorkspace;
    protected final BazelProjectManager bazelProjectManager;
    protected final BazelProject bazelProject;
    protected final ImplicitClasspathHelper implicitDependencyHelper;
    protected final OperatingEnvironmentDetectionStrategy osDetector;
    protected final BazelCommandManager bazelCommandManager;
    private final LogHelper logger;

    private BazelJvmClasspathResponse cachedEntries;
    private long cachePutTimeMillis = 0;

    public BazelJvmClasspath(BazelWorkspace bazelWorkspace, BazelProjectManager bazelProjectManager,
            BazelProject bazelProject, ImplicitClasspathHelper implicitDependencyHelper,
            OperatingEnvironmentDetectionStrategy osDetector, BazelCommandManager bazelCommandManager) {
        this.bazelWorkspace = bazelWorkspace;
        this.bazelProjectManager = bazelProjectManager;
        this.bazelProject = bazelProject;
        this.implicitDependencyHelper = implicitDependencyHelper;
        this.osDetector = osDetector;
        this.bazelCommandManager = bazelCommandManager;

        logger = LogHelper.log(this.getClass());
    }

    public void clean() {
        cachedEntries = null;
        cachePutTimeMillis = 0;
    }

    /**
     * Computes the JVM classpath for the associated BazelProject
     * <p>
     * TODO provide different classpath strategies. This one the Maven-like/Eclipse JDT style, where the classpath is
     * the union of the classpaths of all java rules in the package.
     */
    @Override
    public BazelJvmClasspathResponse getClasspathEntries() {
        // sanity check
        if (bazelWorkspace == null) {
            // not sure how we could get here, but just check
            throw new IllegalStateException(
                    "Attempt to retrieve the classpath of a Bazel Java project prior to setting up the Bazel workspace.");
        }
        long startTimeMS = System.currentTimeMillis();

        boolean foundCachedEntries = false;
        boolean isImport = false;
        BazelJvmClasspathResponse response = new BazelJvmClasspathResponse();

        if (cachedEntries != null) {
            long now = System.currentTimeMillis();
            if ((now - cachePutTimeMillis) <= CLASSPATH_CACHE_TIMEOUT_MS) {
                logger.debug("  Using cached classpath for project " + bazelProject.name);
                return cachedEntries;
            }
            logger.info("Evicted classpath from cache for project " + bazelProject.name);
            cachedEntries = null;
        }

        logger.info("Computing classpath for project " + bazelProject.name + " (cached entries: " + foundCachedEntries
            + ", is import: " + isImport + ")");
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner =
                bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);

        Set<String> projectsAddedToClasspath = new HashSet<>();
        Map<String, JvmClasspathEntry> mainClasspathEntryMap = new TreeMap<>();
        Map<String, JvmClasspathEntry> testClasspathEntryMap = new TreeMap<>();
        Set<JvmClasspathEntry> implicitDeps = Collections.emptySet();

        try {
            BazelProjectTargets configuredTargetsForProject =
                    bazelProjectManager.getConfiguredBazelTargets(bazelProject, false);

            // get the model of the BUILD file for this package, which will tell us the type of each target and the list
            // of all targets if configured with the wildcard target
            BazelBuildFile bazelBuildFileModel = null;
            try {
                // we pass the targets that are configured for the current project to bazel query
                // typically, this is a single wildcard target, but the user may
                // also have specified explicit targets to use
                List<BazelLabel> labels = configuredTargetsForProject.getConfiguredTargets().stream()
                        .map(BazelLabel::new).collect(Collectors.toList());
                Collection<BazelBuildFile> buildFiles = bazelWorkspaceCmdRunner.queryBazelTargetsInBuildFile(labels);
                // since we only call query with labels for the same package, we expect to get a single BazelBuildFile instance back
                if (buildFiles.isEmpty()) {
                    throw new IllegalStateException("Unexpected empty BazelBuildFile collection, this is a bug");
                }
                if (buildFiles.size() > 1) {
                    throw new IllegalStateException("Expected a single BazelBuildFile instance, this is a bug");
                }
                bazelBuildFileModel = buildFiles.iterator().next();
            } catch (Exception anyE) {
                logger.error("Unable to compute classpath containers entries for project " + bazelProject.name, anyE);
                return returnEmptyClasspathOrThrow(anyE);
            }
            // now get the actual list of activated targets, with wildcard resolved using the BUILD file model if necessary
            Set<String> actualActivatedTargets = configuredTargetsForProject.getActualTargets(bazelBuildFileModel);

            Map<BazelLabel, Set<AspectTargetInfo>> targetLabelToAspectTargetInfos =
                    bazelWorkspaceCmdRunner.getAspectTargetInfos(actualActivatedTargets, "getClasspathEntries");

            for (String targetLabel : actualActivatedTargets) {
                String targetType = bazelBuildFileModel.getRuleTypeForTarget(targetLabel);
                boolean isTestTarget = "java_test".equals(targetType);

                Set<AspectTargetInfo> targetInfos = targetLabelToAspectTargetInfos.get(new BazelLabel(targetLabel));

                if (targetInfos == null) {
                    logger.warn("Failed to inspect target: " + targetLabel + ", skipping");
                    targetInfos = Collections.emptySet();
                }

                for (AspectTargetInfo targetInfo : targetInfos) {
                    if (!(targetInfo instanceof JVMAspectTargetInfo)) {
                        // this is not a aspect entry that can contribute to the JVM classpath
                        continue;
                    }
                    JVMAspectTargetInfo jvmTargetInfo = (JVMAspectTargetInfo) targetInfo;
                    String targetInfoLabelPath = jvmTargetInfo.getLabelPath();
                    String kind = jvmTargetInfo.getKind();
                    if ("java_import".equals(kind)) {
                        logger.info("Found java_import target with label {}", targetInfoLabelPath);
                    }

                    if (actualActivatedTargets.contains(targetInfoLabelPath)) {
                        if ("java_library".equals(kind) || "java_binary".equals(kind)) {
                            // this info describes a java_library target in the current package; don't add it to the classpath
                            // as all java_library targets in this package are assumed to be represented by source code entries
                            continue;
                        }

                        // java_test aspect should be analyzed for implicit dependencies
                        if ("java_test".equals(jvmTargetInfo.getKind())) {
                            implicitDeps = implicitDependencyHelper.computeImplicitDependencies(bazelWorkspace, jvmTargetInfo);
                            // there is no need to process test jar further
                            continue;
                        }
                        // else in some cases, the target is local, but we still want to proceed to process it below. the expected
                        // example here are java_import targets in the BUILD file that directly load jars from the file system
                        //   java_import(name = "zip4j", jars = ["lib/zip4j-2.6.4.jar"])
                        else if (!"java_import".equals(jvmTargetInfo.getKind())) {
                            // some other case like java_binary, proto_library, java_proto_library, etc
                            // proceed but log a warn
                            logger.info("Found unsupported target type as dependency: " + jvmTargetInfo.getKind()
                                    + "; the JVM classpath processor currently supports java_library or java_import.");
                        }
                    }

                    List<String> sourcePaths = jvmTargetInfo.getSources();
                    BazelProject otherProject = getSourceProjectForSourcePaths(sourcePaths);
                    if (otherProject == null) {
                        // no project found that houses the sources of this bazel target, add the jars to the classpath
                        // this means that this is an external jar, or a jar produced by a bazel target that was not imported

                        for (JVMAspectOutputJarSet jarSet : jvmTargetInfo.getGeneratedJars()) {
                            JvmClasspathEntry cpEntry = jarsToClasspathEntry(jarSet, isTestTarget);
                            if (cpEntry != null) {
                                addOrUpdateClasspathEntry(bazelWorkspaceCmdRunner, targetLabel, cpEntry, isTestTarget,
                                    mainClasspathEntryMap, testClasspathEntryMap);
                            } else {
                                // there was a problem with the aspect computation, this might resolve itself if we recompute it
                                bazelWorkspaceCmdRunner
                                .flushAspectInfoCache(configuredTargetsForProject.getConfiguredTargets());
                            }
                        }
                        for (JVMAspectOutputJarSet jarSet : jvmTargetInfo.getJars()) {
                            JvmClasspathEntry cpEntry = jarsToClasspathEntry(jarSet, isTestTarget);
                            if (cpEntry != null) {
                                addOrUpdateClasspathEntry(bazelWorkspaceCmdRunner, targetLabel, cpEntry, isTestTarget,
                                    mainClasspathEntryMap, testClasspathEntryMap);
                            } else {
                                // there was a problem with the aspect computation, this might resolve itself if we recompute it
                                bazelWorkspaceCmdRunner
                                .flushAspectInfoCache(configuredTargetsForProject.getConfiguredTargets());
                            }
                        }
                    } else { // otherProject != null
                        String otherBazelProjectName = otherProject.name;
                        if (! bazelProject.name.equals(otherBazelProjectName)) {
                            // add the referenced project to the classpath, directly as a project classpath entry
                            if (!projectsAddedToClasspath.contains(otherBazelProjectName)) {
                                BazelProject otherBazelProject = bazelProjectManager.getProject(otherBazelProjectName);
                                if (otherBazelProject == null) {
                                    otherBazelProject = new BazelProject(otherBazelProjectName);
                                }
                                JvmClasspathEntry cpEntry = new JvmClasspathEntry(otherBazelProject);
                                addOrUpdateClasspathEntry(bazelWorkspaceCmdRunner, targetLabel, cpEntry, isTestTarget,
                                    mainClasspathEntryMap, testClasspathEntryMap);
                            }
                            projectsAddedToClasspath.add(otherBazelProjectName);

                            // now make a project reference between this project and the other project; this allows for features like
                            // code refactoring across projects to work correctly
                            addProjectReference(response.classpathProjectReferences, otherProject);
                        }
                    }
                }
            } // for loop

        } catch (IOException | InterruptedException e) {
            logger.error("Unable to compute classpath containers entries for project {}, error: ", e,
                bazelProject.name);
            return returnEmptyClasspathOrThrow(e);
        } catch (BazelCommandLineToolConfigurationException e) {
            logger.error("Classpath could not be computed. Bazel not found: {}", e, e.getMessage());
            return returnEmptyClasspathOrThrow(e);
        } catch (RuntimeException re) {
            logger.error("Exception caught during classpath computation: {}", re, re.getMessage());
            return returnEmptyClasspathOrThrow(re);
        }

        // cache the entries
        cachePutTimeMillis = System.currentTimeMillis();
        response.jvmClasspathEntries = assembleClasspathEntries(mainClasspathEntryMap, testClasspathEntryMap, implicitDeps);
        cachedEntries = response;
        logger.debug("Cached the classpath for project " + bazelProject.name);

        SimplePerfRecorder.addTime("classpath", startTimeMS);

        return cachedEntries;
    }

    // INTERNAL

    private void addOrUpdateClasspathEntry(BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner, String targetLabel,
            JvmClasspathEntry cpEntry, boolean isTestTarget, Map<String, JvmClasspathEntry> mainClasspathEntryMap,
            Map<String, JvmClasspathEntry> testClasspathEntryMap) {
        if (cpEntry == null) {
            // something was wrong with the Aspect that described the entry, flush the cache
            bazelWorkspaceCmdRunner.flushAspectInfoCache(targetLabel);
            return;
        }

        String pathStr = cpEntry.pathToJar;
        if (pathStr == null) {
            pathStr = cpEntry.bazelProject.name;
        }
        //logger.info("Adding cp entry ["+pathStr+"] to target ["+targetLabel+"]");
        if (!isTestTarget) {
            // add to the main classpath?
            // if this was previously a test CP entry, we need to remove it since this is now a main cp entry
            testClasspathEntryMap.remove(pathStr);

            // make it main cp
            mainClasspathEntryMap.put(pathStr, cpEntry);
        } else if (!mainClasspathEntryMap.containsKey(pathStr)) {
            // add to the test classpath?
            // if it already exists in the main classpath, do not also add to the test classpath
            testClasspathEntryMap.put(pathStr, cpEntry);
        }
    }

    private JvmClasspathEntry[] assembleClasspathEntries(Map<String, JvmClasspathEntry> mainClasspathEntryMap,
            Map<String, JvmClasspathEntry> testClasspathEntryMap, Set<JvmClasspathEntry> implicitClasspathEntrySet) {
        List<JvmClasspathEntry> classpathEntries = new ArrayList<>(mainClasspathEntryMap.values());
        classpathEntries.addAll(testClasspathEntryMap.values());
        // should be added at the end of classpath entries
        classpathEntries.addAll(implicitClasspathEntrySet);
        return classpathEntries.toArray(new JvmClasspathEntry[] {});
    }

    /**
     * Returns the IJavaProject in the current workspace that contains at least one of the specified sources.
     */
    private BazelProject getSourceProjectForSourcePaths(List<String> sources) {
        if (sources == null) {
            return null;
        }
        for (String candidate : sources) {
            BazelProject project = bazelProjectManager.getOwningProjectForSourcePath(bazelWorkspace, candidate);
            if (project != null) {
                return project;
            }
        }
        return null;
    }

    private JvmClasspathEntry jarsToClasspathEntry(JVMAspectOutputJarSet jarSet, boolean isTestLib) {
        JvmClasspathEntry cpEntry;
        cpEntry = new JvmClasspathEntry(jarSet.getJar(), jarSet.getSrcJar(), isTestLib);
        return cpEntry;
    }

    @SuppressWarnings("unused")
    private JvmClasspathEntry[] jarsToClasspathEntries(BazelWorkspace bazelWorkspace,
            WorkProgressMonitor progressMonitor, Set<JVMAspectOutputJarSet> jars, boolean isTestLib) {
        JvmClasspathEntry[] entries = new JvmClasspathEntry[jars.size()];
        int i = 0;
        bazelWorkspace.getBazelOutputBaseDirectory();
        bazelWorkspace.getBazelExecRootDirectory();
        for (JVMAspectOutputJarSet jar : jars) {
            entries[i] = jarsToClasspathEntry(jar, isTestLib);
            i++;
        }
        return entries;
    }

    private void addProjectReference(List<BazelProject> projectList, BazelProject addThis) {
        for (BazelProject existingProject : projectList) {
            if (existingProject.name.equals(addThis.name)) {
                // we already have a reference to this project
                return;
            }
        }
        projectList.add(addThis);
    }

    private void continueOrThrow(Throwable th) {
        // under real usage, we suppress fatal exceptions because sometimes there are IDE timing issues that can
        // be corrected if the classpath is computed again.
        // But under tests, we want to fail fatally otherwise tests could pass when they shouldn't
        if (osDetector.isTestRuntime()) {
            throw new IllegalStateException("The classpath could not be computed by the BazelClasspathContainer", th);
        }
    }

    private BazelJvmClasspathResponse returnEmptyClasspathOrThrow(Throwable th) {
        continueOrThrow(th);
        return new BazelJvmClasspathResponse();
    }

}
