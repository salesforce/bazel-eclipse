/**
 * Copyright (c) 2019-2021, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.sdk.workspace;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.model.BazelTargetKind;

/**
 * Helper to parse BUILD files.
 */
public class BuildFileSupport {
    private static Logger LOG = LoggerFactory.getLogger(BuildFileSupport.class);

    public static boolean hasRegisteredRuleInLine(String buildFileLine) {
        buildFileLine = buildFileLine.trim();
        var registeredRuleNames = BazelTargetKind.getRegisteredTargetKindNames();
        if (registeredRuleNames.stream().parallel().anyMatch(buildFileLine::startsWith)) {
            return true;
        }
        return false;
    }

    /**
     * Parses a File, presumed to be a Bazel BUILD file, looking for indications that it contains rules kinds that are
     * registered with the SDK (and therefore interesting).
     *
     * @param buildFile
     * @return true if it contains at least one registered rule, false if not
     */
    public static boolean hasRegisteredRules(File buildFile) {
        var hasRegisteredRules = false;

        if (!buildFile.exists() || !buildFile.canRead()) {
            return false;
        }

        try (InputStream is = new FileInputStream(buildFile)) {
            hasRegisteredRules = hasRegisteredRules(is);
        } catch (Exception anyE) {
            LOG.error(anyE.getMessage(), anyE);
        }
        return hasRegisteredRules;
    }

    /**
     * Parses an InputStream, presumed to be the contents of a Bazel BUILD file, looking for indications that it
     * contains rules of registered kinds.
     *
     * @param is
     * @return true if it contains at least one rule of a registered kind, false if not
     */
    public static boolean hasRegisteredRules(InputStream is) {
        try (var br = new BufferedReader(new InputStreamReader(is))) {
            var buildFileLine = br.readLine();
            while (buildFileLine != null) {
                if (hasRegisteredRuleInLine(buildFileLine)) {
                    return true;
                }
                buildFileLine = br.readLine();
            }

        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
        return false;
    }
}
