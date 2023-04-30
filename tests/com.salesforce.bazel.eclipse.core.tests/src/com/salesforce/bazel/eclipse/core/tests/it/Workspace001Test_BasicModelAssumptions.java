package com.salesforce.bazel.eclipse.core.tests.it;

import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.sdk.BazelVersion;
import com.salesforce.bazel.sdk.model.BazelLabel;

import testdata.SharedTestData;
import testdata.utils.BazelWorkspaceExtension;
import testdata.utils.LoggingProgressProviderExtension;

/**
 * Integration tests using the 001 test workspace.
 * <p>
 * This does not import the workspace
 * </p>
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
@ExtendWith(LoggingProgressProviderExtension.class)
public class Workspace001Test_BasicModelAssumptions {

    private static final BazelVersion BAZEL_VERSION = new BazelVersion(6, 1, 1);

    @RegisterExtension
    static BazelWorkspaceExtension bazelWorkspaceExtension =
            new BazelWorkspaceExtension(SharedTestData.WORKSPACE_001, SharedTestData.class, BAZEL_VERSION);

    private BazelPackage assertPackage(BazelWorkspace bazelWorkspace, String packageLabel) {
        var bazelPackage = bazelWorkspace.getBazelPackage(new BazelLabel(packageLabel));
        assertTrue(bazelPackage.exists(), () -> format("Bazel package '%s' does not exists!", bazelPackage));
        return bazelPackage;
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
    void test0001_check_workspace() throws Exception {
        var bazelWorkspace = bazelWorkspaceExtension.getBazelWorkspace();
        assertNotNull(bazelWorkspace); // remaining asserts already done in getBazelWorkspace
    }

    @Test
    void test0010_module1() throws Exception {
        var bazelWorkspace = bazelWorkspaceExtension.getBazelWorkspace();

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
}
