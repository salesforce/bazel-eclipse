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
package com.salesforce.bazel.sdk.command.internal;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.salesforce.bazel.sdk.aspect.AspectPackageInfo;
import com.salesforce.bazel.sdk.command.test.MockWorkProgressMonitor;
import com.salesforce.bazel.sdk.command.test.TestBazelCommandEnvironmentFactory;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceDescriptor;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;

/**
 * Tests various behaviors of the BazelWorkspaceAspectHelper collaborator.
 * <p>
 * These tests use a test harness to generate real json files on the file system, which are then read in by the helper.
 * See TestBazelCommandEnvironmentFactory for how that is done.
 */
public class BazelWorkspaceAspectHelperTest {
    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Test
    public void testAspectLoading() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv();
        BazelWorkspaceAspectHelper aspectHelper = env.bazelWorkspaceCommandRunner.getBazelWorkspaceAspectHelper();

        // retrieve the aspects for the target
        List<String> targets = new ArrayList<>();
        targets.add("//projects/libs/javalib0:*");
        Map<String, Set<AspectPackageInfo>> aspectMap =
                aspectHelper.getAspectPackageInfos(targets, new MockWorkProgressMonitor(), "testAspectLoading");
        // aspect infos returned for: guava, slf4j, javalib0, javalib0-test
        assertEquals(4, aspectMap.get("//projects/libs/javalib0:*").size());

        // now check that the caches are populated
        assertEquals(1, aspectHelper.aspectInfoCache_current.size());
        assertEquals(1, aspectHelper.aspectInfoCache_lastgood.size());
    }

    @Test
    @Ignore // limitation in test framework makes this test not valid
    public void testAspectLoadingSpecificTarget() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv();
        BazelWorkspaceAspectHelper aspectHelper = env.bazelWorkspaceCommandRunner.getBazelWorkspaceAspectHelper();

        // retrieve the aspects for the target
        List<String> targets = new ArrayList<>();
        targets.add("//projects/libs/javalib0:javalib0");
        Map<String, Set<AspectPackageInfo>> aspectMap =
                aspectHelper.getAspectPackageInfos(targets, new MockWorkProgressMonitor(), "testAspectLoading");
        // aspect infos returned for: guava, slf4j, javalib0, javalib0-test
        assertEquals(4, aspectMap.get("//projects/libs/javalib0:javalib0").size()); // TODO this should be only 3 entries, javalib0-test should not be loaded

        // now check that the caches are populated
        assertEquals(1, aspectHelper.aspectInfoCache_current.size());
        assertEquals(1, aspectHelper.aspectInfoCache_lastgood.size());
    }

    @Test
    public void testAspectLoadingAndCaching() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv();
        BazelWorkspaceAspectHelper aspectHelper = env.bazelWorkspaceCommandRunner.getBazelWorkspaceAspectHelper();

        // retrieve the aspects for the target
        List<String> targets = new ArrayList<>();
        targets.add("//projects/libs/javalib0:*");
        Map<String, Set<AspectPackageInfo>> aspectMap =
                aspectHelper.getAspectPackageInfos(targets, new MockWorkProgressMonitor(), "testAspectLoading");
        // aspect infos returned for: guava, slf4j, javalib0, javalib0-test
        assertEquals(1, aspectMap.size());
        assertEquals(0, aspectHelper.numberCacheHits);

        // ask for the same target again
        aspectMap = aspectHelper.getAspectPackageInfos(targets, new MockWorkProgressMonitor(), "testAspectLoading");
        // aspect infos returned for: guava, slf4j, javalib0, javalib0-test
        assertEquals(1, aspectMap.size());
        assertEquals(1, aspectHelper.numberCacheHits); // the entries all came from cache
    }

    @Test
    public void testAspectCacheFlush() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv();
        BazelWorkspaceAspectHelper aspectHelper = env.bazelWorkspaceCommandRunner.getBazelWorkspaceAspectHelper();

        // retrieve the aspects for the target
        List<String> targets = new ArrayList<>();
        targets.add("//projects/libs/javalib0:*");
        Map<String, Set<AspectPackageInfo>> aspectMap =
                aspectHelper.getAspectPackageInfos(targets, new MockWorkProgressMonitor(), "testAspectLoading");
        // aspect infos returned for: guava, slf4j, javalib0, javalib0-test
        assertEquals(1, aspectMap.size());
        assertEquals(0, aspectHelper.numberCacheHits);

        // ask for the same target again
        aspectMap = aspectHelper.getAspectPackageInfos(targets, new MockWorkProgressMonitor(), "testAspectLoading");
        // aspect infos returned for: guava, slf4j, javalib0, javalib0-test
        assertEquals(1, aspectMap.size());
        assertEquals(1, aspectHelper.numberCacheHits); // the entries all came from cache

        // flush the cache (we do this when the user executes a 'clean' in Eclipse
        aspectHelper.flushAspectInfoCache();
        assertEquals(0, aspectHelper.aspectInfoCache_current.size());
        assertEquals(0, aspectHelper.aspectInfoCache_wildcards.size());
        assertEquals(1, aspectHelper.aspectInfoCache_lastgood.size()); // last good is an emergency fallback, not flushed

        // ask for the same target again
        aspectMap = aspectHelper.getAspectPackageInfos(targets, new MockWorkProgressMonitor(), "testAspectLoading");
        // aspect infos returned for: guava, slf4j, javalib0, javalib0-test
        assertEquals(1, aspectMap.size());
        assertEquals(1, aspectHelper.numberCacheHits); // the entries all came from cache
    }

    // INTERNAL

    private TestBazelCommandEnvironmentFactory createEnv() throws Exception {
        File testDir = tmpFolder.newFolder();
        File workspaceDir = new File(testDir, "bazel-workspace");
        workspaceDir.mkdirs();
        File outputbaseDir = new File(testDir, "outputbase");
        outputbaseDir.mkdirs();

        TestBazelWorkspaceDescriptor descriptor =
                new TestBazelWorkspaceDescriptor(workspaceDir, outputbaseDir).javaPackages(1);
        TestBazelWorkspaceFactory workspace = new TestBazelWorkspaceFactory(descriptor).build();
        TestBazelCommandEnvironmentFactory env = new TestBazelCommandEnvironmentFactory();
        env.createTestEnvironment(workspace, testDir, null);

        return env;
    }

}
