package testdata.utils;

import static java.util.Objects.requireNonNull;
import static org.eclipse.core.runtime.IPath.fromPath;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.setup.ImportBazelWorkspaceJob;
import com.salesforce.bazel.sdk.BazelVersion;

/**
 * JUnit extension for provisioning a workspace into Eclipse.
 * <p>
 * This extension is best used as static field in a test class with {@link RegisterExtension} annotation.
 * </p>
 */
public class ProvisionWorkspaceExtension extends BazelWorkspaceExtension implements AfterAllCallback {

    private static Logger LOG = LoggerFactory.getLogger(ProvisionWorkspaceExtension.class);
    private final IPath pathToProjectViewFileInsideBazelWorkspace;

    /**
     * Create a new extension.
     *
     * @param workspaceTestDataLocation
     *            the location within an Eclipse test bundle to the Bazel workspace
     * @param testClassForObtainingBundle
     *            the class of the Eclipse test bundle to search for the {@code workspaceTestDataLocation}
     * @param pathToProjectViewFileInsideBazelWorkspace
     *            the path to the <code>.bazelproject</code> file to import/provision (relative inside the Bazel
     *            workspace)
     * @throws Exception
     *             in case of problems resolving the workspace URL
     */
    public ProvisionWorkspaceExtension(String workspaceTestDataLocation, Class<?> testClassForObtainingBundle,
            IPath pathToProjectViewFileInsideBazelWorkspace) {
        super(workspaceTestDataLocation, testClassForObtainingBundle);
        this.pathToProjectViewFileInsideBazelWorkspace = pathToProjectViewFileInsideBazelWorkspace;
    }

    /**
     * Create a new extension.
     *
     * @param workspaceTestDataLocation
     *            the location within an Eclipse test bundle to the Bazel workspace
     * @param testClassForObtainingBundle
     *            the class of the Eclipse test bundle to search for the {@code workspaceTestDataLocation}
     * @param pathToProjectViewFileInsideBazelWorkspace
     *            the path to the <code>.bazelproject</code> file to import/provision (relative inside the Bazel
     *            workspace)
     * @param bazelVersion
     *            an optional Bazel version for generating a <code>.bazelversion</code> file. (maybe null)
     * @throws Exception
     *             in case of problems resolving the workspace URL
     */
    public ProvisionWorkspaceExtension(String workspaceTestDataLocation, Class<?> testClassForObtainingBundle,
            IPath pathToProjectViewFileInsideBazelWorkspace, BazelVersion bazelVersion) {
        super(workspaceTestDataLocation, testClassForObtainingBundle, bazelVersion);
        this.pathToProjectViewFileInsideBazelWorkspace = pathToProjectViewFileInsideBazelWorkspace;
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        try {
            // cleanup
            var workspace = ResourcesPlugin.getWorkspace();
            workspace.run((ICoreRunnable) monitor -> {
                // delete all projects
                var projects = workspace.getRoot().getProjects();
                for (IProject project : projects) {
                    var isWithinWorkspace = workspace.getRoot().getLocation().isPrefixOf(project.getLocation());
                    project.delete(isWithinWorkspace, true, monitor);
                }
                workspace.save(true, monitor);
            }, new NullProgressMonitor());
        } finally {
            // ensure model cache is cleared
            super.afterAll(context);
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        super.beforeAll(context);
        LOG.info("Importing workspace '{}'", getWorkspaceRoot());

        var projectViewLocation =
                fromPath(getWorkspaceRoot()).append(requireNonNull(pathToProjectViewFileInsideBazelWorkspace,
                    "error in test: no .bazelproject file path specified for provision workspace extension"));

        var workspaceJob = new ImportBazelWorkspaceJob(getBazelWorkspace(), projectViewLocation);
        workspaceJob.schedule();
        workspaceJob.join();

        var result = workspaceJob.getResult();
        if (!result.isOK()) {
            throw new AssertionError(result.getMessage(), result.getException());
        }
    }
}
