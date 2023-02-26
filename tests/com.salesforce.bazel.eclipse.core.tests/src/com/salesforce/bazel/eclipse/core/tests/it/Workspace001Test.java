package com.salesforce.bazel.eclipse.core.tests.it;

import static java.lang.String.format;
import static org.junit.Assume.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.setup.ImportBazelWorkspaceJob;

/**
 * Integration tests using the 001 test workspace.
 * <p>
 * This test imports a Bazel workspace into Eclipse and then performs a bunch of tests with it. It cannot be execute as
 * a plain JUnit test but needs to run in an Eclipse environment.
 * </p>
 */
public class Workspace001Test {

    private static Logger LOG = LoggerFactory.getLogger(Workspace001Test.class);

    private static IPath workspaceRoot;

    /**
     * @throws java.lang.Exception
     */
    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        var testFragmentBundle = FrameworkUtil.getBundle(Workspace001Test.class);
        assumeTrue("This test can only run inside an OSGi runtime.", testFragmentBundle != null);

        var workspaceRootUrl = FileLocator.find(testFragmentBundle, new Path("testdata/workspaces/001"));
        assertNotNull(workspaceRootUrl, () -> format("Workspace root not found in bundle '%s'!", testFragmentBundle));
        workspaceRoot = new Path(new File(FileLocator.toFileURL(workspaceRootUrl).toURI()).getAbsolutePath());

        // import the workspace project
        var bazelWorkspace = BazelCore.getModel().getBazelWorkspace(workspaceRoot);
        assertTrue(bazelWorkspace.exists(), () -> format("Bazel workspace '%s' does not exists!", workspaceRoot));

        LOG.info("Importating workspace '{}'", workspaceRoot);
        var workspaceJob =
                new ImportBazelWorkspaceJob(bazelWorkspace, workspaceRoot.append(BazelProject.DEFAULT_PROJECT_VIEW));
        workspaceJob.schedule();
        workspaceJob.join();

        var result = workspaceJob.getResult();
        if (!result.isOK()) {
            throw new AssertionError(result.getMessage(), result.getException());
        }
    }

    /**
     * @throws java.lang.Exception
     */
    @AfterAll
    static void tearDownAfterClass() throws Exception {}

    /**
     * @throws java.lang.Exception
     */
    @BeforeEach
    void setUp() throws Exception {}

    /**
     * @throws java.lang.Exception
     */
    @AfterEach
    void tearDown() throws Exception {}

    @Test
    void test() {
        fail("Not yet implemented");
    }

}
