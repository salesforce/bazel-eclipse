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

import java.io.File;
import java.util.Collection;
import java.util.Objects;

/**
 * A wrapper for a Bazel logical "problem" (error/warning).
 *
 * @author nishant.dsouza
 * @since 5/3/2019
 */
public class BazelProblem {

    private final String resourcePath;
    private final int lineNumber;
    private final String description;
    private final boolean isError;

    /**
     * Returns a new Error level BazelProblem.
     */
    public static BazelProblem createError(String resourcePath, int lineNumber, String description) {
        return new BazelProblem(resourcePath, lineNumber, description, true);
    }

    /**
     * Returns a new Warning level BazelProblem.
     */
    public static BazelProblem createWarning(String resourcePath, int lineNumber, String description) {
        return new BazelProblem(resourcePath, lineNumber, description, false);
    }

    private BazelProblem(String resourcePath, int lineNumber, String description, boolean isError) {
        this.resourcePath = Objects.requireNonNull(resourcePath);
        this.lineNumber = lineNumber;
        this.description = Objects.requireNonNull(description);
        this.isError = isError;
    }

    /**
     * Returns the matching BazelLabel for this problem's resourcePath.
     *
     * @param labels
     *            all BazelLabel instances to consider
     * @return the matching BazelLabel, null if no match is found
     */
    public BazelLabel getOwningLabel(Collection<BazelLabel> labels) {
        String shortestRelativeResourcePath = null;
        BazelLabel bestMatch = null;
        for (BazelLabel label : labels) {
            String relativeResourcePath = getRelativeResourcePath(label);
            if (relativeResourcePath != null) {
                if (shortestRelativeResourcePath == null
                        || relativeResourcePath.length() < shortestRelativeResourcePath.length()) {
                    bestMatch = label;
                    shortestRelativeResourcePath = relativeResourcePath;
                }
            }
        }
        return bestMatch;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public String getDescription() {
        return description;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public boolean isError() {
        return isError;
    }

    public BazelProblem toErrorWithRelativizedResourcePath(BazelLabel label) {
        String rel = getRelativeResourcePath(label);
        if (rel == null) {
            throw new IllegalArgumentException(
                    "Unable to build a relative path for " + resourcePath + " based on label " + label);
        }
        return new BazelProblem(rel, lineNumber, description, isError);
    }

    public BazelProblem toGenericWorkspaceLevelError(String descriptionPrefix) {
        return new BazelProblem(File.separator + "WORKSPACE", 0, descriptionPrefix + resourcePath + " " + description,
                isError);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourcePath, description, lineNumber);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BazelProblem)) {
            return false;
        }
        BazelProblem o = (BazelProblem) other;
        return resourcePath.equals(o.resourcePath) && description.equals(o.description) && lineNumber == o.lineNumber;
    }

    @Override
    public String toString() {
        return (isError ? "ERROR: " : "WARNING: ") + getResourcePath() + ":" + lineNumber + " " + getDescription();
    }

    private String getRelativeResourcePath(BazelLabel label) {
        String packagePath = label.getPackagePath();
        if (resourcePath.startsWith(packagePath + File.separator) && resourcePath.length() > packagePath.length() + 1) {
            return resourcePath.substring(packagePath.length());
        }
        return null;
    }

}
