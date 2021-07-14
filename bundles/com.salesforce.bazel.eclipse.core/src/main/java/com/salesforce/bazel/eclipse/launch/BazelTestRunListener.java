package com.salesforce.bazel.eclipse.launch;

import org.eclipse.jdt.junit.TestRunListener;
import org.eclipse.jdt.junit.model.ITestCaseElement;
import org.eclipse.jdt.junit.model.ITestRunSession;

import com.salesforce.bazel.sdk.logging.LogHelper;

/**
 * Logging hooks for test execution from BEF.
 */
public class BazelTestRunListener extends TestRunListener {
    static final LogHelper LOG = LogHelper.log(BazelTestRunListener.class);

    @Override
    public void sessionLaunched(ITestRunSession session) {
        LOG.info("test session launched for project {}", session.getLaunchedProject().getProject().getName());
    }

    @Override
    public void sessionStarted(ITestRunSession session) {
        LOG.info("test session started for project {} run {}", session.getLaunchedProject().getProject().getName(),
            session.getTestRunName());
    }

    @Override
    public void sessionFinished(ITestRunSession session) {
        LOG.info("test session finished for project {}", session.getLaunchedProject().getProject().getName());
    }

    @Override
    public void testCaseStarted(ITestCaseElement testCaseElement) {
        LOG.info("test case {}.{} started", testCaseElement.getTestClassName(), testCaseElement.getTestMethodName());
    }

    @Override
    public void testCaseFinished(ITestCaseElement testCaseElement) {
        LOG.info("test case {}.{} finished", testCaseElement.getTestClassName(), testCaseElement.getTestMethodName());
    }

}
