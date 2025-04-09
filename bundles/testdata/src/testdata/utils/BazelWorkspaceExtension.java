package testdata.utils;

import static java.lang.String.format;
import static java.nio.file.Files.writeString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.eclipse.core.runtime.FileLocator;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.osgi.framework.FrameworkUtil;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.BazelCorePlugin;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.sdk.BazelVersion;

/**
 * JUnit extension for providing access to a Bazel workspace.
 * <p>
 * This extension does not import or synchronize any projects into Eclipse. It only ensures a workspace from test data
 * location from the classpath is extracted during test execution and available for the tests to use.
 * </p>
 * <p>
 * This extension is best used as static field in a test class with {@link RegisterExtension} annotation.
 * </p>
 */
public class BazelWorkspaceExtension implements BeforeAllCallback, AfterAllCallback {

    private final String workspaceTestDataLocation;
    private final Class<?> testClassForObtainingBundle;
    private final BazelVersion bazelVersion;

    private Path workspaceRoot;
    private BazelWorkspace bazelWorkspace;

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
    public BazelWorkspaceExtension(String workspaceTestDataLocation, Class<?> testClassForObtainingBundle) {
        this(workspaceTestDataLocation, testClassForObtainingBundle, null);
    }

    /**
     * Create a new extension.
     *
     * @param workspaceTestDataLocation
     *            the location within an Eclipse test bundle to the Bazel workspace
     * @param testClassForObtainingBundle
     *            the class of the Eclipse test bundle to search for the {@code workspaceTestDataLocation}
     * @param bazelVersion
     *            an optional Bazel version for generating a <code>.bazelversion</code> file. (maybe null)
     * @throws Exception
     *             in case of problems resolving the workspace URL
     */
    public BazelWorkspaceExtension(String workspaceTestDataLocation, Class<?> testClassForObtainingBundle,
            BazelVersion bazelVersion) {
        // just store values here and initialize lated to avoid exceptions during class
        // initializations
        this.workspaceTestDataLocation = workspaceTestDataLocation;
        this.testClassForObtainingBundle = testClassForObtainingBundle;
        this.bazelVersion = bazelVersion;
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        BazelCorePlugin.getInstance().getBazelModelManager().getModelInfoCache().invalidateAll();

        if ((bazelVersion != null) && (workspaceRoot != null)) {
            Files.delete(workspaceRoot.resolve(".bazelversion"));
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        var testFragmentBundle = FrameworkUtil.getBundle(testClassForObtainingBundle);
        assertNotNull(testFragmentBundle, "This test can only run inside an OSGi runtime.");

        var workspaceRootUrl =
                FileLocator.find(testFragmentBundle, new org.eclipse.core.runtime.Path(workspaceTestDataLocation));
        assertNotNull(workspaceRootUrl, () -> format("Workspace root not found in bundle '%s'!", testFragmentBundle));
        try {
            workspaceRoot = Path.of(FileLocator.toFileURL(workspaceRootUrl).toURI());
        } catch (Exception e) {
            throw new AssertionError(
                    format("Error obtaining file path to test workspace '%s' using uri '%s' in bundle '%s'",
                        workspaceTestDataLocation, workspaceRootUrl, testFragmentBundle),
                    e);
        }

        if (bazelVersion != null) {
            writeString(workspaceRoot.resolve(".bazelversion"), bazelVersion.toString(), StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        }

        bazelWorkspace =
                BazelCore.getModel().getBazelWorkspace(new org.eclipse.core.runtime.Path(workspaceRoot.toString()));
        assertTrue(bazelWorkspace.exists(), () -> format("Bazel workspace '%s' does not exists!", workspaceRoot));
        if (bazelVersion != null) {
            assertEquals(bazelVersion, bazelWorkspace.getBazelVersion(),
                "Bazel version is expected to match! Please check your test setup to provide the proper Bazel binary for changing Bazel versions using .bazelversion file. Use either Bazelisk or Bazel shell wrapper script.");
        }
    }

    public BazelWorkspace getBazelWorkspace() {
        var bazelWorkspace = this.bazelWorkspace;
        assertTrue(bazelWorkspace.exists(), () -> format("Bazel workspace '%s' does not exists!", getWorkspaceRoot()));
        return bazelWorkspace;
    }

    public Path getWorkspaceRoot() {
        var workspaceRoot = this.workspaceRoot;
        assertNotNull(workspaceRoot, "Bazel test workspace was not properly initialized!");
        return workspaceRoot;
    }
}
