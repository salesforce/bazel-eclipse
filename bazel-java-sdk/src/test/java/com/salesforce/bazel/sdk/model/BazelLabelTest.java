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
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.salesforce.bazel.sdk.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import com.salesforce.bazel.sdk.model.BazelLabel;

public class BazelLabelTest {

    @Test
    public void testIsPackageDefault() {
        assertTrue(new BazelLabel("foo").isPackageDefault());
        assertTrue(new BazelLabel("//foo/blah").isPackageDefault());
        assertFalse(new BazelLabel("blah:t").isPackageDefault());
        assertFalse(new BazelLabel("//...").isPackageDefault());
        assertFalse(new BazelLabel("//:*").isPackageDefault());
    }

    @Test
    public void testIsConcrete() {
        assertTrue(new BazelLabel("//foo/blah").isConcrete());
        assertTrue(new BazelLabel("//foo/blah:t1").isConcrete());
        assertFalse(new BazelLabel("blah/...").isConcrete());
        assertFalse(new BazelLabel("blah:*").isConcrete());
        assertFalse(new BazelLabel("//...").isConcrete());
        assertFalse(new BazelLabel("//:*").isConcrete());
    }

    @Test
    public void testGetPackagePath() {
        assertEquals("foo", new BazelLabel("foo").getPackagePath());
        assertEquals("foo/blah", new BazelLabel("//foo/blah").getPackagePath());
        assertEquals("foo/blah", new BazelLabel("foo/blah:t1").getPackagePath());
        assertEquals("blah", new BazelLabel("blah/...").getPackagePath());
        assertEquals("blah", new BazelLabel("blah:*").getPackagePath());
        assertEquals("", new BazelLabel("//...").getPackagePath());
        assertEquals("", new BazelLabel("//:*").getPackagePath());
    }

    @Test
    public void testGetDefaultPackageLabel() {
        assertEquals("//foo", new BazelLabel("foo").getDefaultPackageLabel().getLabel());
        assertEquals("//foo/blah", new BazelLabel("//foo/blah").getDefaultPackageLabel().getLabel());
        assertEquals("//foo/blah", new BazelLabel("foo/blah:t1").getDefaultPackageLabel().getLabel());
        assertEquals("//blah", new BazelLabel("blah/...").getDefaultPackageLabel().getLabel());
        assertEquals("//blah", new BazelLabel("blah:*").getDefaultPackageLabel().getLabel());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetDefaultPackageLabel_invalidRootLabel() {
        assertEquals("//", new BazelLabel("//...").getDefaultPackageLabel());
    }

    @Test
    public void testGetPackageName() {
        assertEquals("foo", new BazelLabel("foo").getPackageName());
        assertEquals("blah", new BazelLabel("//foo/blah").getPackageName());
        assertEquals("blah", new BazelLabel("foo/blah:t1").getPackageName());
        assertEquals("blah", new BazelLabel("blah/...").getPackageName());
        assertEquals("blah", new BazelLabel("blah:*").getPackageName());
        assertEquals("", new BazelLabel("//...").getPackageName());
        assertEquals("", new BazelLabel("//:*").getPackageName());
    }

    @Test
    public void testGetTargetName() {
        assertEquals("foo", new BazelLabel("foo").getTargetName());
        assertEquals("blah", new BazelLabel("//foo/blah").getTargetName());
        assertEquals("t1", new BazelLabel("foo/blah:t1").getTargetName());
        assertEquals(null, new BazelLabel("blah/...").getTargetName());
        assertEquals(null, new BazelLabel("blah:*").getTargetName());
        assertEquals(null, new BazelLabel("//...").getTargetName());
        assertEquals(null, new BazelLabel("//:*").getTargetName());
    }

    @Test
    public void testGetLabel() {
        assertEquals("//foo", new BazelLabel("foo").getLabel());
        assertEquals("//foo/blah", new BazelLabel("//foo/blah").getLabel());
        assertEquals("//foo/blah:t1", new BazelLabel("foo/blah:t1").getLabel());
        assertEquals("//...", new BazelLabel("//...").getLabel());
        assertEquals("//:*", new BazelLabel("//:*").getLabel());
    }

    @Test
    public void testToPackageWildcardLabel() {
        assertEquals("//foo:*", new BazelLabel("foo").toPackageWildcardLabel().getLabel());
    }

    @Test
    public void testGetLastComponentOfTargetName() {
        assertEquals("test123", new BazelLabel("test123").getLastComponentOfTargetName());
        assertEquals("test123", new BazelLabel("foo:test123").getLastComponentOfTargetName());
        assertEquals("blah", new BazelLabel("foo:src/foo/blah").getLastComponentOfTargetName());
        assertEquals(null, new BazelLabel("//...").getLastComponentOfTargetName());
    }

    @Test
    public void testEquality() {
        Set<BazelLabel> s = new HashSet<>();
        s.add(new BazelLabel("//a/b/c"));
        s.add(new BazelLabel("//a/b/c"));

        assertEquals(1, s.size());
        assertTrue(s.contains(new BazelLabel("//a/b/c")));
    }

    @Test(expected = IllegalStateException.class)
    public void testToPackageWildcardLabel_invalidLabel() {
        assertEquals("//foo:*", new BazelLabel("foo:test").toPackageWildcardLabel().getLabel());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidLabel_null() {
        new BazelLabel(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidLabel_empty() {
        new BazelLabel("  ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidLabel_tailingColon() {
        new BazelLabel("//blah:");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidLabel_tailingSlash() {
        new BazelLabel("/");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidLabel_onlyLeadingSlashes() {
        new BazelLabel("//");
    }
}
