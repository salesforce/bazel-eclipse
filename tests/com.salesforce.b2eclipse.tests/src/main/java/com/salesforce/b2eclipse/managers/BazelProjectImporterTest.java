/**
 * Copyright (c) 2020, Salesforce.com, Inc. All rights reserved.
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
 *
 */

package com.salesforce.b2eclipse.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("restriction")
public class BazelProjectImporterTest {

    private static final String IMPORT_BAZEL_ENABLED = "java.import.bazel.enabled";

    private static final String BAZEL_SRC_PATH_KEY = "java.import.bazel.src.path";

    private static final String BAZEL_SRC_PATH_VALUE = "/java/src";

    private BazelProjectImporter importer;

    private B2EPreferncesManager preferencesManager;

    private final Map<String, Object> settings = new HashMap<>();

    @Before
    public void setup() {
        importer = new BazelProjectImporter();

        preferencesManager = B2EPreferncesManager.getInstance();

        settings.put(IMPORT_BAZEL_ENABLED, true);
        settings.put(BAZEL_SRC_PATH_KEY, BAZEL_SRC_PATH_VALUE);
        preferencesManager.setConfiguration(settings);
    }

    @After
    public void deleteImportedProjects() throws CoreException {
        for (IProject project : getWorkspaceRoot().getProjects()) {
            project.delete(true, null);
        }
    }

    @Test
    public void basic() throws CoreException {
        importer.initialize(new File("projects/bazel-ls-demo-project"));
        importer.importToWorkspace(new NullProgressMonitor());

        IProject module1Proj = getWorkspaceRoot().getProject("module1");
        IProject module2Proj = getWorkspaceRoot().getProject("module2");
        IProject module3Proj = getWorkspaceRoot().getProject("module3");

        IProject[] referencedProjects = module1Proj.getReferencedProjects();

        assertEquals(2, referencedProjects.length);

        assertTrue("Didn't find module2 in the referenced projects list",
            Arrays.stream(referencedProjects).anyMatch(proj -> proj.equals(module2Proj)));

        assertTrue("Didn't find module3 in the referenced projects list",
            Arrays.stream(referencedProjects).anyMatch(proj -> proj.equals(module3Proj)));

    }

    @Test
    public void withSubpackage() throws CoreException {
        importer.initialize(new File("projects/build-with-subpackage"));
        importer.importToWorkspace(new NullProgressMonitor());

        IProject moduleProj = getWorkspaceRoot().getProject("module");
        IProject subModuleProj = getWorkspaceRoot().getProject("submodule");
        IProject[] referencedProjects = moduleProj.getReferencedProjects();

        assertEquals(1, referencedProjects.length);

        assertTrue("Couldn't find submodule in the referenced projects list",
            Arrays.stream(referencedProjects).anyMatch(proj -> proj.equals(subModuleProj)));
    }

    @Test
    public void withQueryInTargetFile() throws CoreException, IOException {
        File projectFile = new File("projects/bazel-ls-demo-project");
        File targetFile = new File(projectFile, BazelBuildSupport.BAZELPROJECT_FILE_NAME_SUFIX);

        FileUtils.writeLines(targetFile, Arrays.asList("directories:", "  module1", "  module2"));

        importer.initialize(projectFile);
        importer.importToWorkspace(new NullProgressMonitor());

        FileUtils.forceDelete(targetFile);

        IProject module1Proj = getWorkspaceRoot().getProject("module1");
        IProject module2Proj = getWorkspaceRoot().getProject("module2");
        IProject module3Proj = getWorkspaceRoot().getProject("module3");

        IProject[] referencedProjects = module1Proj.getReferencedProjects();

        assertEquals(1, referencedProjects.length);

        assertTrue("Didn't find module2 in the referenced projects list",
            Arrays.stream(referencedProjects).anyMatch(proj -> proj.equals(module2Proj)));

        assertFalse("module3 should be excluded from import by .bazeltargets file", module3Proj.exists());
    }

    private IWorkspaceRoot getWorkspaceRoot() {
        return ResourcesPlugin.getWorkspace().getRoot();
    }
}
