package com.salesforce.bazel.eclipse.core.projectview;

import static java.lang.String.format;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.readString;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.idea.blaze.base.model.primitives.InvalidTargetException;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;

/**
 * A reader for <code>.bazelproject</code> files.
 * <p>
 * The reader provides access to the content of <code>.bazelproject</code>.
 * </p>
 * <p>
 * Note, the <code>.bazelproject</code> file content is not straightforward yaml. It's some obscure IntelliJ file format
 * created by JetBrains. We found an Apache 2.0 licensed Kotlin parser we could take the parsing from. See <a href=
 * "https://github.com/JetBrains/bazel-bsp/blob/730061f6a9d3fe42311fc95049b86dd5ca4c56b7/executioncontext/projectview/src/main/java/org/jetbrains/bsp/bazel/projectview/parser/DefaultProjectViewParser.kt">DefaultProjectViewParser.kt</a>
 * for details.
 * </p>
 */
public class BazelProjectFileReader {

    static class BazelProjectViewBuilder {

        class ImportHandle implements AutoCloseable {

            private final Path bazelProjectFile;

            public ImportHandle(Path bazelProjectFile) {
                this.bazelProjectFile = bazelProjectFile;
            }

            @Override
            public void close() {
                importingFiles.remove(bazelProjectFile);
            }

        }

        String workspaceType;
        final LinkedHashSet<String> directories = new LinkedHashSet<>();
        final LinkedHashSet<String> targets = new LinkedHashSet<>();
        boolean deriveTargetsFromDirectories = false;
        LinkedHashSet<String> additionalLanguages = new LinkedHashSet<>();
        String javaLanguageLevel;
        LinkedHashSet<String> tsConfigRules = new LinkedHashSet<>();
        String targetDiscoveryStrategy, targetProvisioningStrategy;
        final LinkedHashSet<Path> importingFiles = new LinkedHashSet<>();
        IPath bazelBinary;
        final LinkedHashMap<String, String> targetProvisioningSettings = new LinkedHashMap<>();
        final LinkedHashMap<String, String> projectMappings = new LinkedHashMap<>();
        final LinkedHashSet<String> importPreferences = new LinkedHashSet<>();
        final LinkedHashSet<String> projectSettings = new LinkedHashSet<>();
        final LinkedHashSet<String> buildFlags = new LinkedHashSet<>();
        final LinkedHashSet<String> syncFlags = new LinkedHashSet<>();
        final LinkedHashSet<String> testFlags = new LinkedHashSet<>();
        final LinkedHashSet<String> testSourcesGlobs = new LinkedHashSet<>();
        boolean discoverAllExternalAndWorkspaceJars = false;
        final LinkedHashSet<String> externalJarsFilters = new LinkedHashSet<>();
        int targetShardSize = 500;
        boolean shardSync = true;
        int importDepth = 0;

        public BazelProjectView build() throws IllegalStateException {
            // check mandatory parameters
            if (directories.isEmpty()) {
                throw new IllegalStateException("no directories specified; this is a required section");
            }
            if (!deriveTargetsFromDirectories && targets.isEmpty()) {
                throw new IllegalStateException(
                        "no targets specified; this is a required section unless derive_targets_from_directories is set");
            }

            List<WorkspacePath> directoriesToImport = new ArrayList<>();
            List<WorkspacePath> directoriesToExclude = new ArrayList<>();
            for (String d : directories) {
                if (d.startsWith(EXCLUDED_ENTRY_PREFIX)) {
                    directoriesToExclude.add(new WorkspacePath(d.substring(EXCLUDED_ENTRY_PREFIX.length())));
                } else {
                    directoriesToImport.add(new WorkspacePath(d));
                }
            }
            if (directoriesToImport.isEmpty()) {
                throw new IllegalStateException("directories contains only excludes; at least one include is required");
            }

            List<TargetExpression> targetsList = new ArrayList<>();
            for (String t : targets) {
                try {
                    targetsList.add(TargetExpression.fromString(t));
                } catch (InvalidTargetException e) {
                    new IllegalStateException(e.getMessage(), e);
                }
            }
            if (!deriveTargetsFromDirectories && !targetsList.stream().anyMatch(not(TargetExpression::isExcluded))) {
                throw new IllegalStateException(
                        "at least one target to include is required unless derive_targets_from_directories is set");
            }

            List<WorkspacePath> preferencesToImport = new ArrayList<>();
            for (String epfFile : importPreferences) {
                preferencesToImport.add(new WorkspacePath(epfFile));
            }

            List<WorkspacePath> projectSettingsToSync = new ArrayList<>();
            for (String prefsFile : projectSettings) {
                projectSettingsToSync.add(new WorkspacePath(prefsFile));
            }

            return new BazelProjectView(
                    directoriesToImport,
                    directoriesToExclude,
                    targetsList,
                    deriveTargetsFromDirectories,
                    workspaceType,
                    additionalLanguages,
                    javaLanguageLevel,
                    tsConfigRules,
                    bazelBinary,
                    targetDiscoveryStrategy,
                    targetProvisioningStrategy,
                    targetProvisioningSettings,
                    projectMappings,
                    preferencesToImport,
                    projectSettingsToSync,
                    buildFlags,
                    syncFlags,
                    testFlags,
                    new GlobSetMatcher(testSourcesGlobs),
                    discoverAllExternalAndWorkspaceJars,
                    new GlobSetMatcher(externalJarsFilters),
                    shardSync,
                    targetShardSize,
                    importDepth);
        }

        public ImportHandle startImporting(Path bazelProjectViewFile) throws IOException {
            if (!importingFiles.add(bazelProjectViewFile)) {
                throw new IOException(
                        format(
                            "Recursive import detected for file '%s'%n%s",
                            bazelProjectViewFile,
                            importingFiles.stream()
                                    .map(Path::toString)
                                    .collect(joining(System.lineSeparator() + "-> ", "-> ", ""))));
            }
            return new ImportHandle(bazelProjectViewFile);
        }
    }

    private static Logger LOG = LoggerFactory.getLogger(BazelProjectFileReader.BazelProjectViewBuilder.class);

    private static final Pattern SECTION_HEADER_REGEX = Pattern.compile("((^[^:\\-/*\\s]+)([: ]))", Pattern.MULTILINE);
    private static final Pattern WHITESPACE_CHAR_REGEX = Pattern.compile("\\s+");
    private static final String COMMENT_LINE_REGEX = "#(.)*(\\n|\\z)";
    private static final String EXCLUDED_ENTRY_PREFIX = "-";

    private final Path bazelProjectFile;
    private final Path bazelWorkspaceRoot;

    /**
     * @param bazelProjectFile
     *            the project file to read
     * @param bazelWorkspaceRoot
     *            the workspace root (for resolving imports)
     */
    public BazelProjectFileReader(Path bazelProjectFile, Path bazelWorkspaceRoot) {
        this.bazelProjectFile = bazelProjectFile;
        this.bazelWorkspaceRoot = bazelWorkspaceRoot;
    }

    /**
     * Parses the content of a project file into the given builder.
     * <p>
     * <a href="https://ij.bazel.build/docs/project-views.html">Spec</a> is followed as best as possible.
     * </p>
     *
     * @param bazelProjectFile
     *            the file to parse
     * @param builder
     *            the builder to populate values
     * @throws IOException
     */
    private void parseProjectFile(Path bazelProjectFile, BazelProjectViewBuilder builder) throws IOException {
        // set context
        try (var importHandle = builder.startImporting(bazelProjectFile)) {
            // read file
            var bazelProjectFileContent = readString(bazelProjectFile);

            // remove comments
            bazelProjectFileContent = removeComments(bazelProjectFileContent);

            // parse into section
            var rawSections = parseRawSections(bazelProjectFileContent);

            // build the model
            // note: we go over each section one by one to ensure imports are properly handled
            // lists compose; single values last one wins
            for (RawSection rawSection : rawSections) {
                switch (rawSection.getName()) {
                    case "directories": {
                        parseSectionBodyIntoList(rawSection).forEach(builder.directories::add);
                        break;
                    }
                    case "targets": {
                        parseSectionBodyIntoList(rawSection).forEach(builder.targets::add);
                        break;
                    }
                    case "derive_targets_from_directories": {
                        builder.deriveTargetsFromDirectories =
                                parseSectionBodyAsBoolean(rawSection, builder.deriveTargetsFromDirectories);
                        break;
                    }
                    case "import": {
                        Path fileToImport;
                        try {
                            fileToImport = rawSection.getBodyAsPath();
                        } catch (NullPointerException e) {
                            throw new IOException(
                                    format("Invalid syntax in '%s': import needs a value!", bazelProjectFile),
                                    e);
                        }
                        if (fileToImport.isAbsolute()) {
                            throw new IOException(
                                    format(
                                        "Invalid import (%s) defined in '%s': imports must not be absolute. They need to be relative to the workspace root.",
                                        fileToImport,
                                        bazelProjectFile));
                        }
                        var resolvedPathOfFileToImport = bazelWorkspaceRoot.resolve(fileToImport);
                        if (!isRegularFile(resolvedPathOfFileToImport)) {
                            LOG.warn(
                                "Import '{}' in project view '{}' cannot be found. Skipping. Some projects might be missing.",
                                fileToImport,
                                bazelProjectFile);
                            break;
                        }
                        try {
                            // parse the import into the existing builder (this allows to implement the wanted behavior)
                            parseProjectFile(resolvedPathOfFileToImport, builder);
                        } catch (NoSuchFileException e) {
                            throw new NoSuchFileException(
                                    resolvedPathOfFileToImport.toString(),
                                    bazelProjectFile.toString(),
                                    format(
                                        "import '%s' not found (defined in '%s')",
                                        resolvedPathOfFileToImport,
                                        bazelProjectFile));
                        }
                        break;
                    }
                    case "workspace_type": {
                        try {
                            builder.workspaceType = rawSection.getBodyAsSingleValue();
                        } catch (NullPointerException e) {
                            throw new IOException(
                                    format("Invalid syntax in '%s': workspace_type needs a value!", bazelProjectFile),
                                    e);
                        }
                        break;
                    }
                    case "additional_languages": {
                        parseSectionBodyIntoList(rawSection).forEach(builder.additionalLanguages::add);
                        break;
                    }
                    case "java_language_level": {
                        try {
                            builder.javaLanguageLevel = rawSection.getBodyAsSingleValue();
                        } catch (NullPointerException e) {
                            throw new IOException(
                                    format(
                                        "Invalid syntax in '%s': java_language_level needs a value!",
                                        bazelProjectFile),
                                    e);
                        }
                        break;
                    }
                    case "ts_config_rules": {
                        parseSectionBodyIntoList(rawSection).forEach(builder.tsConfigRules::add);
                        break;
                    }
                    case "bazel_binary": {
                        try {
                            builder.bazelBinary = IPath.forPosix(rawSection.getBodyAsSingleValue());
                        } catch (NullPointerException e) {
                            throw new IOException(
                                    format("Invalid syntax in '%s': bazel_binary needs a value!", bazelProjectFile),
                                    e);
                        }
                        break;
                    }
                    case "project_mappings": {
                        parseSectionBodyIntoList(rawSection).forEach(s -> {
                            var separator = s.indexOf('=');
                            if (separator != -1) {
                                var key = s.substring(0, separator).trim();
                                var value = s.substring(separator + 1).trim();
                                builder.projectMappings.put(key, value);
                            }
                        });
                        break;
                    }
                    case "target_discovery_strategy": {
                        // extension for BEF
                        try {
                            builder.targetDiscoveryStrategy = rawSection.getBodyAsSingleValue();
                        } catch (NullPointerException e) {
                            throw new IOException(
                                    format(
                                        "Invalid syntax in '%s': target_discovery_strategy needs a value!",
                                        bazelProjectFile),
                                    e);
                        }
                        break;
                    }
                    case "target_provisioning_strategy": {
                        // extension for BEF
                        try {
                            builder.targetProvisioningStrategy = rawSection.getBodyAsSingleValue();
                        } catch (NullPointerException e) {
                            throw new IOException(
                                    format(
                                        "Invalid syntax in '%s': target_provisioning_strategy needs a value!",
                                        bazelProjectFile),
                                    e);
                        }
                        break;
                    }
                    case "target_provisioning_settings": {
                        // extension for BEF
                        parseSectionBodyIntoList(rawSection).forEach(s -> {
                            var separator = s.indexOf('=');
                            if (separator != -1) {
                                var key = s.substring(0, separator).trim();
                                var value = s.substring(separator + 1).trim();
                                builder.targetProvisioningSettings.put(key, value);
                            }
                        });
                        break;
                    }
                    case "import_preferences": {
                        // extension for BEF
                        parseSectionBodyIntoList(rawSection).forEach(builder.importPreferences::add);
                        break;
                    }
                    case "project_settings": {
                        // extension for BEF
                        parseSectionBodyIntoList(rawSection).forEach(builder.projectSettings::add);
                        break;
                    }
                    case "build_flags": {
                        parseSectionBodyIntoList(rawSection).forEach(builder.buildFlags::add);
                        break;
                    }
                    case "sync_flags": {
                        parseSectionBodyIntoList(rawSection).forEach(builder.syncFlags::add);
                        break;
                    }
                    case "test_flags": {
                        parseSectionBodyIntoList(rawSection).forEach(builder.testFlags::add);
                        break;
                    }
                    case "test_sources": {
                        parseSectionBodyIntoList(rawSection).forEach(builder.testSourcesGlobs::add);
                        break;
                    }
                    case "discover_all_external_and_workspace_jars": {
                        // extension for BEF
                        builder.discoverAllExternalAndWorkspaceJars =
                                parseSectionBodyAsBoolean(rawSection, builder.discoverAllExternalAndWorkspaceJars);
                        break;
                    }
                    case "external_jars_discovery_filters": {
                        // extension for BEF
                        parseSectionBodyIntoList(rawSection).forEach(builder.externalJarsFilters::add);
                        break;
                    }
                    case "shard_sync": {
                        builder.shardSync = parseSectionBodyAsBoolean(rawSection, builder.shardSync);
                        break;
                    }
                    case "target_shard_size": {
                        builder.targetShardSize = parseSectionBodyAsInt(rawSection, builder.targetShardSize);
                        break;
                    }
                    case "import_target_output":
                    case "exclude_target": {
                        // ignore deprecated
                        break;
                    }
                    case "import_depth": {
                        builder.importDepth = parseSectionBodyAsInt(rawSection, builder.importDepth);
                        break;
                    }
                    default:
                        LOG.warn("Unexpected section '{}' while reading '{}'", rawSection.getName(), bazelProjectFile);
                        break;
                }
            }
        }
    }

    private List<RawSection> parseRawSections(String bazelProjectFileContent) throws IOException {
        List<RawSection> results = new ArrayList<>();
        var headers = SECTION_HEADER_REGEX.matcher(bazelProjectFileContent)
                .results()
                .map(t -> t.group(2))
                .toArray(String[]::new);
        var bodies = SECTION_HEADER_REGEX.split(bazelProjectFileContent);
        if (headers.length != (bodies.length - 1)) {
            throw new IOException(
                    format(
                        "Syntax error in .bazelproject: The number of section headers doesn't match the number of section bodies (%d != %d; header: %s).",
                        headers.length,
                        bodies.length,
                        Stream.of(headers).collect(joining(", "))));
        }

        for (var i = 0; i < headers.length; i++) {
            results.add(new RawSection(headers[i], bodies[i + 1].trim()));
        }
        return results;

    }

    private boolean parseSectionBodyAsBoolean(RawSection rawSection, boolean defaultValue) {
        var rawBody = rawSection.getRawBody();
        if ((rawBody != null) && !rawBody.isBlank()) {
            return Boolean.parseBoolean(rawBody.trim());
        }
        return defaultValue;
    }

    private int parseSectionBodyAsInt(RawSection rawSection, int defaultValue) {
        var rawBody = rawSection.getRawBody();
        if ((rawBody != null) && !rawBody.isBlank()) {
            try {
                return Integer.parseInt(rawBody.trim());
            } catch (NumberFormatException e) {
                LOG.warn(
                    "Invalid integer for section '{}' (falling back to default {}): {}",
                    rawSection.getName(),
                    defaultValue,
                    e.getMessage(),
                    e);
            }
        }
        return defaultValue;
    }

    private Stream<String> parseSectionBodyIntoList(RawSection rawSection) {
        var rawBody = rawSection.getRawBody();
        if (rawBody == null) {
            return Stream.empty();
        }

        return WHITESPACE_CHAR_REGEX.splitAsStream(rawBody).filter(not(String::isBlank));
    }

    /**
     * Reads and parse the file.
     *
     * @return the read {@link BazelProjectView}
     * @throws IOException
     *             in case of errors reading, parsing or validating the file
     */
    public BazelProjectView read() throws IOException {
        var projectView = new BazelProjectViewBuilder();
        parseProjectFile(bazelProjectFile, projectView);
        try {
            return projectView.build();
        } catch (IllegalStateException e) {
            throw new IOException(format("Invalid syntax in '%s': %s", bazelProjectFile, e.getMessage()), e);
        }
    }

    private String removeComments(String bazelProjectFileContent) {
        return bazelProjectFileContent.replaceAll(COMMENT_LINE_REGEX, "\n");
    }

}