package com.salesforce.bazel.eclipse.ui.commands;

import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.jobs.Job;

import com.salesforce.bazel.eclipse.core.BazelCorePlugin;
import com.salesforce.bazel.eclipse.core.classpath.InitializeOrRefreshClasspathJob;

public class UpdateClasspathsHandler extends BaseBazelProjectsHandler {

    @Override
    protected Job createJob(List<IProject> projects, ExecutionEvent event) throws CoreException {
        return new InitializeOrRefreshClasspathJob(
                projects,
                BazelCorePlugin.getInstance().getBazelModelManager().getClasspathManager(),
                true /* force */);
    }
}
