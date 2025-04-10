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
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.newOutputStream;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

import com.google.common.collect.ImmutableSortedSet;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.protobuf.TextFormat;
import com.salesforce.bazel.sdk.BazelVersion;

/**
 * Helper for deploying the IntelliJ Aspects into a Bazel Workspace
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

    public static final String OUTPUT_GROUP_JAVA_RUNTIME_CLASSPATH = "runtime_classpath";

    public static final String ASPECTS_VERSION = "1e99c4";
    public static final Predicate<String> ASPECT_OUTPUT_FILE_PREDICATE = str -> str.endsWith(".intellij-info.txt");

    private static String getLanguageSuffix(LanguageClass language) {
        var group = LanguageOutputGroup.forLanguage(language);
        return group != null ? group.suffix : null;
    }

    /** {@code .ij_aspects} */
    String FILE_NAME_DOT_INTELLIJ_ASPECTS = ".ij_aspects";

    String ASPECT_TEMPLATE_DIRECTORY = "template";

    private final Path aspectsDirectory;

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
        aspectsDirectory = baseDirectory.resolve(format("%s", ASPECTS_VERSION)).normalize();
    }

    private boolean allowDirectDepsTrimming(LanguageClass language) {
        return (language != LanguageClass.C) && (language != LanguageClass.GO);
    }

    /**
     * Copies the aspects into a Bazel workspace.
     *
     * @param bazelVersion
     *            the Bazel version
     * @param workspaceRoot
     *            the workspace root
     */
    public void copyIntoWorkspace(Path workspaceRoot, BazelVersion bazelVersion) throws IOException {
        // make sure we have it in the state directory
        makeAvailable();

        // target directory for the aspects
        var targetDirectory = workspaceRoot.resolve(getPathToAspectPackageInWorkspace(bazelVersion).relativePath());
        if (isDirectory(targetDirectory) && isRegularFile(targetDirectory.resolve("BUILD.bazel"))) {
            // already deployed
            return;
        }

        // copy all default files
        var filesToCopy = readAllLines(getAspectsDirectory().resolve("default/manifest"));
        for (String file : filesToCopy) {
            // make sure the file is relative
            if (file.startsWith("/")) {
                file = file.substring(1);
            }
            var target = targetDirectory.resolve(file);
            createDirectories(target.getParent());
            copy(getAspectsDirectory().resolve("default").resolve(file), target, REPLACE_EXISTING);
        }

        // copy templates
        var templateOptions = getTemplateOptions(bazelVersion);
        writeTemplateToWorkspace(
            targetDirectory,
            "java_info.bzl",
            ASPECT_TEMPLATE_DIRECTORY,
            "java_info.template.bzl",
            templateOptions);
        writeTemplateToWorkspace(
            targetDirectory,
            "python_info.bzl",
            ASPECT_TEMPLATE_DIRECTORY,
            "python_info.template.bzl",
            templateOptions);
        writeTemplateToWorkspace(
            targetDirectory,
            "intellij_info_bundled.bzl",
            ASPECT_TEMPLATE_DIRECTORY,
            "intellij_info.template.bzl",
            templateOptions);
        writeTemplateToWorkspace(
            targetDirectory,
            "code_generator_info.bzl",
            ASPECT_TEMPLATE_DIRECTORY,
            "code_generator_info.template.bzl",
            templateOptions);
    }

    String getAspectsArchiveLocation() {
        return format("/aspects/aspects-%s.zip", ASPECTS_VERSION);
    }

    /**
     * @return path to the extracted aspect files in the state (cache) location
     */
    Path getAspectsDirectory() {
        return aspectsDirectory;
    }

    /**
     * A list of command line flags for enabling the aspects
     *
     * @param bazelVersion
     *            the Bazel version
     * @return list of flags to add to the command line (never <code>null</code>)
     */
    public List<String> getFlags(BazelVersion bazelVersion) {
        var labelToDeployedAspectPackage =
                format("//%s", getPathToAspectPackageInWorkspace(bazelVersion).relativePath());

        return List.of(
            format("--aspects=%s:intellij_info_bundled.bzl%%intellij_info_aspect", labelToDeployedAspectPackage),
            format("--aspects=%s:java_classpath.bzl%%java_classpath_aspect", labelToDeployedAspectPackage));
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
     * @param outputGroup
     *            the output groups to request (usually {@link OutputGroup#INFO} and {@link OutputGroup#RESOLVE}
     *            together or only {@link OutputGroup#COMPILE})
     * @param languages
     *            the set of languages to obtain information for (as configured in the project view)
     * @param directDepsOnly
     *            should be set to <code>true</code> when the project view specifies
     *            <code>derive_targets_from_directories</code> (see
     *            https://github.com/bazelbuild/intellij/blob/37813e607ad26716c4d1ccf4bc3e7163b2188658/base/src/com/google/idea/blaze/base/sync/aspects/BlazeIdeInterfaceAspectsImpl.java#L724)
     * @return list of output group names created by the aspect
     */
    public Collection<String> getOutputGroupNames(Set<OutputGroup> outputGroups, Set<LanguageClass> languages,
            boolean directDepsOnly) {
        ImmutableSortedSet.Builder<String> outputGroupsBuilder = ImmutableSortedSet.naturalOrder();
        for (OutputGroup outputGroup : outputGroups) {
            if (OutputGroup.INFO.equals(outputGroup)) {
                outputGroupsBuilder.add(outputGroup.prefix + "generic");
            }
            languages.stream()
                    .map(l -> getOutputGroupForLanguage(outputGroup, l, directDepsOnly))
                    .filter(Objects::nonNull)
                    .forEach(outputGroupsBuilder::add);
        }
        return outputGroupsBuilder.build();
    }

    private WorkspacePath getPathToAspectPackageInWorkspace(BazelVersion bazelVersion) {
        return new WorkspacePath(format(".eclipse/.intellij-aspects/%s-%s", ASPECTS_VERSION, bazelVersion));
    }

    private Map<String, String> getTemplateOptions(BazelVersion bazelVersion) {
        // https://github.com/bazelbuild/intellij/blob/e756686d2082be4fc2b3077321899c754722ab75/base/src/com/google/idea/blaze/base/sync/aspects/storage/AspectTemplateWriter.kt#L100

        var isAtLeastBazel8 = bazelVersion.isAtLeast(8, 0, 0);
        var isAtLeastBazel9 = bazelVersion.isAtLeast(9, 0, 0);

        return Map.of(
            "bazel8OrAbove",
            isAtLeastBazel8 ? "true" : "false",
            "bazel9OrAbove",
            isAtLeastBazel9 ? "true" : "false",
            "isJavaEnabled",
            "true",
            "isPythonEnabled",
            "false");
    }

    /**
     * Makes the aspects repository available in the state (cache) directory.
     * <p>
     * Does nothing if the directory already exists.
     * </p>
     *
     * @throws IOException
     */
    void makeAvailable() throws IOException {
        var directory = getAspectsDirectory();
        if (isDirectory(directory) && isRegularFile(directory.resolve("default/manifest"))) {
            // already deployed
            return;
        }

        // unzip
        try (var in =
                new ZipInputStream(requireNonNull(openAspectsArchiveStream(), "no input stream for aspects archive"))) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null;) {
                if (entry.getName().equals("/")) {
                    continue; // skip the root entry
                }
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
                        	    cd ./bundles/com.salesforce.bazel.sdk/aspects/import%n\
                        	    ./import-and-build.sh%n\
                        	%n\
                        	%n""",
                        archiveLocation // @formatter:on
                    ));
        }
        return stream;
    }

    public TargetIdeInfo readAspectFile(BlazeArtifact artifact) throws IOException {
        try (var inputStream = artifact.getInputStream()) {
            var builder = IntellijIdeInfo.TargetIdeInfo.newBuilder();
            var parser = TextFormat.Parser.newBuilder().setAllowUnknownFields(true).build();
            parser.merge(new InputStreamReader(inputStream, UTF_8), builder);
            return builder.build();
        }
    }

    @Override
    public String toString() {
        return format("IntelliJAspects(%s)", aspectsDirectory);
    }

    void writeTemplateToWorkspace(Path destinationDirectory, String destinationFile, String templateDirectory,
            String templateFile, Map<String, ?> options) throws IOException {
        // https://github.com/bazelbuild/intellij/blob/e756686d2082be4fc2b3077321899c754722ab75/base/src/com/google/idea/blaze/base/util/TemplateWriter.java#L18

        final var template = getAspectsDirectory().resolve(templateDirectory).resolve(templateFile);

        createDirectories(destinationDirectory);
        final var dstStream = newOutputStream(destinationDirectory.resolve(destinationFile), CREATE, TRUNCATE_EXISTING);

        final var ctx = new VelocityContext();
        options.forEach(ctx::put);

        try (final var writer = new OutputStreamWriter(dstStream)) {
            try (final var reader = Files.newBufferedReader(template)) {
                final var success = Velocity.evaluate(ctx, writer, templateFile, reader);

                if (!success) {
                    throw new IOException("Failed to evaluate template: " + templateFile);
                }
            }
        }
    }
}
