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
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.command.test.TestBazelCommandEnvironmentFactory;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceDescriptor;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

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
    @Ignore // TODO broken on Windows due to some state leak between this test and another test
    public void testAspectLoading() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv("loading");
        BazelWorkspaceAspectProcessor aspectHelper = env.bazelWorkspaceCommandRunner.getBazelWorkspaceAspectHelper();
        BazelLabel label = new BazelLabel("//projects/libs/javalib0:*"); // $SLASH_OK bazel path

        // retrieve the aspects for the target
        List<BazelLabel> targets = Collections.singletonList(label);
        Map<BazelLabel, Set<AspectTargetInfo>> aspectMap =
                aspectHelper.getAspectTargetInfos(targets, "testAspectLoading");
        // aspect infos returned for: guava, slf4j, hamcrest, junit, javalib0, javalib0-test
        assertNotNull(aspectMap);
        assertEquals(1, aspectMap.size());
        assertNotNull(aspectMap.get(label));
        Set<AspectTargetInfo> javalib0Aspects = aspectMap.get(label);
        printAspectInfos(javalib0Aspects, "testAspectLoading");
        assertEquals(6, javalib0Aspects.size());

        // now check that the caches are populated
        // javalib0:javalib0, javalib0:javalib0-test, javalib0:*
        assertEquals(3, aspectHelper.aspectInfoCache_current.size());
        assertEquals(3, aspectHelper.aspectInfoCache_lastgood.size());
    }

    @Test
    @Ignore // limitation in test framework makes this test not valid
    public void testAspectLoadingSpecificTarget() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv("specific");
        BazelWorkspaceAspectProcessor aspectHelper = env.bazelWorkspaceCommandRunner.getBazelWorkspaceAspectHelper();
        BazelLabel label = new BazelLabel("//projects/libs/javalib0:javalib0"); // $SLASH_OK bazel path

        // retrieve the aspects for the target
        List<BazelLabel> targets = Collections.singletonList(label);
        Map<BazelLabel, Set<AspectTargetInfo>> aspectMap =
                aspectHelper.getAspectTargetInfos(targets, "testAspectLoading");
        Set<AspectTargetInfo> javalib0Aspects = aspectMap.get(label);
        printAspectInfos(javalib0Aspects, "testAspectLoadingSpecificTarget");

        // aspect infos returned for: guava, slf4j, javalib0, javalib0-test
        assertEquals(4, javalib0Aspects.size()); // TODO this should be only 3 entries, javalib0-test should not be loaded

        // now check that the caches are populated
        assertEquals(1, aspectHelper.aspectInfoCache_current.size());
        assertEquals(1, aspectHelper.aspectInfoCache_lastgood.size());
    }

    @Test
    public void testAspectLoadingAndCaching() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv("caching");
        BazelWorkspaceAspectProcessor aspectHelper = env.bazelWorkspaceCommandRunner.getBazelWorkspaceAspectHelper();
        BazelLabel label = new BazelLabel("//projects/libs/javalib0:*"); // $SLASH_OK bazel path

        // retrieve the aspects for the target
        List<BazelLabel> targets = Collections.singletonList(label);
        Map<BazelLabel, Set<AspectTargetInfo>> aspectMap =
                aspectHelper.getAspectTargetInfos(targets, "testAspectLoading");
        // aspect infos returned for: guava, slf4j, javalib0, javalib0-test
        assertEquals(1, aspectMap.size());
        assertEquals(0, aspectHelper.numberCacheHits);

        // ask for the same target again
        aspectMap = aspectHelper.getAspectTargetInfos(targets, "testAspectLoading");
        // aspect infos returned for: guava, slf4j, javalib0, javalib0-test
        assertEquals(1, aspectMap.size());
        assertEquals(1, aspectHelper.numberCacheHits); // the entries all came from cache
    }

    @Test
    public void testAspectCacheFlush() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv("flush");
        BazelWorkspaceAspectProcessor aspectHelper = env.bazelWorkspaceCommandRunner.getBazelWorkspaceAspectHelper();
        BazelLabel label = new BazelLabel("//projects/libs/javalib0:*"); // $SLASH_OK bazel path

        // retrieve the aspects for the target
        List<BazelLabel> targets = Collections.singletonList(label);
        Map<BazelLabel, Set<AspectTargetInfo>> aspectMap =
                aspectHelper.getAspectTargetInfos(targets, "testAspectLoading");
        assertEquals(1, aspectMap.size());
        assertEquals(label, aspectMap.keySet().iterator().next());
        assertEquals(0, aspectHelper.numberCacheHits);

        // ask for the same target again
        aspectMap = aspectHelper.getAspectTargetInfos(targets, "testAspectLoading");
        assertEquals(1, aspectMap.size());
        assertEquals(label, aspectMap.keySet().iterator().next());
        assertEquals(1, aspectHelper.numberCacheHits); // the entries all came from cache

        // flush the cache (we do this when the user executes a 'clean' in Eclipse)
        aspectHelper.flushAspectInfoCache();
        assertEquals(0, aspectHelper.aspectInfoCache_current.size());
        // javalib0:javalib0, javalib0:javalib0-test, javalib0:*
        assertEquals(3, aspectHelper.aspectInfoCache_lastgood.size()); // last good is an emergency fallback, not flushed

        // ask for the same target again
        aspectMap = aspectHelper.getAspectTargetInfos(targets, "testAspectLoading");
        assertEquals(1, aspectMap.size());
        assertEquals(label, aspectMap.keySet().iterator().next());
        assertEquals(1, aspectHelper.numberCacheHits); // the entries all came from cache
    }

    // INTERNAL

    private TestBazelCommandEnvironmentFactory createEnv(String testKey) throws Exception {
        File testDir = tmpFolder.newFolder();
        File workspaceDir = new File(testDir, "bazelws-" + testKey);
        workspaceDir.mkdirs();
        File outputbaseDir = new File(testDir, "obase-" + testKey);
        outputbaseDir.mkdirs();

        TestOptions testOptions = new TestOptions().numberOfJavaPackages(1);

        TestBazelWorkspaceDescriptor descriptor =
                new TestBazelWorkspaceDescriptor(workspaceDir, outputbaseDir).testOptions(testOptions);
        TestBazelWorkspaceFactory workspace = new TestBazelWorkspaceFactory(descriptor);
        TestBazelCommandEnvironmentFactory env = new TestBazelCommandEnvironmentFactory();

        workspace.build();
        env.createTestEnvironment(workspace, testDir, testOptions);

        return env;
    }

    private void printAspectInfos(Set<AspectTargetInfo> aspects, String testName) {
        int index = 0;
        System.out.println("Aspect list for test " + testName);
        for (AspectTargetInfo aspectInfo : aspects) {
            String aspectFilePath = "<unknown";
            if (aspectInfo.getAspectDataFile() != null) {
                aspectFilePath = aspectInfo.getAspectDataFile().getAbsolutePath();
            }
            System.out.println("  [A" + index++ + "] " + aspectInfo.getLabelPath() + " path: " + aspectFilePath);
        }
    }

}
