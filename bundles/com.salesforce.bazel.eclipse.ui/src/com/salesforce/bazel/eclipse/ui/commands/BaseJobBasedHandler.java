package com.salesforce.bazel.eclipse.ui.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.jobs.Job;

public abstract class BaseJobBasedHandler extends AbstractHandler {

    protected abstract Job createJob(ExecutionEvent event) throws ExecutionException;

    @Override
    public Object execute(final ExecutionEvent event) throws ExecutionException {
        final var job = createJob(event);
        job.setUser(true);
        job.schedule();
        return null;
    }

}
