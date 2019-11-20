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
package com.salesforce.bazel.eclipse.model;

/**
 * The Bazel targets we support.
 */
public enum TargetKind {

    JAVA_LIBRARY("java_library") {

        @Override
        public boolean isRunnable() {
            return false;
        }

        @Override
        public boolean isTestable() {
            return false;
        }
    },

    JAVA_BINARY("java_binary") {

        @Override
        public boolean isRunnable() {
            return true;
        }

        @Override
        public boolean isTestable() {
            return false;
        }
    },

    JAVA_TEST("java_test") {

        @Override
        public boolean isRunnable() {
            return false;
        }

        @Override
        public boolean isTestable() {
            return true;
        }
    };

    private final String targetKind;

    private TargetKind(String targetKind) {
        this.targetKind = targetKind;
    }

    /**
     * Returns the corresponding TargetKind value based on the specified String value, ignoring the casing of the given
     * value. Returns null if no matching TargetKind value is found.
     * 
     * @return matching TargetKind instance, null if no match
     */
    public static TargetKind valueOfIgnoresCase(String value) {
        try {
            return TargetKind.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Returns the corresponding TargetKind value based on the specified String value, ignoring the casing of the given
     * value. Throws an Exception if not matching TargetKind value is found.
     * 
     * @return matching TargetKind instance
     * @throws IllegalStateException
     *             if no matching TargetKind is found for the specified value
     */
    public static TargetKind valueOfIgnoresCaseRequiresMatch(String value) {
        TargetKind targetKind = valueOfIgnoresCase(value);
        if (targetKind == null) {
            throw new IllegalStateException("No matching TargetKind found for value [" + value + "]");
        }
        return targetKind;
    }

    /**
     * Returns the target kind as a String.
     */
    public String getKind() {
        return this.targetKind;
    }

    /**
     * Returns true if this target kind is runnable using "bazel run".
     */
    public abstract boolean isRunnable();

    /**
     * Returns true if this target kind is runnable using "bazel test".
     */
    public abstract boolean isTestable();

    @Override
    public String toString() {
        return getKind();
    }
}
