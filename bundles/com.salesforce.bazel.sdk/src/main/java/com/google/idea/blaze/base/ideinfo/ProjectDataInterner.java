/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.ideinfo;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Label;

/**
 * Utility class to intern frequently duplicated objects in the project data.
 *
 * <p>
 * The underlying interners are application-wide, not specific to a project.
 */
public final class ProjectDataInterner {
    private static class Impl implements State {
        private final Interner<Label> labelInterner = Interners.newWeakInterner();
        private final Interner<String> stringInterner = Interners.newWeakInterner();
        private final Interner<TargetKey> targetKeyInterner = Interners.newWeakInterner();
        private final Interner<Dependency> dependencyInterner = Interners.newWeakInterner();
        private final Interner<ArtifactLocation> artifactLocationInterner = Interners.newWeakInterner();
        private final Interner<ExecutionRootPath> executionRootPathInterner = Interners.newWeakInterner();
        private final Interner<LibraryArtifact> libraryArtifactInterner = Interners.newWeakInterner();

        @Override
        public ArtifactLocation doIntern(ArtifactLocation artifactLocation) {
            return artifactLocationInterner.intern(artifactLocation);
        }

        @Override
        public Dependency doIntern(Dependency dependency) {
            return dependencyInterner.intern(dependency);
        }

        @Override
        public ExecutionRootPath doIntern(ExecutionRootPath executionRootPath) {
            return executionRootPathInterner.intern(executionRootPath);
        }

        @Override
        public Label doIntern(Label label) {
            return labelInterner.intern(label);
        }

        @Override
        public LibraryArtifact doIntern(LibraryArtifact libraryArtifact) {
            return libraryArtifactInterner.intern(libraryArtifact);
        }

        @Override
        public String doIntern(String string) {
            return stringInterner.intern(string);
        }

        @Override
        public TargetKey doIntern(TargetKey targetKey) {
            return targetKeyInterner.intern(targetKey);
        }
    }

    private static class NoOp implements State {
        @Override
        public ArtifactLocation doIntern(ArtifactLocation artifactLocation) {
            return artifactLocation;
        }

        @Override
        public Dependency doIntern(Dependency dependency) {
            return dependency;
        }

        @Override
        public ExecutionRootPath doIntern(ExecutionRootPath executionRootPath) {
            return executionRootPath;
        }

        @Override
        public Label doIntern(Label label) {
            return label;
        }

        @Override
        public LibraryArtifact doIntern(LibraryArtifact libraryArtifact) {
            return libraryArtifact;
        }

        @Override
        public String doIntern(String string) {
            return string;
        }

        @Override
        public TargetKey doIntern(TargetKey targetKey) {
            return targetKey;
        }
    }

    private interface State {
        ArtifactLocation doIntern(ArtifactLocation artifactLocation);

        Dependency doIntern(Dependency dependency);

        ExecutionRootPath doIntern(ExecutionRootPath executionRootPath);

        Label doIntern(Label label);

        LibraryArtifact doIntern(LibraryArtifact libraryArtifact);

        String doIntern(String string);

        TargetKey doIntern(TargetKey targetKey);
    }

    private static volatile State state = useInterner() ? new Impl() : new NoOp();

    static ArtifactLocation intern(ArtifactLocation artifactLocation) {
        return state.doIntern(artifactLocation);
    }

    static Dependency intern(Dependency dependency) {
        return state.doIntern(dependency);
    }

    public static ExecutionRootPath intern(ExecutionRootPath executionRootPath) {
        return state.doIntern(executionRootPath);
    }

    public static Label intern(Label label) {
        return state.doIntern(label);
    }

    static LibraryArtifact intern(LibraryArtifact libraryArtifact) {
        return state.doIntern(libraryArtifact);
    }

    static String intern(String string) {
        return state.doIntern(string);
    }

    static TargetKey intern(TargetKey targetKey) {
        return state.doIntern(targetKey);
    }

    private static boolean useInterner() {
        return Boolean.parseBoolean(System.getProperty("intern.project.data", "true"));
    }
}
