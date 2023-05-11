package com.salesforce.bazel.eclipse.ui.commands;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.jobs.Job;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.model.SynchronizeProjectViewJob;

public class SynchronizeProjectsHandler extends BaseBazelProjectHandler {

    @Override
    protected Job createJob(IProject project, ExecutionEvent event) throws CoreException {
        var bazelProject = BazelCore.create(project);
        var bazelWorkspace = bazelProject.getBazelWorkspace();

        return new SynchronizeProjectViewJob(bazelWorkspace, bazelWorkspace.getBazelProjectView());
    }
}
