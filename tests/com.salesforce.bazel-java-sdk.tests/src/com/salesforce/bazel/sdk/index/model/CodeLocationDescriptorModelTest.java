/**
 * Copyright (c) 2022, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.sdk.index.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CodeLocationDescriptorModelTest {

    private static final long ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L;

    @Test
    public void testJarAgeComputations() {
        CodeLocationDescriptor jarLocationDescriptor = new CodeLocationDescriptor();

        long currentTimeMillis = 1000000000000L; // fixed time such that tests are reproducible
        long earlistValidTimeMillis = 945000000000L;
        final int UNKNOWN_DAYS = -1;

        // verify default age is unknown (-1)
        assertEquals(UNKNOWN_DAYS, jarLocationDescriptor.ageInDays);

        // happy path: 0 days old (written 10 seconds ago)
        long writtenTimeMillis = currentTimeMillis - 10000L; // tens seconds before now
        boolean success =
                jarLocationDescriptor.computeAge(writtenTimeMillis, currentTimeMillis, earlistValidTimeMillis);
        assertTrue(success);
        assertEquals(0, jarLocationDescriptor.ageInDays);

        // happy path: 5 days old
        writtenTimeMillis = currentTimeMillis - (5 * ONE_DAY_MILLIS) - 10000L;
        success = jarLocationDescriptor.computeAge(writtenTimeMillis, currentTimeMillis, earlistValidTimeMillis);
        assertTrue(success);
        assertEquals(5, jarLocationDescriptor.ageInDays);

        // unhappy path: written time of entry is older than earliest valid time (earlistValidTimeMillis) which is
        // likely a fake date provided by a hermetic build system
        writtenTimeMillis = 0;
        success = jarLocationDescriptor.computeAge(writtenTimeMillis, currentTimeMillis, earlistValidTimeMillis);
        assertFalse(success);
        assertEquals(UNKNOWN_DAYS, jarLocationDescriptor.ageInDays);

        // unhappy path: written time of entry is bogus future date
        writtenTimeMillis = currentTimeMillis + (3 * ONE_DAY_MILLIS);
        success = jarLocationDescriptor.computeAge(writtenTimeMillis, currentTimeMillis, earlistValidTimeMillis);
        assertFalse(success);
        assertEquals(UNKNOWN_DAYS, jarLocationDescriptor.ageInDays);
    }

}
