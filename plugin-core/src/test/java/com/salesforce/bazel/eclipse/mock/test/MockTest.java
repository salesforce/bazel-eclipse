package com.salesforce.bazel.eclipse.mock.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import com.salesforce.bazel.eclipse.BazelNature;
import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.mock.MockIProjectFactory;
import com.salesforce.bazel.eclipse.mock.MockResourceHelper;

/**
 * Basic tests for our test framework. Kinda meta.
 */
public class MockTest {
    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Test
    public void testIProjectFactory() throws Exception {
        MockIProjectFactory mockIProjectFactory = new MockIProjectFactory();
        MockResourceHelper resourceHelper = new MockResourceHelper(tmpFolder.newFolder(), null);
        BazelPluginActivator.setResourceHelperForTests(resourceHelper);
        
        // create the bill of materials
        MockIProjectFactory.MockIProjectDescriptor desc = new MockIProjectFactory.MockIProjectDescriptor("test1");        
        desc.customNatures.put("greenNature", Mockito.mock(IProjectNature.class));
        desc.customNatures.put("redNature", Mockito.mock(IProjectNature.class));
        
        // create the mock project
        IProject proj = mockIProjectFactory.buildIProject(desc);
        
        // test it
        assertEquals(desc.name, proj.getName());        
        // check for the custom natures
        assertNotNull(proj.getNature("greenNature"));
        assertNotNull(proj.getNature("redNature"));
        // verify standard natures are added by default
        assertNotNull(proj.getNature(BazelNature.BAZEL_NATURE_ID));
        assertNotNull(proj.getNature("org.eclipse.jdt.core.javanature"));
    }
}
