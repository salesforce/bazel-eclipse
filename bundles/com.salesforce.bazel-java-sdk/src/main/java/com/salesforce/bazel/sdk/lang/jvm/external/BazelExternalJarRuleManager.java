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
package com.salesforce.bazel.sdk.lang.jvm.external;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

/**
 * Bazel has a collection of tech stacks for downloading jars. For use cases where we need to know what the workspace is
 * using, this manager is used to indicate what tech stack(s) are in operation.
 */
public class BazelExternalJarRuleManager {

    /**
     * These started as an Enum, but new ways of downloading jars may become available, so String is a more extensible
     * option.
     */
    public static final String MAVEN_INSTALL = "maven_install"; // rules_jvm_external
    public static final String JVM_MAVEN_IMPORT = "jvm_maven_import_external"; // bazeltools
    public static final String MAVEN_JAR = "maven_jar"; // obsolete as of Bazel 2

    private Map<String, BazelExternalJarRuleType> availableTypes;

    public BazelExternalJarRuleManager(OperatingEnvironmentDetectionStrategy os) {
        availableTypes = new HashMap<>();
        availableTypes.put(MAVEN_INSTALL, new MavenInstallExternalJarRuleType(os));
        availableTypes.put(JVM_MAVEN_IMPORT, new BazelExternalJarRuleType(JVM_MAVEN_IMPORT, os)); // TODO create specialized subclass for jvm_import
        availableTypes.put(MAVEN_JAR, new BazelExternalJarRuleType(MAVEN_JAR, os)); // TODO create specialized subclass for maven_jar
    }

    /**
     * Enumerates the known jar downloading technologies.
     *
     * @return
     */
    public Map<String, BazelExternalJarRuleType> getAvailableTypes() {
        return availableTypes;
    }

    /**
     * This is rare to use. If your tooling environment knows of an external jar downloading technology that is not
     * known to the SDK, you can use this method as an extension point. Or you could use it to remove an existing type
     * that you don't want to support.
     */
    public void setAvailableTypes(Map<String, BazelExternalJarRuleType> availableTypes) {
        this.availableTypes = availableTypes;
    }

    /**
     * Is the passed rule name a known jar downloading tech?
     */
    public boolean isKnownType(String ruleName) {
        return getType(ruleName) != null;
    }

    /**
     * Gets the jar downloading type by rule name.
     */
    public BazelExternalJarRuleType getType(String ruleName) {
        return availableTypes.get(ruleName);
    }

    /**
     * Determine what jar downloader rule types are in use for a given workspace.
     */
    public List<BazelExternalJarRuleType> findInUseExternalJarRuleTypes(BazelWorkspace bazelWorkspace) {
        List<BazelExternalJarRuleType> inUseRuleTypes = new ArrayList<>();

        for (BazelExternalJarRuleType type : availableTypes.values()) {
            if (type.isUsedInWorkspace(bazelWorkspace)) {
                inUseRuleTypes.add(type);
            }
        }

        return inUseRuleTypes;
    }

    /**
     * Find the rule type that downloaded the passed file
     */
    public BazelExternalJarRuleType findOwningRuleType(BazelWorkspace bazelWorkspace, String absoluteFilepath) {
        for (BazelExternalJarRuleType type : availableTypes.values()) {
            if (type.doesBelongToRuleType(bazelWorkspace, absoluteFilepath)) {
                return type;
            }
        }
        return null;
    }
}
