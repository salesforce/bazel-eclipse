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
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Abstraction over the internal details of a BUILD file.
 * Depending on how this object was built, it may not contain all of the information.
 */
public class BazelBuildFile {
    
    /**
     * The label that identifies the package associated with this BUILD file, e.g. //projects/libs/foo
     */
    String label;
    
    /**
     * Maps the String rule type (e.g. java_library) to the target labels (e.g. "//projects/libs/foo:foolib", 
     *   "//projects/libs/foo:barlib")
     */
    private Map<String, Set<String>> typeToTargetMap = new TreeMap<>();

    /**
     * Maps the String target label (e.g. //projects/libs/foo:foolib) to the rule type  (e.g. java_library)
     */
    private Map<String, String> targetToTypeMap = new TreeMap<>();

    private Set<String> allTargets = new TreeSet<>();
    
    
    public BazelBuildFile(String label) {
        this.label = label;
    }
    
    public void addTarget(String ruleType, String targetLabel) {
        this.targetToTypeMap.put(targetLabel, ruleType);
        this.allTargets.add(targetLabel);
        
        Set<String> targetsForRuleType = typeToTargetMap.get(ruleType);
        if (targetsForRuleType == null) {
            targetsForRuleType = new TreeSet<>();
            this.typeToTargetMap.put(ruleType, targetsForRuleType);
        }
        if (!targetsForRuleType.contains(targetLabel)) {
            targetsForRuleType.add(targetLabel);
        }
        
    }
    
    public String getLabel() {
        return this.label;
    }
    
    public Set<String> getRuleTypes() {
        return this.typeToTargetMap.keySet();
    }
    
    public Set<String> getTargetsForRuleType(String ruleType) {
        return this.typeToTargetMap.get(ruleType);
    }
    
    public String getRuleTypeForTarget(String targetLabel) {
        return this.targetToTypeMap.get(targetLabel);
    }
    
    public Set<String> getAllTargetLabels() {
        return this.allTargets;
    }
}