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
package com.salesforce.bazel.sdk.lang.jvm.classpath.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.aspect.jvm.JVMAspectOutputJarSet;
import com.salesforce.bazel.sdk.aspect.jvm.JVMAspectTargetInfo;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathEntry;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathData;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
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
public class BazelAspectJvmClasspathStrategy extends BazelJvmClasspathStrategy {
    private final LogHelper logger;

    public BazelAspectJvmClasspathStrategy(BazelWorkspace bazelWorkspace, BazelProjectManager bazelProjectManager,
            ImplicitClasspathHelper implicitDependencyHelper, OperatingEnvironmentDetectionStrategy osDetector,
            BazelCommandManager bazelCommandManager) {
        super(bazelWorkspace, bazelProjectManager, implicitDependencyHelper, osDetector, bazelCommandManager);

        logger = LogHelper.log(this.getClass());
    }

    @Override
    public JvmClasspathData getClasspathForTarget(BazelProject bazelProject, String targetLabel, String targetType,
            BazelProjectTargets configuredTargetsForProject, Set<String> actualActivatedTargets, JvmClasspathData classpathData) {

        // TODO need to support runtime classpath
        // TODO need to eliminate the JDT main/test model here, this strategy should be agnostic to that
        Set<String> projectsAddedToClasspath = new HashSet<>();

        boolean isTestTarget = "java_test".equals(targetType);

        try {
            BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner =
                    bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);
            Map<BazelLabel, Set<AspectTargetInfo>> targetLabelToAspectTargetInfos =
                    bazelWorkspaceCmdRunner.getAspectTargetInfos(actualActivatedTargets, "getClasspathEntries");
            Set<AspectTargetInfo> targetInfos = targetLabelToAspectTargetInfos.get(new BazelLabel(targetLabel));

            if (targetInfos == null) {
                logger.warn("Failed to inspect target: " + targetLabel + ", skipping");
                targetInfos = Collections.emptySet();
                classpathData.isComplete = false;
            } else {
                // ok, we have real aspect data, mark this classpath complete
                classpathData.isComplete = true;
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
                        classpathData.implicitDeps = implicitDependencyHelper.computeImplicitDependencies(bazelWorkspace, jvmTargetInfo);
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

                    // TODO implement runtime classpath
                    
                    addTargetJarsIntoClasspath(bazelWorkspaceCmdRunner, classpathData, configuredTargetsForProject, targetLabel, 
                        isTestTarget, jvmTargetInfo);
                } else { // otherProject != null
                    String otherBazelProjectName = otherProject.name;
                    if (!bazelProject.name.equals(otherBazelProjectName)) {
                        // add the referenced project to the classpath, directly as a project classpath entry
                        if (!projectsAddedToClasspath.contains(otherBazelProjectName)) {
                            BazelProject otherBazelProject = bazelProjectManager.getProject(otherBazelProjectName);
                            if (otherBazelProject == null) {
                                otherBazelProject = new BazelProject(otherBazelProjectName);
                            }
                            JvmClasspathEntry cpEntry = new JvmClasspathEntry(otherBazelProject);
                            addOrUpdateClasspathEntry(bazelWorkspaceCmdRunner, targetLabel, cpEntry, isTestTarget, classpathData);
                        }
                        projectsAddedToClasspath.add(otherBazelProjectName);

                        // now make a project reference between this project and the other project; this allows for features like
                        // code refactoring across projects to work correctly
                        addProjectReference(classpathData.classpathProjectReferences, otherProject);
                    } else {
                        // project might have a generated sources and been already imported into the workspace.
                        // if it is not a binary, library or test type, then it should be included into the classpath
                        boolean skipTarget =
                                "java_library".equals(kind) || "java_binary".equals(kind) || "java_test".equals(kind);
                        if (!skipTarget) {
                            addTargetJarsIntoClasspath(bazelWorkspaceCmdRunner, classpathData, configuredTargetsForProject, 
                                targetLabel, isTestTarget, jvmTargetInfo);
                        } 
                    } // else name equals
                } // else otherProject != null
            } // for targetInfos
        } // try
        catch (IOException | InterruptedException e) {
            logger.error("Unable to compute classpath containers entries for project {}, error: ", e,
                bazelProject.name);
            classpathData.isComplete = false;
            return returnEmptyClasspathOrThrow(e);
        } catch (BazelCommandLineToolConfigurationException e) {
            logger.error("Classpath could not be computed. Bazel not found: {}", e, e.getMessage());
            classpathData.isComplete = false;
            return returnEmptyClasspathOrThrow(e);
        }
        
        classpathData.jvmClasspathEntries = assembleClasspathEntries(classpathData);
        
        return classpathData;
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
        //logger.info("Adding cp entry ["+pathStr+"] to target ["+targetLabel+"]");
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
