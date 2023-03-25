/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.salesforce.bazel.sdk.aspect;

import com.salesforce.bazel.sdk.primitives.LanguageClass;

/** Enumerates the sets of aspect output groups corresponding to each language */
public enum LanguageOutputGroup {
    ANDROID(LanguageClass.ANDROID, "android"),
    C(LanguageClass.C, "cpp"),
    JAVA(LanguageClass.JAVA, "java"),
    KOTLIN(LanguageClass.KOTLIN, "kt"),
    PYTHON(LanguageClass.PYTHON, "py"),
    GO(LanguageClass.GO, "go"),
    JAVASCRIPT(LanguageClass.JAVASCRIPT, "js"),
    TYPESCRIPT(LanguageClass.TYPESCRIPT, "ts"),
    DART(LanguageClass.DART, "dart");

    public static LanguageOutputGroup forLanguage(LanguageClass languageClass) {
        for (LanguageOutputGroup group : values()) {
            if (group.languageClass == languageClass) {
                return group;
            }
        }
        return null;
    }

    public final LanguageClass languageClass;
    public final String suffix;

    LanguageOutputGroup(LanguageClass languageClass, String suffix) {
        this.languageClass = languageClass;
        this.suffix = suffix;
    }
}