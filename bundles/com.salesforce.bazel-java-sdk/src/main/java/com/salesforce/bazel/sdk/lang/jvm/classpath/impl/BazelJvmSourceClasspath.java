/**
 * Copyright (c) 2021, Salesforce.com, Inc. All rights reserved.
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

import java.io.File;
import java.util.Set;

import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.index.CodeIndexEntry;
import com.salesforce.bazel.sdk.index.jvm.BazelJvmIndexClasspath;
import com.salesforce.bazel.sdk.index.jvm.JvmCodeIndex;
import com.salesforce.bazel.sdk.index.model.CodeLocationDescriptor;
import com.salesforce.bazel.sdk.lang.jvm.JavaSourceFile;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspath;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathResponse;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.project.structure.ProjectStructure;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

/**
 * Classpath loader that uses import data from the Java files to determine the actual classpath, as opposed to the
 * BazelJvmUnionClasspath that uses the Bazel BUILD file.
 * 
 * TODO this should be reimplemented as a classpath strategy instead.
 */
public class BazelJvmSourceClasspath implements JvmClasspath {
    private static final LogHelper LOG = LogHelper.log(BazelJvmSourceClasspath.class);

    protected final BazelWorkspace bazelWorkspace;
    protected final BazelProjectManager bazelProjectManager;
    protected final BazelProject bazelProject;
    protected final ImplicitClasspathHelper implicitDependencyHelper;
    protected final OperatingEnvironmentDetectionStrategy osDetector;
    protected final BazelCommandManager bazelCommandManager;
    private final LogHelper logger;

    protected BazelJvmIndexClasspath classIndex;

    /**
     *
     * @param bazelWorkspace
     *            gateway to a number of workspace level features
     * @param bazelProjectManager
     *            project manager is used to help build references to other projects
     * @param bazelProject
     *            the project (aka a Bazel package that corresponds to the concept of a Maven module)
     * @param implicitDependencyHelper
     *            helper utility that computes annoying implicit dependencies added by Bazel
     * @param osDetector
     *            use this when do OS specific work - this allows us to mock the OS in tests
     * @param bazelCommandManager
     *            gateway object for running Bazel commands
     * @param classIndex
     *            the index of jars in the workspace, and the classes that each contains
     */
    public BazelJvmSourceClasspath(BazelWorkspace bazelWorkspace, BazelProjectManager bazelProjectManager,
            BazelProject bazelProject, ImplicitClasspathHelper implicitDependencyHelper,
            OperatingEnvironmentDetectionStrategy osDetector, BazelCommandManager bazelCommandManager,
            BazelJvmIndexClasspath classIndex) {
        this.bazelWorkspace = bazelWorkspace;
        this.bazelProjectManager = bazelProjectManager;
        this.bazelProject = bazelProject;
        this.implicitDependencyHelper = implicitDependencyHelper;
        this.osDetector = osDetector;
        this.bazelCommandManager = bazelCommandManager;
        this.classIndex = classIndex;

        this.logger = LogHelper.log(this.getClass());
    }

    @Override
    public JvmClasspathResponse getClasspathEntries() {
        // the structure contains the file system layout of source files
        ProjectStructure fileStructure = bazelProject.getProjectStructure();

        // get the index, if one has been computed
        JvmCodeIndex index = JvmCodeIndex.getWorkspaceIndex(bazelWorkspace);

        if (index != null) {
            LOG.info("Computing the dynamic classpath for project {}", fileStructure.projectPath);
            File workspaceRootDir = bazelWorkspace.getBazelWorkspaceRootDirectory();

            for (String sourceDirPath : fileStructure.mainSourceDirFSPaths) {
                File sourceDir = new File(workspaceRootDir, sourceDirPath);
                Set<File> javaFiles = FSPathHelper.findFileLocations(sourceDir, ".java", null, 50);
                for (File file : javaFiles) {
                    if (file.exists()) {
                        JavaSourceFile javaFile = new JavaSourceFile(file);

                        String javaPackage = javaFile.readPackageFromFile();
                        LOG.info("Source file {} is in Java package {}", file.getAbsolutePath(), javaPackage);

                        // TODO do parsing stuff with the javaFile, for example extract the imports as a list
                        // until we do that, we just use the index to print out the jar file that this class
                        // is found in, to show how to use the index

                        String typeName = javaPackage + "." + file.getName().substring(0, file.getName().length() - 5);
                        CodeIndexEntry indexEntry = index.typeDictionary.get(typeName);

                        if (indexEntry == null) {
                            // it is possible the source file is not a target of any rule
                            LOG.info("Type {} is not found in any built jar in the workspace.", typeName);
                        } else {
                            if (indexEntry.singleLocation != null) {
                                LOG.info("Type {} is built into {} in the workspace.", typeName,
                                    indexEntry.singleLocation.locationOnDisk.getAbsolutePath());
                            } else if ((indexEntry.multipleLocations != null)
                                    && (indexEntry.multipleLocations.size() > 0)) {
                                for (CodeLocationDescriptor location : indexEntry.multipleLocations) {
                                    LOG.info("Type {} is built into {} in the workspace.", typeName,
                                        location.locationOnDisk.getAbsolutePath());
                                }
                            } else {
                                // it is possible the source file is not a target of any rule
                                LOG.info("Type {} is not found in any built jar in the workspace.", typeName);
                            }
                        }
                    }
                }
            }
        }

        // TODO until we have the dynamic classpath implemented, just fake it
        return new JvmClasspathResponse();
    }

    @Override
    public void clean() {
    }
}
