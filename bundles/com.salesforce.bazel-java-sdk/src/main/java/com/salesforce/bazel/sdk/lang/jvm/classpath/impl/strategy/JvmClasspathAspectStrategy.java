/**
 * Copyright (c) 2022, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.sdk.lang.jvm.classpath.impl.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.aspect.jvm.JVMAspectOutputJarSet;
import com.salesforce.bazel.sdk.aspect.jvm.JVMAspectTargetInfo;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathData;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathEntry;
import com.salesforce.bazel.sdk.lang.jvm.classpath.impl.util.ImplicitClasspathHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelTargetKind;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.project.BazelProjectTargets;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

/**
 * Classpath strategy implementation that uses a Bazel Aspect to compute the classpath.
 * This is the most cost efficient way to compute the classpath (other than persisted cache).
 * <p>
 * The Aspect causes Bazel to write one or more json files for each target. Each json file contains
 * detailed dependency information.
 * <p>
 * This strategy will only work if the package does not have a build error. Bazel will not write
 * the aspect output files if there is a build error.
 */
public class JvmClasspathAspectStrategy extends JvmClasspathStrategy {
    private static Logger LOG = LoggerFactory.getLogger(JvmClasspathAspectStrategy.class);

    public JvmClasspathAspectStrategy(BazelWorkspace bazelWorkspace, BazelProjectManager bazelProjectManager,
            ImplicitClasspathHelper implicitDependencyHelper, OperatingEnvironmentDetectionStrategy osDetector,
            BazelCommandManager bazelCommandManager) {
        super(bazelWorkspace, bazelProjectManager, implicitDependencyHelper, osDetector, bazelCommandManager);
    }

    @Override
    public JvmClasspathData getClasspathForTarget(JvmClasspathStrategyRequest request) throws Exception {

        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner =
                bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);
        Set<String> projectsAddedToClasspath = new HashSet<>();

        // TODO need to support runtime classpath
        boolean isTestTarget = request.targetKind.isTestable();

        // each project target can depend on many downstream targets
        // we need to query bazel to load all the aspect info objects that are associated with the full
        // dependency graph of the requested target
            Map<BazelLabel, Set<AspectTargetInfo>> targetLabelToAspectTargetInfos =
                    bazelWorkspaceCmdRunner.getAspectTargetInfos(request.actualActivatedTargets, "getClasspathEntries");
            Set<AspectTargetInfo> targetInfos = targetLabelToAspectTargetInfos.get(new BazelLabel(request.targetLabel));

            if (targetInfos == null) {
                LOG.warn("Failed to inspect target: " + request.targetLabel + ", skipping");
                targetInfos = Collections.emptySet();
                request.classpathData.isComplete = false;
            } else {
                // ok, we have real aspect data, mark this classpath complete
                request.classpathData.isComplete = true;
            }

            // iterate through each aspect info object for every upstream dependency
            for (AspectTargetInfo targetInfo : targetInfos) {
                if (!(targetInfo instanceof JVMAspectTargetInfo)) {
                    // this is not a aspect entry that can contribute to the JVM classpath
                    continue;
                }
                JVMAspectTargetInfo jvmTargetInfo = (JVMAspectTargetInfo) targetInfo;
                String targetInfoLabelPath = jvmTargetInfo.getLabelPath();
                BazelTargetKind aspectKind = jvmTargetInfo.getKind();

                LOG.debug("Found java_import target of kind {} with label {}", aspectKind.getKindName(), targetInfoLabelPath);

                if (request.actualActivatedTargets.contains(targetInfoLabelPath)) {
                    if (aspectKind.isKind("java_library") || aspectKind.isKind("java_binary")) {
                        // this info describes a java_library target in the current package; don't add it to the classpath
                        // as all java_library targets in this package are assumed to be represented by source code entries
                        continue;
                    }

                    // java_test aspect should be analyzed for implicit dependencies
                    if (aspectKind.isTestable()) {
                        request.classpathData.implicitDeps = implicitDependencyHelper.computeImplicitDependencies(bazelWorkspace,
                            jvmTargetInfo.getLabel(), aspectKind);
                        // there is no need to process test jar further
                        continue;
                    }
                    // else in some cases, the target is local, but we still want to proceed to process it below. the expected
                    // example here are java_import targets in the BUILD file that directly load jars from the file system
                    //   java_import(name = "zip4j", jars = ["lib/zip4j-2.6.4.jar"]) TODO this comment does not match the code?
                    else if (!aspectKind.isKind("java_import")) {
                        // some other case like java_binary, proto_library, java_proto_library, etc
                        // proceed but log a warn
                        LOG.info("Found unsupported target type as dependency: " + aspectKind.getKindName()
                                + "; the JVM classpath processor currently supports java_library or java_import.");
                    }
                }

                List<String> sourcePaths = jvmTargetInfo.getSources();
                BazelProject otherProject = getSourceProjectForSourcePaths(sourcePaths);
                if (otherProject == null) {
                    // no project found that houses the sources of this bazel target, add the jars to the classpath
                    // this means that this is an external jar, or a jar produced by a bazel target that was not imported

                    // TODO implement runtime classpath

                    addTargetJarsIntoClasspath(bazelWorkspaceCmdRunner, request.classpathData, request.configuredTargetsForProject,
                        request.targetLabel, isTestTarget, jvmTargetInfo);
                } else { // otherProject != null
                    String otherBazelProjectName = otherProject.name;
                    if (!request.bazelProject.name.equals(otherBazelProjectName)) {
                        // add the referenced project to the classpath, directly as a project classpath entry
                        if (!projectsAddedToClasspath.contains(otherBazelProjectName)) {
                            BazelProject otherBazelProject = bazelProjectManager.getProject(otherBazelProjectName);
                            if (otherBazelProject == null) {
                                otherBazelProject = new BazelProject(otherBazelProjectName);
                            }
                            JvmClasspathEntry cpEntry = new JvmClasspathEntry(otherBazelProject);
                            addOrUpdateClasspathEntry(bazelWorkspaceCmdRunner, request.targetLabel, cpEntry, isTestTarget, request.classpathData);
                        }
                        projectsAddedToClasspath.add(otherBazelProjectName);

                        // now make a project reference between this project and the other project; this allows for features like
                        // code refactoring across projects to work correctly
                        addProjectReference(request.classpathData.classpathProjectReferences, otherProject);
                    } else {
                        // project might have a generated sources and been already imported into the workspace.
                        // if it is not a binary, library or test type, then it should be included into the classpath
                        boolean skipTarget =
                                aspectKind.isKind("java_library") || aspectKind.isKind("java_binary") || aspectKind.isKind("java_test");
                        if (!skipTarget) {
                            addTargetJarsIntoClasspath(bazelWorkspaceCmdRunner, request.classpathData, request.configuredTargetsForProject,
                                request.targetLabel, isTestTarget, jvmTargetInfo);
                        }
                    } // else name equals
                } // else otherProject != null
            } // for targetInfos

        request.classpathData.jvmClasspathEntries = assembleClasspathEntries(request.classpathData);

        return request.classpathData;
    }

    protected void addOrUpdateClasspathEntry(BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner, String targetLabel,
            JvmClasspathEntry cpEntry, boolean isTestTarget, JvmClasspathData classpathData) {
        if (cpEntry == null) {
            // something was wrong with the Aspect that described the entry, flush the cache
            bazelWorkspaceCmdRunner.flushAspectInfoCache(targetLabel);
            return;
        }

        String pathStr = cpEntry.pathToJar;
        if (pathStr == null) {
            pathStr = cpEntry.bazelProject.name;
        }
        //LOG.info("Adding cp entry ["+pathStr+"] to target ["+targetLabel+"]");
        if (!isTestTarget) {
            // add to the main classpath?
            // if this was previously a test CP entry, we need to remove it since this is now a main cp entry
            classpathData.testClasspathEntryMap.remove(pathStr);

            // make it main cp
            classpathData.mainClasspathEntryMap.put(pathStr, cpEntry);
        } else if (!classpathData.mainClasspathEntryMap.containsKey(pathStr)) {
            // add to the test classpath?
            // if it already exists in the main classpath, do not also add to the test classpath
            classpathData.testClasspathEntryMap.put(pathStr, cpEntry);
        }
    }

    protected void addTargetJarsIntoClasspath(BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner,
            JvmClasspathData classpathData, BazelProjectTargets configuredTargetsForProject, String targetLabel,
            boolean isTestTarget, JVMAspectTargetInfo jvmTargetInfo) {

        for (JVMAspectOutputJarSet jarSet : jvmTargetInfo.getGeneratedJars()) {
            addJarIntoClasspath(bazelWorkspaceCmdRunner, classpathData, configuredTargetsForProject, targetLabel,
                isTestTarget, jarSet);
        }

        for (JVMAspectOutputJarSet jarSet : jvmTargetInfo.getJars()) {
            addJarIntoClasspath(bazelWorkspaceCmdRunner, classpathData, configuredTargetsForProject, targetLabel,
                isTestTarget, jarSet);
        }
    }

    protected void addJarIntoClasspath(BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner,
            JvmClasspathData classpathData, BazelProjectTargets configuredTargetsForProject, String targetLabel,
            boolean isTestTarget, JVMAspectOutputJarSet jarSet) {

        JvmClasspathEntry cpEntry = jarsToClasspathEntry(jarSet, false, isTestTarget);
        if (cpEntry != null) {
            addOrUpdateClasspathEntry(bazelWorkspaceCmdRunner, targetLabel, cpEntry, isTestTarget, classpathData);
        } else {
            // there was a problem with the aspect computation, this might resolve itself if we recompute it
            bazelWorkspaceCmdRunner.flushAspectInfoCache(configuredTargetsForProject.getConfiguredTargets());
        }

    }

    protected JvmClasspathEntry[] assembleClasspathEntries(JvmClasspathData classpathData) {
        List<JvmClasspathEntry> classpathEntries = new ArrayList<>(classpathData.mainClasspathEntryMap.values());
        classpathEntries.addAll(classpathData.testClasspathEntryMap.values());
        // should be added at the end of classpath entries
        classpathEntries.addAll(classpathData.implicitDeps);
        return classpathEntries.toArray(new JvmClasspathEntry[] {});
    }

}
