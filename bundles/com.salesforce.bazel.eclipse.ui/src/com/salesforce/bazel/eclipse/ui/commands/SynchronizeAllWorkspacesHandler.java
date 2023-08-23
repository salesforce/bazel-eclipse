package com.salesforce.bazel.eclipse.ui.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.SynchronizeProjectViewJob;

public class SynchronizeAllWorkspacesHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        try {
            var bazelWorkspaces = BazelCore.getModel().getBazelWorkspaces();
            for (BazelWorkspace bazelWorkspace : bazelWorkspaces) {
                var job = new SynchronizeProjectViewJob(bazelWorkspace);
                job.setUser(true);
                job.schedule();
            }
        } catch (CoreException e) {
            throw new ExecutionException("Unknown Error scheduling refresh jobs", e);
        }

        // nothing
        return null;
    }

}
