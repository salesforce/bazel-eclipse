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
package com.salesforce.bazel.sdk.aspect;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.salesforce.bazel.sdk.model.BazelTargetKind;

/**
 * A container for AspectTargetInfo instances, keyed by the label.
 */
public class AspectTargetInfos {

    public static final AspectTargetInfos EMPTY = new AspectTargetInfos(Collections.emptyList());

    private final Map<String, AspectTargetInfo> labelToAspectTargetInfo = new TreeMap<>();

    public AspectTargetInfos(AspectTargetInfo... aspectTargetInfos) {
        this(Arrays.asList(aspectTargetInfos));
    }

    public AspectTargetInfos(Collection<AspectTargetInfo> aspectTargetInfos) {
        for (AspectTargetInfo aspectTargetInfo : aspectTargetInfos) {
            AspectTargetInfo previousValue =
                    labelToAspectTargetInfo.put(aspectTargetInfo.getLabelPath(), aspectTargetInfo);
            if (previousValue != null) {
                if (!previousValue.toString().equals(aspectTargetInfo.toString())) {
                    throw new IllegalStateException("Did not expect a duplicate label with different contents: "
                            + previousValue.getLabelPath());
                }
            }
        }
    }

    public static AspectTargetInfos fromSets(Collection<Set<AspectTargetInfo>> aspectTargetInfoSets) {
        Set<AspectTargetInfo> infoList = new LinkedHashSet<>(); // with TreeSet: class com.salesforce.bazel.sdk.aspect.AspectTargetInfo cannot be cast to class java.lang.Comparable
        for (Set<AspectTargetInfo> set : aspectTargetInfoSets) {
            for (AspectTargetInfo info : set) {
                infoList.add(info);
            }
        }
        return new AspectTargetInfos(infoList);
    }

    public void addAll(Set<AspectTargetInfo> aspectTargetInfoSet) {
        for (AspectTargetInfo info : aspectTargetInfoSet) {
            labelToAspectTargetInfo.put(info.getLabelPath(), info);
        }
    }

    public AspectTargetInfo lookupByLabel(String label) {
        return labelToAspectTargetInfo.get(label);
    }

    public Collection<AspectTargetInfo> lookupByTargetKind(Set<BazelTargetKind> requestedTargetKinds) {
        List<AspectTargetInfo> matchedTargetInfos = new ArrayList<>();

        for (AspectTargetInfo aspectTargetInfo : labelToAspectTargetInfo.values()) {
            String aspectKindStr = aspectTargetInfo.getKind();
            BazelTargetKind aspectKind = BazelTargetKind.valueOfIgnoresCase(aspectKindStr);
            if (aspectKind != null) {
                if (requestedTargetKinds.contains(aspectKind)) {
                    matchedTargetInfos.add(aspectTargetInfo);
                }
            } else {
                System.err.println("AspectTargetInfo " + aspectTargetInfo.getLabelPath() + " has an unknown kind: "
                        + aspectTargetInfo.getKind());
            }
        }
        return matchedTargetInfos;
    }

    public Collection<AspectTargetInfo> lookupByTargetKind(BazelTargetKind... requestedTargetKinds) {
        List<BazelTargetKind> requestedTargetKindsList = Arrays.asList(requestedTargetKinds);
        List<AspectTargetInfo> aspectTargetInfos = new ArrayList<>();
        for (AspectTargetInfo aspectTargetInfo : labelToAspectTargetInfo.values()) {
            if (requestedTargetKindsList.contains(BazelTargetKind.valueOfIgnoresCase(aspectTargetInfo.getKind()))) {
                aspectTargetInfos.add(aspectTargetInfo);
            }
        }
        return aspectTargetInfos;
    }

    /**
     * Returns all AspectTargetInfo instances that have one or more matching root source path.
     */
    public Collection<AspectTargetInfo> lookupByRootSourcePath(String rootSourcePath) {
        List<AspectTargetInfo> aspectTargetInfos = new ArrayList<>();
        Path rootSourcePathP = Paths.get(rootSourcePath);
        for (AspectTargetInfo aspectTargetInfo : labelToAspectTargetInfo.values()) {
            for (String sourcePath : aspectTargetInfo.getSources()) {
                if (Paths.get(sourcePath).startsWith(rootSourcePathP)) {
                    assertAllSourcesHaveSameRootPath(rootSourcePathP, aspectTargetInfo);
                    aspectTargetInfos.add(aspectTargetInfo);
                    break;
                }
            }
        }
        return aspectTargetInfos;
    }

    public Iterable<AspectTargetInfo> getTargetInfos() {
        return labelToAspectTargetInfo.values();
    }

    private static void assertAllSourcesHaveSameRootPath(Path rootSourcePath, AspectTargetInfo aspectTargetInfo) {
        for (String sourcePath : aspectTargetInfo.getSources()) {
            if (!Paths.get(sourcePath).startsWith(rootSourcePath)) {
                throw new IllegalStateException("AspectTargetInfo " + aspectTargetInfo.getLabelPath()
                        + " has sources that are not under " + rootSourcePath + ": " + aspectTargetInfo.getSources());
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String label : labelToAspectTargetInfo.keySet()) {
            sb.append(label).append("->").append(labelToAspectTargetInfo.get(label).getSources()).append(", ");

        }
        return sb.toString();
    }
}
