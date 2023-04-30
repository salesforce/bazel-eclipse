package testdata.utils;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.eclipse.core.runtime.AssertionFailedException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.ProgressProvider;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

public class LoggingProgressProviderExtension extends ProgressProvider
        implements Extension, BeforeAllCallback, AfterAllCallback {

    private ProgressProvider oldProgressProvider;
    private IJobManager jobManager;

    @Override
    public IProgressMonitor createMonitor(Job job) {
        return new LoggingProgressMonitor(job);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        var jobManager = this.jobManager;
        if (jobManager != null) {
            jobManager.setProgressProvider(oldProgressProvider);
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        jobManager = Job.getJobManager();
        assertNotNull(jobManager,
            "The Eclipse Jobs API is not porperly initialized. This extension must be running as an OSGi plug-in test.");

        var progressProviderField = jobManager.getClass().getDeclaredField("progressProvider");
        assertNotNull(progressProviderField,
            "The Eclipse Jobs API changed in an incompatible way. This extension is therfore broken and needs modifications!");

        if (!progressProviderField.trySetAccessible()) {
            throw new AssertionFailedException("Unable to read the old progress provider.");
        }

        oldProgressProvider = (ProgressProvider) progressProviderField.get(jobManager);
        jobManager.setProgressProvider(this);
    }

}
