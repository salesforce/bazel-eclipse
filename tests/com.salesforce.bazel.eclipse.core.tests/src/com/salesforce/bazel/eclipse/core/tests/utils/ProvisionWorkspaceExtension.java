package com.salesforce.bazel.eclipse.core.tests.utils;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.setup.ImportBazelWorkspaceJob;

/**
 * JUnit extension for provisioning a workspace.
 * <p>
 * This extension is best used as static field in a test class with {@link RegisterExtension} annotation.
 * </p>
 */
public class ProvisionWorkspaceExtension implements Extension, BeforeAllCallback, AfterAllCallback {

    private static Logger LOG = LoggerFactory.getLogger(ProvisionWorkspaceExtension.class);

    private final String workspaceTestDataLocation;
    private final Class<?> testClassForObtainingBundle;

    private volatile Path workspaceRoot;

    /**
     * Create a new extension.
     *
     * @param workspaceTestDataLocation
     *            the location within an Eclipse test bundle to the Bazel workspace
     * @param testClassForObtainingBundle
     *            the class of the Eclipse test bundle to search for the {@code workspaceTestDataLocation}
     * @throws Exception
     *             in case of problems resolving the workspace URL
     */
    public ProvisionWorkspaceExtension(String workspaceTestDataLocation, Class<?> testClassForObtainingBundle) {
        // just store values here and initialize lated to avoid exceptions during class initializations
        this.workspaceTestDataLocation = workspaceTestDataLocation;
        this.testClassForObtainingBundle = testClassForObtainingBundle;
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        // cleanup
        var workspace = ResourcesPlugin.getWorkspace();
        workspace.run((ICoreRunnable) monitor -> {
            var projects = workspace.getRoot().getProjects();
            for (IProject project : projects) {
                var isWithinWorkspace = workspace.getRoot().getLocation().isPrefixOf(project.getLocation());
                project.delete(isWithinWorkspace, true, monitor);
            }
            workspace.save(true, monitor);
        }, new NullProgressMonitor());
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        var testFragmentBundle = FrameworkUtil.getBundle(testClassForObtainingBundle);
        assertNotNull(testFragmentBundle, "This test can only run inside an OSGi runtime.");

        var workspaceRootUrl = FileLocator.find(testFragmentBundle, new Path(workspaceTestDataLocation));
        assertNotNull(workspaceRootUrl, () -> format("Workspace root not found in bundle '%s'!", testFragmentBundle));
        try {
            workspaceRoot = new Path(new File(FileLocator.toFileURL(workspaceRootUrl).toURI()).getAbsolutePath());
        } catch (Exception e) {
            throw new AssertionError(
                    format("Error obtaining file path to test workspace '%s' using uri '%s' in bundle '%s'",
                        workspaceTestDataLocation, workspaceRootUrl, testFragmentBundle),
                    e);
        }

        // import the workspace project
        var bazelWorkspace = BazelCore.getModel().getBazelWorkspace(workspaceRoot);
        assertTrue(bazelWorkspace.exists(), () -> format("Bazel workspace '%s' does not exists!", workspaceRoot));

        LOG.info("Importing workspace '{}'", workspaceRoot);
        var workspaceJob =
                new ImportBazelWorkspaceJob(bazelWorkspace, workspaceRoot.append(BazelProject.DEFAULT_PROJECT_VIEW));
        workspaceJob.schedule();
        workspaceJob.join();

        var result = workspaceJob.getResult();
        if (!result.isOK()) {
            throw new AssertionError(result.getMessage(), result.getException());
        }
    }

    public Path getWorkspaceRoot() {
        var workspaceRoot = this.workspaceRoot;
        assertNotNull(workspaceRoot, "Bazel test workspace was not properly initialized!");
        return workspaceRoot;
    }

}
