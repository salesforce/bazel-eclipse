package com.salesforce.bazel.eclipse.ui.launchconfiguration;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.ui.WorkingDirectoryBlock;
import org.eclipse.jdt.launching.JavaRuntime;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.launchconfiguration.BazelLaunchConfigurationConstants;
import com.salesforce.bazel.eclipse.core.model.BazelProject;

public class BazelWorkingDirectoryBlock extends WorkingDirectoryBlock {

    /**
     * Constructs a new working directory block.
     */
    public BazelWorkingDirectoryBlock() {
        super(BazelLaunchConfigurationConstants.WORKING_DIRECTORY);
    }

    @Override
    protected IProject getProject(ILaunchConfiguration configuration) throws CoreException {
        var javaProject = JavaRuntime.getJavaProject(configuration);
        if (javaProject == null) {
            return null;
        }

        var project = javaProject.getProject();
        if (BazelProject.isBazelProject(project)) {
            // default to the Bazel workspace project as working directory location
            return BazelCore.create(project).getBazelWorkspace().getBazelProject().getProject();
        }
        return project;
    }

    @Override
    protected void log(CoreException e) {
        setErrorMessage(e.getMessage());
    }
}