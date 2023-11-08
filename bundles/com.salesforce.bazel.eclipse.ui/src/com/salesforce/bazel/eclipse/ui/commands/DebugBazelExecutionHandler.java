package com.salesforce.bazel.eclipse.ui.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

public class DebugBazelExecutionHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        new BazelDiagnosticsJob().schedule();

        // nothing
        return null;
    }

}
