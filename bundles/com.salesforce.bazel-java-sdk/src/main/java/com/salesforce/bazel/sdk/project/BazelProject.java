package com.salesforce.bazel.sdk.project;

import java.util.List;

import com.salesforce.bazel.sdk.model.BazelPackageInfo;

/**
 * A BazelProject is a logical concept that has no concrete artifact in the Bazel workspace. It is a BazelPackage that
 * represents a logical module or major component of a system. In the Maven world, this would be a Maven module.
 * <p>
 * A BazelProject can contain one or more Bazel packages.
 */
public class BazelProject {
    public String name;

    // TODO implement mapping of project to bazel packages contained within
    public List<BazelPackageInfo> bazelPackages;
    public BazelProjectManager bazelProjectManager;

    // the tool environment (e.g. IDE) may provide a project implementation object of its own, that is
    // stored here
    public Object projectImpl;

    public BazelProject(String name) {
        this.name = name;
    }

    public BazelProject(String name, List<BazelPackageInfo> packages) {
        this.name = name;
        this.bazelPackages = packages;
    }

    public BazelProject(String name, List<BazelPackageInfo> packages, Object projectImpl) {
        this.name = name;
        this.bazelPackages = packages;
        this.projectImpl = projectImpl;
    }

    public BazelProject(String name, Object projectImpl) {
        this.name = name;
        this.projectImpl = projectImpl;
    }

    public Object getProjectImpl() {
        return projectImpl;
    }
}
