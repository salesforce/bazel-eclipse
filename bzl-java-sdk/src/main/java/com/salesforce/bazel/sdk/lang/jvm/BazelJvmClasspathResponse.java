package com.salesforce.bazel.sdk.lang.jvm;

import java.util.ArrayList;
import java.util.List;

import com.salesforce.bazel.sdk.project.BazelProject;

public class BazelJvmClasspathResponse {
    /**
     * The jvm classpath entries (e.g. jar files)
     */
    public JvmClasspathEntry[] jvmClasspathEntries = new JvmClasspathEntry[] {};

    /**
     * The list of projects that should be added to the classpath, if this environment is using project support. The
     * caller is expected to invoke the following: bazelProjectManager.setProjectReferences(bazelProject,
     * computedClasspath.classpathProjectReferences); But due to locking in some environments, this may need to be
     * delayed.
     */
    public List<BazelProject> classpathProjectReferences = new ArrayList<>();
}