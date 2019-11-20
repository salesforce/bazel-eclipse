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
 *
 */
package com.salesforce.bazel.eclipse.model.projectviews;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/** A class to create a {@link ProjectView} */
public class Builder {
    private ImmutableList.Builder<String> buildFlags = ImmutableList.builder();
    private ImmutableList.Builder<String> directories = ImmutableList.builder();
    private ImmutableList.Builder<String> targets = ImmutableList.builder();
    private int javaLanguageLevel = 0; // <= 0 means take it from java_toolchain.

    Builder() {}

    public Builder addBuildFlag(String... flag) {
        buildFlags.add(flag);
        return this;
    }

    public Builder addDirectory(String... dir) {
        directories.add(dir);
        return this;
    }

    public Builder addTarget(String... target) {
        targets.add(target);
        return this;
    }

    public Builder setJavaLanguageLevel(int level) {
        Preconditions.checkArgument(level > 0, "Can only set java language level to a value > 0");
        Preconditions.checkArgument(javaLanguageLevel == 0, "Java language level was already set");
        javaLanguageLevel = level;
        return this;
    }

    // State object using while parsing a stream
    private String currentSection = null;

    /**
     * Parse the project view given in view, following also imports.
     * 
     * @throws IOException
     * @throws ProjectViewParseException
     */
    public Builder parseView(File view) throws IOException, ProjectViewParseException {
        int linenb = 0;
        for (String line : Files.readAllLines(view.toPath())) {
            linenb++;
            parseLine(view.getPath(), view.getParentFile(), line, linenb);
        }
        currentSection = null;
        return this;
    }

    /**
     * Parse the project view at the given {@code url}.
     * 
     * @throws IOException
     * @throws ProjectViewParseException
     */
    public Builder parseView(URL url) throws IOException, ProjectViewParseException {
        int linenb = 0;

        if (url == null) {
            throw new ProjectViewParseException("URL to the ProjectView file is null.");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
            for (String line : reader.lines().toArray(String[]::new)) {
                linenb++;
                parseLine(url.toString(), null, line, linenb);
            }
        }
        currentSection = null;
        return this;
    }

    private void parseLine(String fileName, File parentFile, String line, int linenb)
            throws ProjectViewParseException, IOException {
        if (line.isEmpty()) {
            currentSection = null;
        } else if (line.startsWith("  ")) {
            if (currentSection == null) {
                throw new ProjectViewParseException(
                        "Line " + linenb + " of project view " + fileName + " is not in a section");
            }
            if (currentSection.equals("directories")) {
                directories.add(line.substring(2));
            } else if (currentSection.equals("targets")) {
                targets.add(line.substring(2));
            } else if (currentSection.equals("build_flags")) {
                buildFlags.add(line.substring(2));
            } // else ignoring other sections
        } else if (line.startsWith("import ")) {
            // imports
            String path = line.substring(7);
            if (path.startsWith("/")) {
                parseView(new File(path));
            } else {
                parseView(new File(parentFile, path));
            }
        } else if (line.contains(":")) {
            // section declaration
            line = line.trim();
            if (line.endsWith(":")) {
                currentSection = line.substring(0, line.length() - 1);
                if (currentSection.equals("java_language_level")) {
                    throw new ProjectViewParseException("Line " + linenb + " of project view " + fileName
                            + ": java_language_level cannot be a section name");
                }
            } else {
                int colonIndex = line.indexOf(':');
                String label = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                if (label.equals("directories") || label.equals("import") || label.equals("targets")
                        || label.equals("build_flags")) {
                    throw new ProjectViewParseException("Line " + linenb + " of project view " + fileName + ": " + label
                            + " cannot be a label name");
                }
                if (label.equals("java_language_level")) {
                    if (!value.matches("^[0-9]+$")) {
                        throw new ProjectViewParseException("Line " + linenb + " of project view " + fileName
                                + ": java_language_level should be an integer.");
                    }
                    javaLanguageLevel = Integer.parseInt(value);
                }
            }
        } else if (!line.trim().startsWith("#")) {
            throw new ProjectViewParseException(
                    "Project view " + fileName + " contains a syntax error at line " + linenb);
        }
    }

    public ProjectView build() {
        return new ProjectViewImpl(directories.build(), targets.build(), javaLanguageLevel, buildFlags.build());
    }
}
