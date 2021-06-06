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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A Bazel rule type (e.g. java_library) we support.
 * <p>
 * It is tempting to make this an Enum, but we want the set of supported kinds to be expandable.
 */
public class BazelTargetKind {

    protected final String targetKind;
    protected boolean isRunnable = false;
    protected boolean isTestable = false;

    static Map<String, BazelTargetKind> knownInstances = new HashMap<>();

    public BazelTargetKind(String targetKind, boolean isRunnable, boolean isTestable) {
        this.targetKind = targetKind;
        this.isRunnable = isRunnable;
        this.isTestable = isTestable;

        knownInstances.put(targetKind.toLowerCase(), this);
    }

    /**
     * Returns the corresponding TargetKind value based on the specified String value, ignoring the casing of the given
     * value. Returns null if no matching TargetKind value is found.
     *
     * @return matching TargetKind instance, null if no match
     */
    public static BazelTargetKind valueOfIgnoresCase(String value) {
        String valueLower = value.toLowerCase();
        BazelTargetKind found = knownInstances.get(valueLower);

        return found;
    }

    /**
     * Returns the corresponding TargetKind value based on the specified String value, ignoring the casing of the given
     * value. Throws an Exception if not matching TargetKind value is found.
     *
     * @return matching TargetKind instance
     * @throws IllegalStateException
     *             if no matching TargetKind is found for the specified value
     */
    public static BazelTargetKind valueOfIgnoresCaseRequiresMatch(String value) {
        BazelTargetKind targetKind = valueOfIgnoresCase(value);
        if (targetKind == null) {
            throw new IllegalStateException("No matching TargetKind found for value [" + value + "]");
        }
        return targetKind;
    }

    /**
     * Gets the map (key is the kind string, value is the kind object) of registered target kinds.
     */
    public static Map<String, BazelTargetKind> getKnownKinds() {
        // returning the live copy, if the SDK user thinks they know a good reason to modify this then
        // give them the ability but hopefully they know what they are doing.
        return knownInstances;
    }

    /**
     * Returns the target kind as a String.
     */
    public String getKind() {
        return targetKind;
    }

    /**
     * Returns true if this target kind is runnable using "bazel run".
     */
    public boolean isRunnable() {
        return isRunnable;
    }

    /**
     * Returns true if this target kind is runnable using "bazel test".
     */
    public boolean isTestable() {
        return isTestable;
    }



    @Override
    public int hashCode() {
        return Objects.hash(targetKind);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        BazelTargetKind other = (BazelTargetKind) obj;
        return Objects.equals(targetKind, other.targetKind);
    }

    @Override
    public String toString() {
        return getKind();
    }
}
