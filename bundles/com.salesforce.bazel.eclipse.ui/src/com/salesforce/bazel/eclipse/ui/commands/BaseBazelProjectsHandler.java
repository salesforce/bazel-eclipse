package com.salesforce.bazel.eclipse.ui.commands;

import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.ISources;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.statushandlers.StatusManager;

import com.salesforce.bazel.eclipse.ui.utils.BazelProjectUtilitis;

public abstract class BaseBazelProjectsHandler extends BaseJobBasedHandler {

    @Override
    protected Job createJob(final ExecutionEvent event) throws ExecutionException {
        final var projects =
                BazelProjectUtilitis.findSelectedProjects(HandlerUtil.getActiveWorkbenchWindowChecked(event));

        if (projects.isEmpty()) {
            MessageDialog.openError(
                HandlerUtil.getActiveShell(event),
                "Cannot Execute",
                "Please select at least one Bazel projects!");
            throw new ExecutionException("No Bazel project selected");
        }

        try {
            return createJob(projects, event);
        } catch (CoreException e) {
            StatusManager.getManager().handle(e.getStatus(), StatusManager.SHOW | StatusManager.LOG);
            throw new ExecutionException("Unable to initialize command!", e);
        }
    }

    protected abstract Job createJob(List<IProject> projects, ExecutionEvent event) throws CoreException;

    @Override
    public void setEnabled(final Object evaluationContext) {
        var enabled = false;
        if ((evaluationContext instanceof IEvaluationContext context)) {
            final var object = context.getVariable(ISources.ACTIVE_WORKBENCH_WINDOW_NAME);
            if (object instanceof IWorkbenchWindow) {
                final var selectedProjects = BazelProjectUtilitis.findSelectedProjects((IWorkbenchWindow) object);
                enabled = selectedProjects.size() >= 1;
            }
        }
        setBaseEnabled(enabled);
    }

}
