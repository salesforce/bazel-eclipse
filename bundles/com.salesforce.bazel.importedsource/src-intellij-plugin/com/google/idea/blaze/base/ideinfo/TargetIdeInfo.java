/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.Dependency.DependencyType;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;

/** Simple implementation of TargetIdeInfo. */
public final class TargetIdeInfo implements ProtoWrapper<IntellijIdeInfo.TargetIdeInfo> {
    /** Builder for rule ide info */
    public static class Builder {
        private TargetKey key;
        private Kind kind;
        private ArtifactLocation buildFile;
        private final ImmutableList.Builder<Dependency> dependencies = ImmutableList.builder();
        private final ImmutableList.Builder<String> tags = ImmutableList.builder();
        private final ImmutableSet.Builder<ArtifactLocation> sources = ImmutableSet.builder();
        private JavaIdeInfo javaIdeInfo;
        private TestIdeInfo testIdeInfo;
        private JavaToolchainIdeInfo javaToolchainIdeInfo;
        private Long syncTime;

        @CanIgnoreReturnValue
        public Builder addDependency(Label label) {
            this.dependencies.add(new Dependency(TargetKey.forPlainTarget(label), DependencyType.COMPILE_TIME));
            return this;
        }

        @CanIgnoreReturnValue
        public Builder addDependency(String s) {
            return addDependency(Label.create(s));
        }

        @CanIgnoreReturnValue
        public Builder addRuntimeDep(Label label) {
            this.dependencies.add(new Dependency(TargetKey.forPlainTarget(label), DependencyType.RUNTIME));
            return this;
        }

        @CanIgnoreReturnValue
        public Builder addRuntimeDep(String s) {
            return addRuntimeDep(Label.create(s));
        }

        @CanIgnoreReturnValue
        public Builder addSource(ArtifactLocation source) {
            this.sources.add(source);
            return this;
        }

        @CanIgnoreReturnValue
        public Builder addSource(ArtifactLocation.Builder source) {
            return addSource(source.build());
        }

        @CanIgnoreReturnValue
        public Builder addTag(String s) {
            this.tags.add(s);
            return this;
        }

        public TargetIdeInfo build() {
            return new TargetIdeInfo(key, kind, buildFile, dependencies.build(), tags.build(), sources.build(),
                    javaIdeInfo, testIdeInfo, javaToolchainIdeInfo, syncTime);
        }

        @CanIgnoreReturnValue
        public Builder setBuildFile(ArtifactLocation buildFile) {
            this.buildFile = buildFile;
            return this;
        }

        @CanIgnoreReturnValue
        public Builder setJavaInfo(JavaIdeInfo.Builder builder) {
            javaIdeInfo = builder.build();
            return this;
        }

        @CanIgnoreReturnValue
        public Builder setJavaToolchainIdeInfo(JavaToolchainIdeInfo.Builder javaToolchainIdeInfo) {
            this.javaToolchainIdeInfo = javaToolchainIdeInfo.build();
            return this;
        }

        @CanIgnoreReturnValue
        public Builder setKind(Kind kind) {
            this.kind = kind;
            return this;
        }

        @VisibleForTesting
        @CanIgnoreReturnValue
        public Builder setKind(String kindString) {
            var kind = Preconditions.checkNotNull(Kind.fromRuleName(kindString));
            return setKind(kind);
        }

        @CanIgnoreReturnValue
        public Builder setLabel(Label label) {
            this.key = TargetKey.forPlainTarget(label);
            return this;
        }

        @CanIgnoreReturnValue
        public Builder setLabel(String label) {
            return setLabel(Label.create(label));
        }

        @CanIgnoreReturnValue
        public Builder setSyncTime(@Nullable Instant syncTime) {
            this.syncTime = syncTime != null ? syncTime.toEpochMilli() : null;
            return this;
        }

        @CanIgnoreReturnValue
        public Builder setTestInfo(TestIdeInfo.Builder testInfo) {
            this.testIdeInfo = testInfo.build();
            return this;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    public static TargetIdeInfo fromProto(IntellijIdeInfo.TargetIdeInfo proto) {
        return fromProto(proto, /* syncTimeOverride= */ null);
    }

    @Nullable
    public static TargetIdeInfo fromProto(IntellijIdeInfo.TargetIdeInfo proto, @Nullable Instant syncTimeOverride) {
        var key = proto.hasKey() ? TargetKey.fromProto(proto.getKey()) : null;
        var kind = Kind.fromProto(proto);
        if ((key == null) || (kind == null)) {
            return null;
        }
        ImmutableSet.Builder<ArtifactLocation> sourcesBuilder = ImmutableSet.builder();
        JavaIdeInfo javaIdeInfo = null;
        if (proto.hasJavaIdeInfo()) {
            javaIdeInfo = JavaIdeInfo.fromProto(proto.getJavaIdeInfo());
            sourcesBuilder
                    .addAll(ProtoWrapper.map(proto.getJavaIdeInfo().getSourcesList(), ArtifactLocation::fromProto));
        }
        var syncTime = syncTimeOverride != null ? Long.valueOf(syncTimeOverride.toEpochMilli())
                : proto.getSyncTimeMillis() == 0 ? null : proto.getSyncTimeMillis();
        return new TargetIdeInfo(key, kind,
                proto.hasBuildFileArtifactLocation() ? ArtifactLocation.fromProto(proto.getBuildFileArtifactLocation())
                        : null,
                ProtoWrapper.map(proto.getDepsList(), Dependency::fromProto),
                ProtoWrapper.internStrings(proto.getTagsList()), sourcesBuilder.build(), javaIdeInfo,
                proto.hasTestInfo() ? TestIdeInfo.fromProto(proto.getTestInfo()) : null, proto.hasJavaToolchainIdeInfo()
                        ? JavaToolchainIdeInfo.fromProto(proto.getJavaToolchainIdeInfo()) : null,
                syncTime);
    }

    private final TargetKey key;
    private final Kind kind;
    @Nullable
    private final ArtifactLocation buildFile;
    private final ImmutableList<Dependency> dependencies;
    private final ImmutableList<String> tags;
    private final ImmutableSet<ArtifactLocation> sources;

    @Nullable
    private final JavaIdeInfo javaIdeInfo;

    @Nullable
    private final TestIdeInfo testIdeInfo;

    @Nullable
    private final JavaToolchainIdeInfo javaToolchainIdeInfo;

    @Nullable
    private final Long syncTimeMillis;

    private TargetIdeInfo(TargetKey key, Kind kind, @Nullable ArtifactLocation buildFile,
            ImmutableList<Dependency> dependencies, ImmutableList<String> tags, ImmutableSet<ArtifactLocation> sources,
            @Nullable JavaIdeInfo javaIdeInfo, @Nullable TestIdeInfo testIdeInfo,
            @Nullable JavaToolchainIdeInfo javaToolchainIdeInfo, @Nullable Long syncTimeMillis) {
        this.key = key;
        this.kind = kind;
        this.buildFile = buildFile;
        this.dependencies = dependencies;
        this.tags = tags;
        this.sources = sources;
        this.javaIdeInfo = javaIdeInfo;
        this.testIdeInfo = testIdeInfo;
        this.javaToolchainIdeInfo = javaToolchainIdeInfo;
        this.syncTimeMillis = syncTimeMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        var that = (TargetIdeInfo) o;
        return Objects.equals(key, that.key) && (kind == that.kind) && Objects.equals(buildFile, that.buildFile)
                && Objects.equals(dependencies, that.dependencies) && Objects.equals(tags, that.tags)
                && Objects.equals(javaIdeInfo, that.javaIdeInfo) && Objects.equals(testIdeInfo, that.testIdeInfo)
                && Objects.equals(javaToolchainIdeInfo, that.javaToolchainIdeInfo)
                && Objects.equals(syncTimeMillis, that.syncTimeMillis);
    }

    @Nullable
    public ArtifactLocation getBuildFile() {
        return buildFile;
    }

    public List<Dependency> getDependencies() {
        return dependencies;
    }

    @Nullable
    public JavaIdeInfo getJavaIdeInfo() {
        return javaIdeInfo;
    }

    @Nullable
    public JavaToolchainIdeInfo getJavaToolchainIdeInfo() {
        return javaToolchainIdeInfo;
    }

    public TargetKey getKey() {
        return key;
    }

    public Kind getKind() {
        return kind;
    }

    public ImmutableSet<ArtifactLocation> getSources() {
        return sources;
    }

    @Nullable
    public Instant getSyncTime() {
        return syncTimeMillis != null ? Instant.ofEpochMilli(syncTimeMillis) : null;
    }

    public ImmutableList<String> getTags() {
        return tags;
    }

    @Nullable
    public TestIdeInfo getTestIdeInfo() {
        return testIdeInfo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, kind, buildFile, dependencies, tags, sources, javaIdeInfo, testIdeInfo,
            javaToolchainIdeInfo, syncTimeMillis);
    }

    public boolean isPlainTarget() {
        return getKey().isPlainTarget();
    }

    /** Returns whether this rule is one of the kinds. */
    public boolean kindIsOneOf(Collection<Kind> kinds) {
        return kinds.contains(getKind());
    }

    /** Returns whether this rule is one of the kinds. */
    public boolean kindIsOneOf(Kind... kinds) {
        return kindIsOneOf(Arrays.asList(kinds));
    }

    @Override
    public IntellijIdeInfo.TargetIdeInfo toProto() {
        var builder =
                IntellijIdeInfo.TargetIdeInfo.newBuilder().setKey(key.toProto()).setKindString(kind.getKindString())
                        .addAllDeps(ProtoWrapper.mapToProtos(dependencies)).addAllTags(tags);
        ProtoWrapper.unwrapAndSetIfNotNull(builder::setBuildFileArtifactLocation, buildFile);
        ProtoWrapper.unwrapAndSetIfNotNull(builder::setJavaIdeInfo, javaIdeInfo);
        ProtoWrapper.unwrapAndSetIfNotNull(builder::setTestInfo, testIdeInfo);
        ProtoWrapper.unwrapAndSetIfNotNull(builder::setJavaToolchainIdeInfo, javaToolchainIdeInfo);
        ProtoWrapper.setIfNotNull(builder::setSyncTimeMillis, syncTimeMillis);
        return builder.build();
    }

    @Override
    public String toString() {
        return getKey().toString();
    }

    public TargetInfo toTargetInfo() {
        return TargetInfo.builder(getKey().getLabel(), getKind().getKindString())
                .setTestSize(getTestIdeInfo() != null ? getTestIdeInfo().getTestSize() : null)
                .setTestClass(getJavaIdeInfo() != null ? getJavaIdeInfo().getTestClass() : null)
                .setSyncTime(getSyncTime()).setSources(ImmutableList.copyOf(getSources())).build();
    }

    /**
     * Updates this target's {@link #syncTimeMillis}. Returns this same {@link TargetIdeInfo} instance if the sync time
     * is unchanged.
     */
    public TargetIdeInfo updateSyncTime(Instant syncTime) {
        var syncTimeMillis = syncTime.toEpochMilli();
        if (Objects.equals(syncTimeMillis, this.syncTimeMillis)) {
            return this;
        }
        return new TargetIdeInfo(key, kind, buildFile, dependencies, tags, sources, javaIdeInfo, testIdeInfo,
                javaToolchainIdeInfo, syncTimeMillis);
    }
}
