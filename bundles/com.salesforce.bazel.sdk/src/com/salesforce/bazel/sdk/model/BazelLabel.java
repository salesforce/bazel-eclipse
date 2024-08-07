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

import static java.lang.String.format;

import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;

/**
 * Answers to everything you've always wanted to ask a Bazel Label.
 * </p>
 * Pass this around in code instead of String primitives.
 */
public class BazelLabel {

    // BAZEL PATH CONSTANTS
    // Please use these in your code, instead of hardcoded Strings. It makes it easier to
    // reason about path manipulation code.

    // Wildcard used as a target, that identifies all targets including implicit targets (_deploy.jar etc)
    public static final String BAZEL_WILDCARD_ALLTARGETS_STAR = ":*";

    // Wildcard used as a target, that identifies all targets
    public static final String BAZEL_WILDCARD_ALLTARGETS = ":all";

    // Wildcard used as a package, that identifies all packages at the current level or below
    public static final String BAZEL_WILDCARD_ALLPACKAGES = "...";

    // All packages wildcard
    public static final String BAZEL_ALL_REPO_PACKAGES = "//...";

    // Root package wildcard
    public static final String BAZEL_ROOT_PACKAGE_ALLTARGETS = "//:*";

    // Double slash characters for root of Bazel paths
    public static final String BAZEL_ROOT_SLASHES = "//";

    // Colon character for Bazel paths that delimits the target
    public static final String BAZEL_COLON = ":";

    // Slash character for Bazel paths
    public static final String BAZEL_SLASH = "/";

    // @ symbol that precedes external repo paths
    public static final String BAZEL_EXTERNALREPO_AT = "@";

    // @@ for canonical repo names (https://docs.google.com/document/d/1N81qfCa8oskCk5LqTW-LNthy6EBrDot7bdUsjz6JFC4/edit#heading=h.5mcn15i0e1ch)
    public static final String BAZEL_CANONICAL_EXTERNALREPO_AT = "@@";

    // INSTANCE MEMBERS

    /**
     * Assembles the BazelLabel from an external repository name and the local label path. (foo, //a/b/c)
     * => @foo//a/b/c)
     */
    private static BazelLabel getFullLabel(String externalRepositoryName, String localLabelPart) {
        var fullLabelStr = getFullLabelPath(externalRepositoryName, localLabelPart);
        return new BazelLabel(fullLabelStr);
    }

    /**
     * Assembles the full path from an external repository name and the local label path. (foo, //a/b/c) => @foo//a/b/c)
     */
    private static String getFullLabelPath(String externalRepositoryName, String localLabelPart) {
        var result = BazelLabel.BAZEL_ROOT_SLASHES + localLabelPart;
        if (externalRepositoryName != null) {
            result = BazelLabel.BAZEL_EXTERNALREPO_AT + externalRepositoryName + result;
        }
        return result;
    }

    /**
     * Converts the label path to the relative label path.
     * <p>
     * //a/b/c returns a/b/c, /a/b/c returns a/b/c
     */
    private static String makeLabelPathRelative(String labelPath) {
        labelPath = labelPath.trim();
        if (labelPath.startsWith(BAZEL_ROOT_SLASHES)) {
            labelPath = labelPath.substring(2);
        } else if (labelPath.startsWith(BAZEL_SLASH)) {
            labelPath = labelPath.substring(1);
        }
        return labelPath;
    }

    // CTORS

    /**
     * Removes the trailing slash from a path
     */
    private static String sanitizePackagePath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("BazelLabel cannot have a null path.");
        }
        path = path.trim();
        if (path.endsWith(BazelLabel.BAZEL_SLASH)) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * Trims a leading colon from the target
     */
    private static String sanitizeTargetName(String target) {
        if (target == null) {
            // TODO this should be allowed, a null target implies the default target (e.g. //a/b/c => //a/b/c:c)
            throw new IllegalArgumentException("BazelLabel needs a target");
        }
        target = target.trim();
        if (target.startsWith(BazelLabel.BAZEL_COLON)) {
            target = target.substring(1);
        }
        return target;
    }

    // PATH OPERATIONS

    /**
     * Looks for obvious label format errors.
     * <p>
     * Returns false, or throws an IllegalArgumentException (if throwOnError=true) if a problem is found.
     */
    public static boolean validateLabelPath(String labelPathStr, boolean throwOnError) {
        try {
            if (labelPathStr == null) {
                throw new IllegalArgumentException("Illegal Bazel path string: " + labelPathStr);
            }
            if (BAZEL_ROOT_SLASHES.equals(labelPathStr)) {
                // this is a special case, it is a legal label so we will shall let it pass
                return true;
            }

            labelPathStr = labelPathStr.trim();

            if (labelPathStr.endsWith(BAZEL_ROOT_SLASHES) && labelPathStr.startsWith(BAZEL_EXTERNALREPO_AT)) {
                // this is a special case, it is a legal label so we will shall let it pass
                return true;
            }
            if ((labelPathStr.length() == 0) || labelPathStr.endsWith(BAZEL_COLON)) {
                throw new IllegalArgumentException("Illegal Bazel path string: " + labelPathStr);
            }
            if (labelPathStr.endsWith(BAZEL_SLASH)) {
                throw new IllegalArgumentException(labelPathStr);
            }
            if (labelPathStr.contains("\\")) {
                // the caller is passing us a label with \ as separators, probably a bug due to Windows paths
                throw new IllegalArgumentException(
                        "Label [" + labelPathStr
                                + "] has Windows style path delimeters. Bazel paths always have / delimiters");
            }
        } catch (IllegalArgumentException iae) {
            if (throwOnError) {
                throw iae;
            }
            return false;
        }
        return true;
    }

    // the full label path, as it is known by Bazel
    private final String fullLabel;

    // the local label path part
    // for //a/b/c, this will be a/b/c
    // for @foo//a/b/c this will be a/b/c
    private final String localLabelPart;

    // if this label points to an external repo, the repository name; otherwise null
    // for @foo//a/b/c this will be foo
    private final String repositoryName;

    /**
     * A BazelLabel instance can be created with any syntactically valid Bazel Label String.
     * </p>
     * Examples:<br>
     * //foo/blah:t1<br>
     * //foo<br>
     * blah/...<br>
     * <p>
     * Throws an IllegalArgumentException is the label string does not parse correctly.
     */
    public BazelLabel(String labelPathStr) {
        BazelLabel.validateLabelPath(labelPathStr, true);

        // does this label refer to an external repo? Ex: @foo//a/b/c:d
        if (labelPathStr.startsWith(BAZEL_EXTERNALREPO_AT)) {
            // also deal with incompatible_unambiguous_label_stringification bazelbuild/bazel#16196 (and bzlmod)
            labelPathStr = labelPathStr.startsWith(BAZEL_CANONICAL_EXTERNALREPO_AT)
                    ? labelPathStr.substring(BAZEL_CANONICAL_EXTERNALREPO_AT.length())
                    : labelPathStr.substring(BAZEL_EXTERNALREPO_AT.length());
            var i = labelPathStr.indexOf(BAZEL_ROOT_SLASHES);
            if (i > 0) {
                repositoryName = labelPathStr.substring(0, i);
                labelPathStr = labelPathStr.substring(i);
            } else {
                repositoryName = null;
            }
        } else {
            repositoryName = null;
        }
        localLabelPart = BazelLabel.makeLabelPathRelative(labelPathStr);
        fullLabel = getFullLabelPath(repositoryName, localLabelPart);
    }

    /**
     * Instantiates a BazelLabel instance with the Bazel package path and the label name specified separately. For
     * example: "a/b/c" and "my-target-name" becomes //a/b/c:my-target-name.
     */
    public BazelLabel(String packagePath, String targetName) {
        this(sanitizePackagePath(packagePath) + BazelLabel.BAZEL_COLON + sanitizeTargetName(targetName));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof BazelLabel o) {
            return fullLabel.equals(o.fullLabel);
        }
        return false;
    }

    /**
     * Returns the repository of this label, null if no repository was specified for this label.
     *
     * @return the repository name, without the leading '@' and trailing "//"
     */
    public String getExternalRepositoryName() {
        return repositoryName;
    }

    // EXTERNAL REPOS

    /**
     * Converts label to a package wildcard path.
     * <p>
     * Example1: //foo/blah -> //foo:blah:*<br>
     * Example2: //foo/blah:bar -> //foo:blah:*
     *
     * @return a new BazelLabel instance, with added package wildcard syntax
     * @throws IllegalStateException
     *             if this is not a package default label
     */
    @Deprecated
    public BazelLabel getLabelAsWildcard() {
        // TODO why do we need this?
        // TODO this shouldnt care about default target
        if (!isDefaultTarget()) {
            throw new IllegalStateException("label " + localLabelPart + " is not package default");
        }
        // TODO all check for :all?
        return getFullLabel(
            repositoryName,
            getPackagePath() + BazelLabel.BAZEL_COLON + BazelLabel.BAZEL_WILDCARD_ALLTARGETS_STAR);
    }

    /**
     * Returns the label path as a String.
     *
     * @return the label
     */
    public String getLabelPath() {
        // TODO seems like the default target should be added here if the target is missing
        return fullLabel;
    }

    // NATURES

    /**
     * Returns the package path of this label, which is the "path part" of the label, excluding any specific target or
     * target wildcard pattern.
     *
     * For example, given a label //foo/blah/goo:t1, the package label is //foo/blah/goo.
     *
     * @return the package path of this label
     */
    public BazelLabel getPackageLabel() {
        return new BazelLabel(getPackagePath(true));
    }

    /**
     * Returns the package name of this label, which is the right-most path component of the package path.
     * <p>
     * //foo/blah/goo:t1 => goo<br>
     * //foo/blah/... => blah
     *
     * @return the package name of this label
     */
    public String getPackageName() {
        var result = getPackagePath();
        var i = result.lastIndexOf(BazelLabel.BAZEL_SLASH);
        if (i != -1) {
            result = result.substring(i + 1);
        }
        i = result.lastIndexOf(BazelLabel.BAZEL_COLON);
        if (i != -1) {
            result = result.substring(0, i);
        }
        return result;
    }

    // MISC OPERATIONS

    /**
     * Returns the package path of this label, which is the "path part" of the label, excluding any specific target or
     * target wildcard pattern.
     *
     * For example, given a label //foo/blah/goo:t1, the package path is foo/blah/goo.
     *
     * @return the package path of this label
     */
    public String getPackagePath() {
        var packagePath = localLabelPart;
        var i = packagePath.lastIndexOf(BazelLabel.BAZEL_WILDCARD_ALLPACKAGES);
        if (i != -1) {
            packagePath = packagePath.substring(0, i);
            if (packagePath.endsWith(BazelLabel.BAZEL_SLASH)) {
                packagePath = packagePath.substring(0, packagePath.length() - 1);
            }
        } else {
            i = localLabelPart.lastIndexOf(BazelLabel.BAZEL_COLON);
            if (i != -1) {
                packagePath = packagePath.substring(0, i);
            }
        }
        return packagePath;
    }

    /**
     * Returns the package path of this label, which is the "path part" of the label, excluding any specific target or
     * target wildcard pattern.
     *
     * For example, given a label //foo/blah/goo:t1, the package path is foo/blah/goo.
     *
     * @return the package path of this label
     */
    public String getPackagePath(boolean includePrefixes) {
        if (!includePrefixes) {
            return getPackagePath();
        }
        var packagePath = fullLabel;
        var i = packagePath.lastIndexOf(BazelLabel.BAZEL_WILDCARD_ALLPACKAGES);
        if (i != -1) {
            packagePath = packagePath.substring(0, i);
            if (packagePath.endsWith(BazelLabel.BAZEL_SLASH)) {
                packagePath = packagePath.substring(0, packagePath.length() - 1);
            }
        } else {
            i = packagePath.lastIndexOf(BazelLabel.BAZEL_COLON);
            if (i != -1) {
                packagePath = packagePath.substring(0, i);
            }
        }
        return packagePath;
    }

    /**
     * Returns the target part of this label. //a/b/c:d => d
     * <p>
     * If this label does not specify a target, the default target is implied and this method will return that. //a/b/c
     * => //a/b/c:c => c
     *
     * @return the target name this label refers to, null if this label uses "..." syntax.
     */
    public String getTargetName() {
        if (localLabelPart.endsWith(BazelLabel.BAZEL_WILDCARD_ALLPACKAGES)) {
            // TODO why does * get a free pass here?
            return null;
        }
        if (isDefaultTarget()) {
            return getPackageName();
        }
        var colonIndex = localLabelPart.lastIndexOf(BazelLabel.BAZEL_COLON);
        return localLabelPart.substring(colonIndex + 1);
    }

    @Override
    public int hashCode() {
        return fullLabel.hashCode();
    }

    /**
     * @return <code>true</code> if this target is concrete ({@link #isConcrete()} returns <code>true</code>) and has a
     *         target component, <code>false</code> otherwise
     */
    public boolean hasTarget() {
        if (!isConcrete()) {
            return false;
        }
        var i = localLabelPart.lastIndexOf(BazelLabel.BAZEL_COLON);
        return i > -1;
    }

    /**
     * If a label refers to a single Bazel Target, is it concrete. If it using wildcard syntax, it is not concrete. For
     * the purposes of this method, a label using the default target (//a/b/c) is concrete.
     *
     * @return true if this instance represents a concrete label, false otherwise
     */
    public boolean isConcrete() {
        return !(localLabelPart.endsWith(BazelLabel.BAZEL_WILDCARD_ALLTARGETS)
                || localLabelPart.endsWith(BazelLabel.BAZEL_WILDCARD_ALLTARGETS_STAR)
                || localLabelPart.endsWith(BazelLabel.BAZEL_WILDCARD_ALLPACKAGES));
    }

    /**
     * If a label omits the target name it refers to and it doesn't use wildcard syntax, it refers to the
     * package-default target. This is that target that has the same name as the Bazel Package it lives in.
     *
     * @return true if this instance points to the package default target, false otherwise
     */
    public boolean isDefaultTarget() {
        if (!isConcrete()) {
            return false;
        }
        var i = localLabelPart.lastIndexOf(BazelLabel.BAZEL_COLON);
        return i == -1;
    }

    /**
     * Does this label refer to an external repo? Ex: @foo//a/b/c:d
     */
    public boolean isExternalRepoLabel() {
        return fullLabel.startsWith(BazelLabel.BAZEL_EXTERNALREPO_AT);
    }

    public WorkspacePath packagePathAsPrimitive() {
        return WorkspacePath.createIfValid(getPackagePath());
    }

    public Label toPrimitive() {
        if (!isConcrete()) {
            throw new IllegalStateException(
                    format("Label '%s' is not concrete and cannot be converted to a primitive label!", fullLabel));
        }

        if (isDefaultTarget()) {
            if (isExternalRepoLabel()) {
                return Label.create(
                    getExternalRepositoryName(),
                    new WorkspacePath(getPackagePath()),
                    TargetName.create(getTargetName()));
            } else {
                return Label.create(new WorkspacePath(getPackagePath()), TargetName.create(getTargetName()));
            }
        }

        return Label.create(fullLabel);
    }

    @Override
    public String toString() {
        return fullLabel;
    }
}
