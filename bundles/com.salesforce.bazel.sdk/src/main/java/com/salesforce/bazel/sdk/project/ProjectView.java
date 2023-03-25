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
package com.salesforce.bazel.sdk.project;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.util.BazelConstants;

/**
 * The <a href="http://ij.bazel.build/docs/project-views.html">project view</a> file.
 * </p>
 * This implementations currently only supports a subset of the functionality provided by the IntelliJ Bazel Plugin,
 * namely these sections:
 * </p>
 * <ul>
 * <li>directories</li>
 * </ul>
 *
 * Example project view file:
 *
 * <pre>
 * directories:
 *   path/to/bazel/package1
 *   path/to/bazel/package2
 *
 * targets:
 *   # the targets section is optional
 *   //path/to/bazel/package1:t1
 * </pre>
 *
 * Exclusions are not supported yet.
 */
public class ProjectView {

    static final int INIT_INDENT = 3;
    static final String DIRECTORIES_SECTION = "directories:";
    static final String TARGETS_SECTION = "targets:";
    static final String DIRECTORIES_COMMENT = "# Add the directories you want added as source here";
    static final String INDENT = "  ";
    static final String SECTION_COLON = ":"; // section headers have a trailing colon
    static final String COMMENT_PREFIX = "#";

    private static void initSections(List<BazelPackageLocation> packages, List<BazelLabel> targets,
            Map<BazelPackageLocation, Integer> packageToLineNumber, Map<BazelLabel, Integer> targetToLineNumber) {
        // directories:
        //   # comment
        // therefore:
        var lineNumber = INIT_INDENT;
        for (BazelPackageLocation pack : packages) {
            packageToLineNumber.put(pack, lineNumber);
            lineNumber += 1;
        }
        // newline
        lineNumber += 1;
        for (BazelLabel target : targets) {
            targetToLineNumber.put(target, lineNumber);
            lineNumber += 1;
        }
    }

    private static void parseSections(String content, File rootWorkspaceDirectory,
            Map<BazelPackageLocation, Integer> packageToLineNumber, Map<BazelLabel, Integer> targetToLineNumber) {
        var withinDirectoriesSection = false;
        var withinTargetsSection = false;
        var lineNumber = 0;
        for (String line : content.split(System.lineSeparator())) {
            lineNumber += 1;
            line = line.trim();
            if (line.isEmpty() || line.startsWith(COMMENT_PREFIX)) {
                continue;
            }
            if (DIRECTORIES_SECTION.equals(line)) {
                withinDirectoriesSection = true;
                withinTargetsSection = false;
                continue;
            }
            if (TARGETS_SECTION.equals(line)) {
                withinDirectoriesSection = false;
                withinTargetsSection = true;
                continue;
            } else if (line.endsWith(SECTION_COLON)) {
                // some other yet unknown section
                withinDirectoriesSection = false;
                withinTargetsSection = false;
                continue;
            }
            if (withinDirectoriesSection) {
                packageToLineNumber.put(new ProjectViewPackageLocation(rootWorkspaceDirectory, line), lineNumber);
            } else if (withinTargetsSection) {
                targetToLineNumber.put(new BazelLabel(line), lineNumber);
            }
        }
    }

    private final File rootWorkspaceDirectory;

    private final Map<BazelPackageLocation, Integer> packageToLineNumber;

    private final Map<BazelLabel, Integer> targetToLineNumber;

    /**
     * Create a new ProjectView instance with the specified directories and targets.
     */
    public ProjectView(File rootWorkspaceDirectory, List<BazelPackageLocation> directories, List<BazelLabel> targets) {
        this.rootWorkspaceDirectory = rootWorkspaceDirectory;
        Map<BazelPackageLocation, Integer> pl = new LinkedHashMap<>();
        Map<BazelLabel, Integer> tl = new LinkedHashMap<>();
        initSections(directories, targets, pl, tl);
        packageToLineNumber = Collections.unmodifiableMap(pl);
        // this may get modified, so the map has to be mutable
        targetToLineNumber = tl;
    }

    /**
     * Creates a new ProjectView instance with the specified raw content.
     */
    public ProjectView(File rootWorkspaceDirectory, String content) {
        this.rootWorkspaceDirectory = rootWorkspaceDirectory;
        Map<BazelPackageLocation, Integer> pl = new LinkedHashMap<>();
        Map<BazelLabel, Integer> tl = new LinkedHashMap<>();
        parseSections(content, rootWorkspaceDirectory, pl, tl);
        packageToLineNumber = Collections.unmodifiableMap(pl);
        // this may get modified, so the map has to be mutable
        targetToLineNumber = tl;
    }

    /**
     * Adds the default targets for each directory that does not have one (or more) entries in the "targets:" section.
     */
    public void addDefaultTargets() {
        List<BazelLabel> defaultLabels = new ArrayList<>();
        for (BazelPackageLocation directory : packageToLineNumber.keySet()) {
            var foundLabel = false;
            var bazelPackage = new BazelLabel(directory.getBazelPackageFSRelativePath());
            for (BazelLabel label : targetToLineNumber.keySet()) {
                if (label.getPackagePath().equals(bazelPackage.getPackagePath())) {
                    foundLabel = true;
                    break;
                }
            }
            if (!foundLabel) {
                for (String target : BazelConstants.DEFAULT_PACKAGE_TARGETS) {
                    defaultLabels.add(new BazelLabel(bazelPackage.getPackagePath(), target));
                }
            }
        }
        for (BazelLabel dflt : defaultLabels) {
            // since this method is used to adjust internal state, it is ok for the
            // line number to not be correct.
            targetToLineNumber.put(dflt, 0);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProjectView o)) {
            return false;
        }
        return packageToLineNumber.keySet().equals(o.packageToLineNumber.keySet())
                && targetToLineNumber.keySet().equals(o.targetToLineNumber.keySet());
    }

    /**
     * Returns the raw project view file content.
     */
    public String getContent() {
        var sb = new StringBuilder();
        sb.append(DIRECTORIES_SECTION).append(System.lineSeparator());
        sb.append(INDENT).append(DIRECTORIES_COMMENT).append(System.lineSeparator());
        for (BazelPackageLocation pack : packageToLineNumber.keySet()) {
            sb.append(INDENT).append(pack.getBazelPackageFSRelativePath()).append(System.lineSeparator());
        }
        if (!targetToLineNumber.isEmpty()) {
            sb.append(System.lineSeparator());
            sb.append(TARGETS_SECTION).append(System.lineSeparator());
            for (BazelLabel target : targetToLineNumber.keySet()) {
                sb.append(INDENT).append(target.getLabelPath()).append(System.lineSeparator());
            }
        }
        return sb.toString();
    }

    /**
     * Returns the directories with their targets.
     */
    public List<BazelPackageLocation> getDirectories() {
        List<BazelPackageLocation> updatedPackageLocations = new ArrayList<>(packageToLineNumber.size());
        for (BazelPackageLocation packageInfo : packageToLineNumber.keySet()) {
            var directory = packageInfo.getBazelPackageFSRelativePath();
            var targets = getTargetsForDirectory(directory);
            updatedPackageLocations.add(new ProjectViewPackageLocation(rootWorkspaceDirectory, directory, targets));
        }
        return Collections.unmodifiableList(updatedPackageLocations);
    }

    /**
     * Returns the line number, in the raw project view file content, of the specified bazel package, in the
     * "directories" section.
     */
    public int getLineNumber(BazelPackageLocation pack) {
        var lineNumber = packageToLineNumber.get(pack);
        if (lineNumber == null) {
            throw new IllegalArgumentException("Unknown " + pack);
        }
        return lineNumber;
    }

    /**
     * Returns only the targets from the targets: section.
     */
    public List<BazelLabel> getTargets() {
        return Collections.unmodifiableList(new ArrayList<>(targetToLineNumber.keySet()));
    }

    private List<BazelLabel> getTargetsForDirectory(String directory) {
        List<BazelLabel> targets = null;
        var bazelPackage = new BazelLabel(directory);
        for (BazelLabel target : targetToLineNumber.keySet()) {
            if (target.getPackagePath().equals(bazelPackage.getPackagePath())) {
                if (targets == null) {
                    targets = new ArrayList<>();
                }
                targets.add(target);
            }
        }
        return targets;
    }

    public File getWorkspaceRootDirectory() {
        return rootWorkspaceDirectory;
    }

    @Override
    public int hashCode() {
        return packageToLineNumber.keySet().hashCode() ^ targetToLineNumber.keySet().hashCode();
    }

}
