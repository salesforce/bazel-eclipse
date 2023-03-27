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
package com.salesforce.bazel.sdk.aspects.intellij;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.common.collect.ImmutableSortedSet;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.protobuf.TextFormat;
import com.salesforce.bazel.sdk.BazelVersion;
import com.salesforce.bazel.sdk.primitives.LanguageClass;

/**
 * Helper for deploying the IntelliJ Aspects
 */
public class IntellijAspects {

    /** A Blaze output group (prefix) created by the aspect. */
    public enum OutputGroup {
        INFO("intellij-info-"), RESOLVE("intellij-resolve-"), COMPILE("intellij-compile-");

        public final String prefix;

        OutputGroup(String prefix) {
            this.prefix = prefix;
        }

        public boolean isPrefixOf(String outputGroupName) {
            return outputGroupName.startsWith(prefix);
        }
    }

    public static final String ASPECTS_VERSION = "37813e";

    public static final String OVERRIDE_REPOSITORY_FLAG = "--override_repository=intellij_aspect";

    public static final Predicate<String> ASPECT_OUTPUT_FILE_PREDICATE = str -> str.endsWith(".intellij-info.txt");

    private static String getLanguageSuffix(LanguageClass language) {
        var group = LanguageOutputGroup.forLanguage(language);
        return group != null ? group.suffix : null;
    }

    private final Path directory;

    /**
     * Creates a new helper using the given base directory.
     * <p>
     * The helper will use a sub directory for the specific version of the aspects.
     * </p>
     *
     * @param baseDirectory
     *            base directory for deploying the aspects
     */
    public IntellijAspects(Path baseDirectory) {
        directory = baseDirectory.resolve(format(".%s", ASPECTS_VERSION)).normalize();
    }

    private boolean allowDirectDepsTrimming(LanguageClass language) {
        return (language != LanguageClass.C) && (language != LanguageClass.GO);
    }

    private String getAspectFlag(BazelVersion bazelVersion) {
        if (bazelVersion.isAtLeast(6, 0, 0)) {
            return "--aspects=@@intellij_aspect//:intellij_info_bundled.bzl%intellij_info_aspect";
        }
        return "--aspects=@intellij_aspect//:intellij_info_bundled.bzl%intellij_info_aspect";
    }

    protected String getAspectsArchiveLocation() {
        return format("/aspects/aspects-%s.zip", ASPECTS_VERSION);
    }

    /**
     * @return path to the aspects to be used
     */
    public Path getDirectory() {
        return directory;
    }

    /**
     * A list of command line flags for enabling the aspects
     *
     * @param bazelVersion
     *            the bazel version
     * @return list of flags to add to the command line (never <code>null</code>)
     */
    public List<String> getFlags(BazelVersion bazelVersion) {
        return List.of(getAspectFlag(bazelVersion), format("%s=%s", OVERRIDE_REPOSITORY_FLAG, directory));
    }

    private String getOutputGroupForLanguage(OutputGroup group, LanguageClass language, boolean directDepsOnly) {
        var langSuffix = getLanguageSuffix(language);
        if (langSuffix == null) {
            return null;
        }
        directDepsOnly = directDepsOnly && allowDirectDepsTrimming(language);
        if (!directDepsOnly) {
            return group.prefix + langSuffix;
        }
        return group.prefix + langSuffix + "-direct-deps";
    }

    /**
     * Collects the names of output groups created by the aspect for the given {@link OutputGroup} and languages.
     *
     * @param outputGroups
     *            list of requested {@link OutputGroup} for each language
     * @param languages
     *            list of requested languages
     * @param directDepsOnly
     *            <code>true</code> if only direct dependencies are requested, <code>false</code> otherwise
     * @return list of output group names created by the aspect
     */
    public Collection<String> getOutputGroupNames(Set<OutputGroup> outputGroups, Set<LanguageClass> languages,
            boolean directDepsOnly) {
        ImmutableSortedSet.Builder<String> outputGroupsBuilder = ImmutableSortedSet.naturalOrder();
        for (OutputGroup outputGroup : outputGroups) {
            if (OutputGroup.INFO.equals(outputGroup)) {
                outputGroupsBuilder.add(outputGroup.prefix + "generic");
            }
            languages.stream().map(l -> getOutputGroupForLanguage(outputGroup, l, directDepsOnly))
                    .filter(Objects::nonNull).forEach(outputGroupsBuilder::add);
        }
        return outputGroupsBuilder.build();
    }

    /**
     * Makes the aspects repository available in the directory.
     * <p>
     * Does nothing if the directory already exists and looks like an aspects workspace.
     * </p>
     *
     * @throws IOException
     */
    public void makeAvailable() throws IOException {
        var directory = getDirectory();
        if (Files.isDirectory(directory) && Files.isRegularFile(directory.resolve("WORKSPACE"))) {
            // already deployed
            return;
        }

        // unzip
        try (var in =
                new ZipInputStream(requireNonNull(openAspectsArchiveStream(), "no input stream for aspects archive"))) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null;) {
                var resolvedPath = directory.resolve(entry.getName()).normalize();
                if (!resolvedPath.startsWith(directory)) {
                    // see: https://snyk.io/research/zip-slip-vulnerability
                    throw new IOException(format("Insecure ZIP entry '%s'", entry.getName()));
                }
                if (entry.isDirectory()) {
                    createDirectories(resolvedPath);
                } else {
                    createDirectories(resolvedPath.getParent());
                    copy(in, resolvedPath);
                }
            }
        }
    }

    protected InputStream openAspectsArchiveStream() throws IOException {
        var archiveLocation = requireNonNull(getAspectsArchiveLocation(), "no aspects archive location");
        var stream = getClass().getClassLoader().getResourceAsStream(archiveLocation);
        if (stream == null) {
            throw new IOException(
                    format( // @formatter:off
                        """
                        	Unable to find aspects archive '%s' on classpath.%n\
                        	Please ensure packaging is correct and/or override this method to provide a custom solution.%n\
                        	%n\
                        	Contributing to BEF? Setting up a dev environment?%n\
                        	%n\
                        	  Please run:%n\
                        	    cd ./bundles/com.salesforce.bazel-java-sdk/aspects/import%n\
                        	    ./import-and-build.sh%n\
                        	%n\
                        	%n""",
                        archiveLocation // @formatter:on
                    ));
        }
        return stream;
    }

    public TargetIdeInfo readAspectFile(Path path) throws IOException {
        try (var inputStream = Files.newInputStream(path)) {
            var builder = IntellijIdeInfo.TargetIdeInfo.newBuilder();
            var parser = TextFormat.Parser.newBuilder().setAllowUnknownFields(true).build();
            parser.merge(new InputStreamReader(inputStream, UTF_8), builder);
            return builder.build();
        }
    }

    @Override
    public String toString() {
        return format("IntelliJAspects(%s)", directory);
    }
}
