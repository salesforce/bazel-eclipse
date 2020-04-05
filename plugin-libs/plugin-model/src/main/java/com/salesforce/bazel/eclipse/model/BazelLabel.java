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
 * Answers to everything you've always wanted to ask a Bazel Label.
 * </p>
 * Pass this around in code instead of String primitives.
 * </p>
 * <b>IMPORTANT NOTE</b> internally this class assumes that '/' is the path separator used in label/target names. This
 * will most likely need to get fixed to support running on Windows.
 * </p>
 * 
 * @author stoens
 * @since Hawaii 2019
 */
public class BazelLabel {

    private final String label;

    /**
     * A BazelLabel instance can be created with any syntactically valid Bazel Label String.
     * </p>
     * Examples:<br>
     * //foo/blah:t1<br>
     * //foo<br>
     * blah/...<br>
     */
    public BazelLabel(String label) {
        validate(label);
        this.label = normalize(label);
    }

    /**
     * If a label omits the target name it refers to and it doesn't use wildcard syntax, it refers to the
     * package-default target. This is that target that has the same name as the Bazel Package it lives in.
     *
     * @return true if this instance points to the package default target, false otherwise
     */
    public boolean isPackageDefault() {
        if (!isConcrete()) {
            return false;
        }
        int i = this.label.lastIndexOf(":");
        return i == -1;
    }

    /**
     * If a label refers to a single Bazel Target, is it concrete. If it using wildcard syntax, it is not concrete.
     *
     * @return true if this instance represents a concrete label, false otherwise
     */
    public boolean isConcrete() {
        return !this.label.endsWith("*") && !this.label.endsWith("...");
    }

    /**
     * Returns the package path of this label, which is the "path part" of the label, excluding any specific target or
     * target wildcard pattern.
     * 
     * For example, given a label //foo/blah/goo:t1, the package path is foo/blah/goo.
     * 
     * @return the package path of this label
     */
    public String getPackagePath() {
        String packagePath = this.label;
        int i = packagePath.lastIndexOf("...");
        if (i != -1) {
            packagePath = packagePath.substring(0, i);
            if (packagePath.endsWith("/")) {
                packagePath = packagePath.substring(0, packagePath.length() - 1);
            }
        } else {
            i = this.label.lastIndexOf(":");
            if (i != -1) {
                packagePath = packagePath.substring(0, i);
            }
        }
        return packagePath;
    }

    /**
     * Returns the default package label for this label.  The default package label does not specify
     * an explicit target and only corresponds to the package path.
     * 
     * For example, given //foo/blah/goo:t1, the corresponding default package label is //foo/blah/goo.
     * 
     * @return BazelLabel instance representing the default package label.
     * @throws IllegalArgumentException if this label is a root-level label (//...) and therefore doesn't have a
     *     package path. 
     */
    public BazelLabel getDefaultPackageLabel() {
        return new BazelLabel(getPackagePath());
    }
    
    /**
     * Returns the package name of this label, which is the right-most path component of the package path.
     * 
     * For example, given a label //foo/blah/goo:t1, the package name is goo.
     * 
     * @return the package name of this label
     */
    public String getPackageName() {
        String packagePath = getPackagePath();
        int i = packagePath.lastIndexOf("/");
        return i == -1 ? packagePath : packagePath.substring(i + 1);
    }

    /**
     * Returns the target name this label refers to.
     *
     * @return the target name this label refers to, or null if it doesn't refer to a single target name
     */
    public String getTargetName() {
        if (isConcrete()) {
            if (isPackageDefault()) {
                return getPackageName();
            } else {
                int i = this.label.lastIndexOf(":");
                // label cannot end with ":", so this is ok
                return this.label.substring(i + 1);
            }
        } else {
            return null;
        }
    }

    /**
     * Some Bazel Target names use a path-like syntax. This method returns the last component of that path. If the
     * target name doesn't use a path-like syntax, this method returns the target name.
     * 
     * For example: if the target name is "a/b/c/d", this method returns "d". if the target name is "a/b/c/", this
     * method returns "c". if the target name is "foo", this method returns "foo".
     * 
     * @return the last path component of the target name if the target name is path-like
     */
    public String getLastComponentOfTargetName() {
        String targetName = getTargetName();
        if (targetName == null) {
            return null;
        }
        int i = targetName.lastIndexOf("/");
        if (i != -1) {
            return targetName.substring(i + 1); // ok because target name cannot end with '/'
        }
        return targetName;
    }

    /**
     * Returns the label as a String.
     *
     * @return the label
     */
    public String getLabel() {
        return "//" + this.label;
    }

    /**
     * Adds package wildcard syntax to a package default label.
     *
     * For example: //foo/blah -> //foo:blah:*
     *
     * @return a new BazelLabel instance, with added package wildcard syntax
     * @throws IllegalStateException
     *             if this is not a package default label
     */
    public BazelLabel toPackageWildcardLabel() {
        if (!isPackageDefault()) {
            throw new IllegalStateException("label " + this.label + " is not package default");
        }
        return new BazelLabel(this.label + ":*");
    }

    @Override
    public int hashCode() {
        return label.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof BazelLabel) {
            BazelLabel o = (BazelLabel) other;
            return label.equals(o.label);
        }
        return false;
    }

    @Override
    public String toString() {
        return getLabel();
    }

    private static void validate(String label) {
        if (label == null) {
            throw new IllegalArgumentException(label);
        }
        label = label.trim();
        if (label.length() == 0) {
            throw new IllegalArgumentException(label);
        }
        if (label.endsWith(":")) {
            throw new IllegalArgumentException(label);
        }
        if (label.endsWith("/")) {
            throw new IllegalArgumentException(label);
        }
        if (label.equals("//")) {
            throw new IllegalArgumentException(label);
        }
    }

    private static String normalize(String label) {
        label = label.trim();
        // internally we store the label without leading "//"
        if (label.startsWith("//")) {
            label = label.substring(2);
        }
        return label;
    }
}
