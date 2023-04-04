package com.salesforce.bazel.eclipse.core.tests.it;

import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.FrameworkUtil;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.tests.utils.LoggingProgressProviderExtension;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * Integration tests using the 001 test workspace.
 * <p>
 * This does not import the workspace
 * </p>
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
@ExtendWith(LoggingProgressProviderExtension.class)
public class Workspace001Test_BasicModelAssumptions {

    private static IPath workspaceRoot;

    /**
     * @throws java.lang.Exception
     */
    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        var testFragmentBundle = FrameworkUtil.getBundle(Workspace001Test_BasicModelAssumptions.class);
        assumeTrue(testFragmentBundle != null, "This test can only run inside an OSGi runtime.");

        var workspaceRootUrl = FileLocator.find(testFragmentBundle, new Path("testdata/workspaces/001"));
        assertNotNull(workspaceRootUrl, () -> format("Workspace root not found in bundle '%s'!", testFragmentBundle));
        workspaceRoot = new Path(new File(FileLocator.toFileURL(workspaceRootUrl).toURI()).getAbsolutePath());
    }

    /**
     * @throws java.lang.Exception
     */
    @AfterAll
    static void tearDownAfterClass() throws Exception {
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
    void test0001_check_workspace() {
        assertWorkspace();
    }

    @Test
    void test0010_module1() throws Exception {
        var bazelWorkspace = assertWorkspace();

        var bazelPackage = assertPackage(bazelWorkspace, "//module1");

        var targets = bazelPackage.getBazelTargets();
        assertThat("Should have four targets!", targets, hasSize(4));

        var module1 = bazelPackage.getBazelTarget("module1");
        assertTrue(module1.exists());

        assertEquals("module1", module1.getTargetName());
        assertEquals("java_binary", module1.getRuleClass());
        assertEquals("//module1:module1", module1.getLabel().toString());

        var ruleAttributes = module1.getRuleAttributes();
        assertNotNull(ruleAttributes);

        assertThat(ruleAttributes.getStringList("deps"), hasItems("//module2:module2", "//module3:module3",
            "@com_google_guava//jar:jar", "//module1:mybuilder_sources"));
    }

    private BazelPackage assertPackage(BazelWorkspace bazelWorkspace, String packageLabel) {
        var bazelPackage = bazelWorkspace.getBazelPackage(new BazelLabel(packageLabel));
        assertTrue(bazelPackage.exists(), () -> format("Bazel package '%s' does not exists!", bazelPackage));
        return bazelPackage;
    }

    private BazelWorkspace assertWorkspace() {
        var bazelWorkspace = BazelCore.getModel().getBazelWorkspace(workspaceRoot);
        assertTrue(bazelWorkspace.exists(), () -> format("Bazel workspace '%s' does not exists!", workspaceRoot));
        return bazelWorkspace;
    }
}
