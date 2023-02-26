package com.salesforce.bazel.eclipse.core.projectview;

import static java.lang.String.format;
import static java.nio.file.Files.readString;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

        String workspaceType;
        final LinkedHashSet<String> directories = new LinkedHashSet<>();
        final LinkedHashSet<String> targets = new LinkedHashSet<>();
        boolean deriveTargetsFromDirectories = false;
        LinkedHashSet<String> additionalLanguages = new LinkedHashSet<>();
        String javaLanguageLevel;
        LinkedHashSet<String> tsConfigRules = new LinkedHashSet<>();
        String targetDiscoveryStrategy, targetProvisioningStrategy;

        public BazelProjectView build() throws IllegalStateException {
            // check mandatory parameters
            if (directories.isEmpty()) {
                throw new IllegalStateException("no directories specified; this is a required section");
            }
            if (!deriveTargetsFromDirectories && targets.isEmpty()) {
                throw new IllegalStateException(
                        "no targets specified; this is a required section unless derive_targets_from_directories is set");
            }

            List<String> directoriesToInclude = new ArrayList<>();
            List<String> directoriesToExclude = new ArrayList<>();
            for (String d : directories) {
                if (d.startsWith(EXCLUDED_ENTRY_PREFIX)) {
                    directoriesToExclude.add(d);
                } else {
                    directoriesToInclude.add(d);
                }
            }
            if (directoriesToInclude.isEmpty()) {
                throw new IllegalStateException("directories contains only excludes; at least one include is required");
            }

            List<String> targetsToInclude = new ArrayList<>();
            List<String> targetsToExclude = new ArrayList<>();
            for (String t : targets) {
                if (t.startsWith(EXCLUDED_ENTRY_PREFIX)) {
                    targetsToExclude.add(t);
                } else {
                    targetsToInclude.add(t);
                }
            }
            if (!deriveTargetsFromDirectories && targetsToInclude.isEmpty()) {
                throw new IllegalStateException(
                        "targets contains only excludes; at least one include is required unless derive_targets_from_directories is set");
            }

            return new BazelProjectView(directoriesToInclude, directoriesToExclude, targetsToInclude, targetsToExclude,
                    deriveTargetsFromDirectories, workspaceType, additionalLanguages, javaLanguageLevel, tsConfigRules,
                    targetDiscoveryStrategy, targetProvisioningStrategy);
        }
    }

    private static final Pattern SECTION_HEADER_REGEX = Pattern.compile("((^[^:\\-/*\\s]+)([: ]))", Pattern.MULTILINE);
    private static final Pattern WHITESPACE_CHAR_REGEX = Pattern.compile("\\s+");
    private static final String COMMENT_LINE_REGEX = "#(.)*(\\n|\\z)";
    private static final String EXCLUDED_ENTRY_PREFIX = "-";

    private final Path bazelProjectFile;

    public BazelProjectFileReader(Path bazelProjectFile) {
        this.bazelProjectFile = bazelProjectFile;
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
                    var rawBody = rawSection.getRawBody();
                    if ((rawBody != null) && !rawBody.isBlank()) {
                        builder.deriveTargetsFromDirectories = Boolean.parseBoolean(rawBody.trim());
                    }
                    break;
                }
                case "import": {
                    Path fileToImport;
                    try {
                        fileToImport = rawSection.getBodyAsPath();
                    } catch (NullPointerException e) {
                        throw new IOException(format("Invalid syntax in '%s': import needs a value!", bazelProjectFile),
                                e);
                    }
                    if (fileToImport.isAbsolute()) {
                        throw new IOException(format(
                            "Invalid import (%s) defined in '%s': imports must be relative to the file they are defined in",
                            fileToImport, bazelProjectFile));
                    }
                    var resolvedPathOfFileToImport = bazelProjectFile.resolveSibling(fileToImport);
                    try {
                        // parse the import into the existing builder (this allows to implement the wanted behavior)
                        parseProjectFile(resolvedPathOfFileToImport, builder);
                    } catch (NoSuchFileException e) {
                        throw new NoSuchFileException(resolvedPathOfFileToImport.toString(),
                                bazelProjectFile.toString(), format("import '%s' not found (defined in '%s')",
                                    resolvedPathOfFileToImport, bazelProjectFile));
                    }
                    break;
                }
                case "workspace_type": {
                    try {
                        builder.workspaceType = rawSection.getBodyAsSingleValue();
                    } catch (NullPointerException e) {
                        throw new IOException(
                                format("Invalid syntax in '%s': workspace_type needs a value!", bazelProjectFile), e);
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
                                format("Invalid syntax in '%s': java_language_level needs a value!", bazelProjectFile),
                                e);
                    }
                    break;
                }
                case "ts_config_rules": {
                    parseSectionBodyIntoList(rawSection).forEach(builder.tsConfigRules::add);
                    break;
                }
                case "target_discovery_strategy": {
                    // extension for BEF
                    try {
                        builder.targetDiscoveryStrategy = rawSection.getBodyAsSingleValue();
                    } catch (NullPointerException e) {
                        throw new IOException(format("Invalid syntax in '%s': target_discovery_strategy needs a value!",
                            bazelProjectFile), e);
                    }
                    break;
                }
                case "target_provisioning_strategy": {
                    // extension for BEF
                    try {
                        builder.targetProvisioningStrategy = rawSection.getBodyAsSingleValue();
                    } catch (NullPointerException e) {
                        throw new IOException(
                                format("Invalid syntax in '%s': target_provisioning_strategy needs a value!",
                                    bazelProjectFile),
                                e);
                    }
                    break;
                }
                case "import_target_output":
                case "exclude_target": {
                    // ignore deprecated
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unexpected value: " + rawSection.getName());
            }
        }
    }

    private List<RawSection> parseRawSections(String bazelProjectFileContent) throws IOException {
        List<RawSection> results = new ArrayList<>();
        var headers = SECTION_HEADER_REGEX.matcher(bazelProjectFileContent).results().map(t -> t.group(2))
                .toArray(String[]::new);
        var bodies = SECTION_HEADER_REGEX.split(bazelProjectFileContent);
        if (headers.length != (bodies.length - 1)) {
            throw new IOException(format(
                "Syntax error in .bazelproject: The number of section headers doesn't match the number of section bodies (%d != %d; header: %s).",
                headers.length, bodies.length, Stream.of(headers).collect(joining(", "))));
        }

        for (var i = 0; i < headers.length; i++) {
            results.add(new RawSection(headers[i], bodies[i + 1].trim()));
        }
        return results;

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