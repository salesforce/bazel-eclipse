package com.salesforce.bazel.eclipse.core.tests.it;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BAZEL_NATURE_ID;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstallType;
import org.eclipse.jdt.launching.JavaRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.tests.utils.LoggingProgressProviderExtension;
import com.salesforce.bazel.eclipse.core.tests.utils.ProvisionWorkspaceExtension;

/**
 * Integration tests using the 001 test workspace.
 * <p>
 * This test imports a Bazel workspace into Eclipse and then performs a bunch of tests with it. It cannot be execute as
 * a plain JUnit test but needs to run in an Eclipse environment.
 * </p>
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
@ExtendWith(LoggingProgressProviderExtension.class)
public class Workspace001Test_Provisioning {

    @RegisterExtension
    static ProvisionWorkspaceExtension provisionedWorkspace =
            new ProvisionWorkspaceExtension("testdata/workspaces/001", Workspace001Test_Provisioning.class);

    private void assertProjectWithProperNatures(IProject project) throws CoreException {
        assertTrue(project.exists(), format("Project '%s' expected to exist!", project.getName()));
        assertTrue(project.hasNature(JavaCore.NATURE_ID),
            format("Project '%s' expected to hava Java nature!", project.getName()));
        assertTrue(project.hasNature(BAZEL_NATURE_ID),
            format("Project '%s' expected to hava Bazel nature!", project.getName()));
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
    void test0001_check_basic_assumptions_in_the_model() throws CoreException {
        var root = ResourcesPlugin.getWorkspace().getRoot();

        var workspaceProject = root.getProject("testdata_workspaces_001");
        assertProjectWithProperNatures(workspaceProject);

        var bazelWorkspaceProject = BazelCore.create(workspaceProject);
        assertTrue(bazelWorkspaceProject.isWorkspaceProject(), "Expect to have the workspace project at this point!");
    }

    @Test
    void test0002_jdk_setup_for_workspace() throws CoreException {
        var bazelWorkspace = provisionedWorkspace.getBazelWorkspace();

        var vmInstallTypes = JavaRuntime.getVMInstallTypes();
        IVMInstall workspaceVm = null;
        for (IVMInstallType vmInstallType : vmInstallTypes) {
            var vmInstalls = vmInstallType.getVMInstalls();
            for (IVMInstall vm : vmInstalls) {
                if (vm.getName().contains(bazelWorkspace.getName())) {
                    assertNull(workspaceVm, "multiple VMs found for workspace; this is not ok");
                    workspaceVm = vm;
                }
            }
        }
        assertNotNull(workspaceVm, "no VMs found for workspace; this is not ok");
    }

}
