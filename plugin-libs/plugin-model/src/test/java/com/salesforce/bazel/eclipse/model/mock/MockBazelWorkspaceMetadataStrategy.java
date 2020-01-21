package com.salesforce.bazel.eclipse.model.mock;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.salesforce.bazel.eclipse.model.BazelWorkspaceCommandOptions;
import com.salesforce.bazel.eclipse.model.BazelWorkspaceMetadataStrategy;
import com.salesforce.bazel.eclipse.model.OperatingEnvironmentDetectionStrategy;

/**
 * Mock impl for BazelWorkspaceMetadataStrategy suitable for tests. It mocks various 
 * metadata queries that we do against Bazel workspaces.
 * <p>
 * Note that this implementation just returns in-memory File objects, it does not actually create 
 * the corresponding dirs/files on the file system.
 */
public class MockBazelWorkspaceMetadataStrategy implements BazelWorkspaceMetadataStrategy {

    public String testWorkspaceName = null;
    public File workspaceRootDir = null;
    public File outputBaseDir = null;
    public OperatingEnvironmentDetectionStrategy os = null;
    
    // default paths are in sync with TestBazelWorkspaceFactory, which may be used to build a real test workspace on the file system
    // but override the below to change for a specific test
    
    public String execRootPath = null;  // default: [outputbase]/execroot/test_workspace
    public String binPath = null; // default: [outputbase]/execroot/test_workspace/bazel-out/darwin-fastbuild/bin
    
    /**
     * The File passed as the workspace need not be populated. This mock impl will 
     * @param workspaceRootDir
     */
    public MockBazelWorkspaceMetadataStrategy(String testWorkspaceName, File workspaceRootDir, File outputBaseDir, OperatingEnvironmentDetectionStrategy os) {
        this.testWorkspaceName = testWorkspaceName;
        this.workspaceRootDir = workspaceRootDir;
        this.outputBaseDir = outputBaseDir;
        this.os = os;
    }
    
    @Override
    public File computeBazelWorkspaceExecRoot() {
        File er = null;
        
        if (execRootPath == null) {
            execRootPath = "execroot/"+testWorkspaceName;
        } 
        er = new File(outputBaseDir, execRootPath);
        return er;
    }

    @Override
    public File computeBazelWorkspaceOutputBase() {
        return outputBaseDir;
    }

    @Override
    public File computeBazelWorkspaceBin() {
        File wb = null;
        
        if (binPath == null) {
            binPath = "execroot/"+testWorkspaceName+"/bazel-out/"+os.getOperatingSystemDirectoryName(os.getOperatingSystemName())+"-fastbuild/bin";
        }
        wb = new File(outputBaseDir, binPath);
        return wb;
    }

    private List<String> optionLines;
    
    public void mockCommandLineOptionOutput(List<String> optionLines) {
        this.optionLines = optionLines;
    }
    
    @Override
    public void populateBazelWorkspaceCommandOptions(BazelWorkspaceCommandOptions commandOptions) {
        if (this.optionLines == null) {
            this.optionLines = new ArrayList<>();
            this.optionLines.add("Inherited 'common' options: --isatty=1 --terminal_columns=260");
            this.optionLines.add("Inherited 'build' options: --javacopt=-source 8 -target 8 --host_javabase=//tools/jdk:my-linux-jdk11 --javabase=//tools/jdk:my-linux-jdk8 --stamp");
        }
        commandOptions.parseOptionsFromOutput(this.optionLines);
    }

}
