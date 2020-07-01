package com.salesforce.bazel.eclipse.workspace.test;

/**
 * Descriptor for a manufactured target (java_library, java_test, etc) in a manufactured bazel package
 * in a test workspace.
 */
public class TestBazelTargetDescriptor {

    public TestBazelPackageDescriptor parentPackage;
    
    // Ex: projects/libs/javalib0:javalib0
    public String targetPath;
    
    // Ex: javalib0
    public String targetName;
    
    // Ex: java_library
    public String targetType;

    public TestBazelTargetDescriptor(TestBazelPackageDescriptor parentPackage, String targetName,
            String targetType) {
        this.parentPackage = parentPackage;
        this.targetPath = parentPackage.packagePath + ":" + targetName;
        this.targetName = targetName;
        this.targetType = targetType;
        
        this.parentPackage.parentWorkspaceDescriptor.createdTargets.put(targetPath, this);
        this.parentPackage.targets.put(targetPath, this);
    }
    
}
