package com.salesforce.bazel.eclipse.ui.commands;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.jobs.Job;

import com.salesforce.bazel.eclipse.core.BazelCorePlugin;
import com.salesforce.bazel.eclipse.core.classpath.InitializeOrRefreshClasspathJob;

public class UpdateClasspathsHandler extends BaseBazelProjectHandler {

    @Override
    protected Job createJob(IProject project, ExecutionEvent event) {
        return new InitializeOrRefreshClasspathJob(new IProject[] { project },
                BazelCorePlugin.getInstance().getBazelModelManager().getClasspathManager(), true /* force */);
    }
}
