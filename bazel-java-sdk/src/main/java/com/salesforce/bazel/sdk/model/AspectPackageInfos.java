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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A container for AspectPackageInfo instances.
 *
 * @author stoens
 * @since summer 2019
 */
public class AspectPackageInfos {

    public static final AspectPackageInfos EMPTY = new AspectPackageInfos(Collections.emptyList());

    private final Map<String, AspectPackageInfo> labelToAspectPackageInfo = new TreeMap<>();

    public AspectPackageInfos(AspectPackageInfo... aspectPackageInfos) {
        this(Arrays.asList(aspectPackageInfos));
    }

    public AspectPackageInfos(Collection<AspectPackageInfo> aspectPackageInfos) {
        for (AspectPackageInfo aspectPackageInfo : aspectPackageInfos) {
            AspectPackageInfo previousValue =
                    labelToAspectPackageInfo.put(aspectPackageInfo.getLabel(), aspectPackageInfo);
            if (previousValue != null) {
                if (!previousValue.toString().equals(aspectPackageInfo.toString())) {
                    throw new IllegalStateException("Did not expect a duplicate label with different contents: " + previousValue.getLabel());
                }
            }
        }
    }

    public static AspectPackageInfos fromSets(Collection<Set<AspectPackageInfo>> aspectPackageInfoSets) {
        Set<AspectPackageInfo> infoList = new LinkedHashSet<>(); // with TreeSet: class com.salesforce.bazel.eclipse.model.AspectPackageInfo cannot be cast to class java.lang.Comparable
        for (Set<AspectPackageInfo> set : aspectPackageInfoSets) {
            for (AspectPackageInfo info : set) {
                infoList.add(info);
            }
        }
        return new AspectPackageInfos(infoList);
    }

    public AspectPackageInfo lookupByLabel(String label) {
        return labelToAspectPackageInfo.get(label);
    }
    
    public AspectPackageInfo lookByPackageName(String name) {
        for (String key : labelToAspectPackageInfo.keySet()) {
            if (key.startsWith(name)) {
                return labelToAspectPackageInfo.get(key);
            }
        }
        return null; //TODO: make it better
    }

    public Collection<AspectPackageInfo> lookupByTargetKind(EnumSet<TargetKind> requestedTargetKinds) {
        List<AspectPackageInfo> aspectPackageInfos = new ArrayList<>();
        for (AspectPackageInfo aspectPackageInfo : labelToAspectPackageInfo.values()) {
            if (requestedTargetKinds.contains(TargetKind.valueOfIgnoresCase(aspectPackageInfo.getKind()))) {
                aspectPackageInfos.add(aspectPackageInfo);
            }
        }
        return aspectPackageInfos;
    }

    /**
     * Returns all AspectPackageInfo instances that have one or more matching root source path.
     */
    public Collection<AspectPackageInfo> lookupByRootSourcePath(String rootSourcePath) {
        List<AspectPackageInfo> aspectPackageInfos = new ArrayList<>();
        Path rootSourcePathP = Paths.get(rootSourcePath);
        for (AspectPackageInfo aspectPackageInfo : labelToAspectPackageInfo.values()) {
            for (String sourcePath : aspectPackageInfo.getSources()) {
                if (Paths.get(sourcePath).startsWith(rootSourcePathP)) {
                    assertAllSourcesHaveSameRootPath(rootSourcePathP, aspectPackageInfo);
                    aspectPackageInfos.add(aspectPackageInfo);
                    break;
                }
            }
        }
        return aspectPackageInfos;
    }

    public Iterable<AspectPackageInfo> getPackageInfos() {
        return this.labelToAspectPackageInfo.values();
    }
    
    private static void assertAllSourcesHaveSameRootPath(Path rootSourcePath, AspectPackageInfo aspectPackageInfo) {
        for (String sourcePath : aspectPackageInfo.getSources()) {
            if (!Paths.get(sourcePath).startsWith(rootSourcePath)) {
                throw new IllegalStateException("AspectPackageInfo " + aspectPackageInfo.getLabel()
                        + " has sources that are not under " + rootSourcePath + ": " + aspectPackageInfo.getSources());
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String label : labelToAspectPackageInfo.keySet()) {
            sb.append(label).append("->").append(labelToAspectPackageInfo.get(label).getSources()).append(", ");

        }
        return sb.toString();
    }
}
