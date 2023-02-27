package com.salesforce.bazel.eclipse.ui.commands;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.ISources;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import com.salesforce.bazel.eclipse.ui.utils.BazelProjectUtilitis;

public abstract class BaseBazelProjectHandler extends BaseJobBasedHandler {

    @Override
    protected Job createJob(final ExecutionEvent event) throws ExecutionException {
        final var projects =
                BazelProjectUtilitis.findSelectedProjects(HandlerUtil.getActiveWorkbenchWindowChecked(event));

        if (projects.size() != 1) {
            MessageDialog.openError(HandlerUtil.getActiveShell(event), "Cannot Execute",
                "Please select only one Bazel projects!");
        }

        return createJob(projects.get(0), event);
    }

    protected abstract Job createJob(IProject project, ExecutionEvent event);

    @Override
    public void setEnabled(final Object evaluationContext) {
        var enabled = false;
        if ((evaluationContext instanceof IEvaluationContext context)) {
            final var object = context.getVariable(ISources.ACTIVE_WORKBENCH_WINDOW_NAME);
            if (object instanceof IWorkbenchWindow) {
                final var selectedProjects = BazelProjectUtilitis.findSelectedProjects((IWorkbenchWindow) object);
                enabled = selectedProjects.size() == 1;
            }
        }
        setBaseEnabled(enabled);
    }

}
