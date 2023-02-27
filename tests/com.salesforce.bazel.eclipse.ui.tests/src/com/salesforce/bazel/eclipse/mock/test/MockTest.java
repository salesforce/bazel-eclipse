/**
 * Copyright (c) 2019, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.bazel.eclipse.mock.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.core.BazelCorePluginSharedContstants;
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
        ComponentContext.getInstance().setResourceHelper(resourceHelper);

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
        assertNotNull(proj.getNature(BazelCorePluginSharedContstants.BAZEL_NATURE_ID));
        assertNotNull(proj.getNature("org.eclipse.jdt.core.javanature"));
    }
}
