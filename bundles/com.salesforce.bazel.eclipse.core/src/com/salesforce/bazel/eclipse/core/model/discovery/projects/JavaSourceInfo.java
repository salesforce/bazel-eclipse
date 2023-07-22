package com.salesforce.bazel.eclipse.core.model.discovery.projects;

import static java.lang.String.format;
import static java.nio.file.Files.find;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.readString;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Function;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;

/**
 * Source information used by {@link JavaProjectInfo} to analyze the <code>srcs</code> information in order to identify
 * root directories or split packages and recommend a layout.
 */
public class JavaSourceInfo {

    private static final IPath INVALID = new Path("_not_following_ide_standards_");

    private static boolean isJavaFile(java.nio.file.Path file) {
        return isRegularFile(file) && file.getFileName().toString().endsWith(".java");
    }

    private final Map<IPath, IPath> detectedPackagePathsByFileEntryPathParent = new HashMap<>();
    private final List<Entry> srcs;
    private final IPath bazelPackageLocation;

    /**
     * A list of all source files impossible to identify a common root directory
     */
    private List<JavaSourceEntry> sourceFilesWithoutCommonRoot;

    /**
     * a map of all discovered source directors and their content (which may either be a {@link List} of
     * {@link JavaSourceEntry} or a single {@link GlobEntry}.
     */
    private Map<IPath, Object> sourceDirectoriesWithFilesOrGlobs;

    public JavaSourceInfo(List<Entry> srcs, IPath bazelPackageLocation) {
        this.srcs = srcs;
        this.bazelPackageLocation = bazelPackageLocation;
    }

    @SuppressWarnings("unchecked")
    public void analyzeSourceDirectories(MultiStatus result) throws CoreException {
        // build an index of all source files and their parent directories (does not need to maintain order)
        Map<IPath, List<JavaSourceEntry>> sourceEntriesByParentFolder = new HashMap<>();

        // group by potential source roots
        Function<JavaSourceEntry, IPath> groupingByPotentialSourceRoots = fileEntry -> {
            // detect package if necessary
            if (fileEntry.detectedPackagePath == null) {
                fileEntry.detectedPackagePath = detectPackagePath(fileEntry);
            }

            // calculate potential source root
            var potentialSourceDirectoryRoot = fileEntry.getPotentialSourceDirectoryRoot();
            if (potentialSourceDirectoryRoot == null) {
                result.add(
                    Status.warning(
                        format(
                            "Java file '%s' (with detected package '%s') does not meet IDE standards. Please move into a folder hierarchy which follows Java package structure!",
                            fileEntry.getPath(),
                            fileEntry.getDetectedPackagePath())));
                return INVALID;
            }

            // build second index of parent for all entries with a potential source root
            // this is needed in order to identify split packages later
            sourceEntriesByParentFolder.putIfAbsent(fileEntry.getPathParent(), new ArrayList<>());
            sourceEntriesByParentFolder.get(fileEntry.getPathParent()).add(fileEntry);

            // return the potential source root (relative)
            return potentialSourceDirectoryRoot.makeRelative().removeTrailingSeparator();

        };

        // collect the potential list of source directories
        var sourceEntriesBySourceRoot = new LinkedHashMap<IPath, Object>();
        for (Entry srcEntry : srcs) {
            if (srcEntry instanceof JavaSourceEntry javaSourceFile) {
                var sourceDirectory = groupingByPotentialSourceRoots.apply(javaSourceFile);
                if (!sourceEntriesBySourceRoot.containsKey(sourceDirectory)) {
                    var list = new ArrayList<>();
                    list.add(javaSourceFile);
                    sourceEntriesBySourceRoot.put(sourceDirectory, list);
                } else {
                    var maybeList = sourceEntriesBySourceRoot.get(sourceDirectory);
                    if (maybeList instanceof List list) {
                        list.add(javaSourceFile);
                    } else {
                        result.add(
                            Status.error(
                                format(
                                    "It looks like source root '%s' is already mapped to a glob pattern. Please split into a separate targets. We cannot support this in the IDE.",
                                    sourceDirectory)));
                    }
                }
            } else if (srcEntry instanceof GlobEntry globEntry) {
                if (sourceEntriesByParentFolder.containsKey(globEntry.getRelativeDirectoryPath())) {
                    result.add(
                        Status.error(
                            format(
                                "It looks like source root '%s' is already mapped to more than one glob pattern. Please split into a separate targets. We cannot support this in the IDE.",
                                globEntry.getRelativeDirectoryPath())));
                } else {
                    sourceEntriesBySourceRoot.put(globEntry.getRelativeDirectoryPath(), globEntry);
                }
            } else {
                // check if the source has label dependencies
                result.add(
                    Status.warning(
                        format(
                            "Found source label reference '%s'. The project may not be fully supported in the IDE.",
                            srcEntry)));
            }
        }

        // discover folders that contain more .java files then declared in srcs
        // (this is a strong split-package indication)
        Set<IPath> potentialSplitPackageOrSubsetFolders = new HashSet<>();
        for (Map.Entry<IPath, List<JavaSourceEntry>> entry : sourceEntriesByParentFolder.entrySet()) {
            var entryParentPath = entry.getKey();
            var entryParentLocation = bazelPackageLocation.append(entryParentPath).toPath();
            var declaredJavaFilesInFolder = entry.getValue().size();
            try {
                // when there are declared Java files, expect them to match
                if (declaredJavaFilesInFolder > 0) {
                    var javaFilesInParent = Files.list(entryParentLocation).filter(JavaSourceInfo::isJavaFile).count();
                    if (javaFilesInParent != declaredJavaFilesInFolder) {
                        if (potentialSplitPackageOrSubsetFolders.add(entryParentPath)) {
                            result.add(
                                Status.warning(
                                    format(
                                        "Folder '%s' contains more Java files then configured in Bazel. This is a split-package scenario which is challenging to support in IDEs! Consider re-structuring your source code into separate folder hierarchies and Bazel packages.",
                                        entryParentLocation)));
                        }
                        continue; // continue with next so we capture all possible warnings (we could also abort, though)
                    }
                }
            } catch (IOException e) {
                throw new CoreException(Status.error(format("Error searching files in '%s'", entryParentLocation), e));
            }
        }

        // discover folders that contain more Java files (including package fragments) then declared in srcs
        // (eg., glob(["src/test/java/some/package/only/*.java"])
        for (var potentialSourceRootAndSourceEntries : sourceEntriesBySourceRoot.entrySet()) {
            var potentialSourceRoot = potentialSourceRootAndSourceEntries.getKey();
            if (INVALID.equals(potentialSourceRoot)) {
                continue;
            }
            if (!(potentialSourceRootAndSourceEntries.getValue() instanceof List)) {
                continue;
            }

            var potentialSourceRootPath = bazelPackageLocation.append(potentialSourceRoot).toPath();
            try {
                var registeredFiles = ((List<?>) potentialSourceRootAndSourceEntries.getValue()).size();
                var foundJavaFilesInSourceRoot = find(
                    potentialSourceRootPath,
                    Integer.MAX_VALUE,
                    (p, a) -> isJavaFile(p),
                    FileVisitOption.FOLLOW_LINKS).count();
                if ((registeredFiles != foundJavaFilesInSourceRoot)
                        && potentialSplitPackageOrSubsetFolders.add(potentialSourceRoot)) {
                    result.add(
                        Status.warning(
                            format(
                                "Folder '%s' contains more Java files then configured in Bazel. This is a scenario which is challenging to support in IDEs! Consider re-structuring your source code into separate folder hierarchies and Bazel packages.",
                                potentialSourceRootPath)));
                }
            } catch (IOException e) {
                throw new CoreException(
                        Status.error(format("Error searching files in '%s'", potentialSourceRootPath), e));
            }
        }

        // when there are no split packages we found a good setup
        if (potentialSplitPackageOrSubsetFolders.isEmpty()) {
            // collect remaining files without a root
            if (sourceEntriesBySourceRoot.containsKey(INVALID)) {
                sourceFilesWithoutCommonRoot = (List<JavaSourceEntry>) sourceEntriesBySourceRoot.remove(INVALID);
            }

            // create source directories
            this.sourceDirectoriesWithFilesOrGlobs = sourceEntriesBySourceRoot;

        } else {
            // treat all sources as if they don't have a directory
            // (if there are multiple source roots we could do an extra effort and try to filter the ones without split packages; but is this worth supporting?)
            sourceFilesWithoutCommonRoot = srcs.stream()
                    .filter(JavaSourceEntry.class::isInstance)
                    .map(JavaSourceEntry.class::cast)
                    .collect(toList());
        }
    }

    private IPath detectPackagePath(JavaSourceEntry fileEntry) {
        // we inspect at most one file per directory (anything else is too weird to support)
        var previouslyDetectedPackagePath = detectedPackagePathsByFileEntryPathParent.get(fileEntry.getPathParent());
        if (previouslyDetectedPackagePath != null) {
            return previouslyDetectedPackagePath;
        }

        // assume empty by default
        IPath packagePath = Path.EMPTY;
        var packageName = readPackageName(fileEntry);
        if (packageName.length() > 0) {
            var packageNameSegments = new StringTokenizer(new String(packageName), ".");
            while (packageNameSegments.hasMoreElements()) {
                packagePath = packagePath.append(packageNameSegments.nextToken());
            }
        }

        // remember in cache
        detectedPackagePathsByFileEntryPathParent.put(fileEntry.getPathParent(), packagePath);

        return packagePath;
    }

    public IPath getBazelPackageLocation() {
        return bazelPackageLocation;
    }

    public Collection<IPath> getDetectedJavaPackages() {
        return requireNonNull(sourceDirectoriesWithFilesOrGlobs, "no source directories discovered").values()
                .stream()
                .filter(JavaSourceEntry.class::isInstance)
                .map(JavaSourceEntry.class::cast)
                .map(JavaSourceEntry::getDetectedPackagePath)
                .distinct()
                .collect(toList());
    }

    /**
     * @param sourceDirectory
     *            the source directory (must be contained in {@link #getSourceDirectories()})
     * @return all detected Java packages for the specified source directory (collected from found files)
     */
    public Collection<IPath> getDetectedJavaPackagesForSourceDirectory(IPath sourceDirectory) {
        var fileOrGlob = requireNonNull(
            requireNonNull(sourceDirectoriesWithFilesOrGlobs, "no source directories discovered").get(sourceDirectory),
            () -> format("source directory '%s' unknown", sourceDirectory));
        if (fileOrGlob instanceof Collection<?> files) {
            return files.stream()
                    .map(JavaSourceEntry.class::cast)
                    .map(JavaSourceEntry::getDetectedPackagePath)
                    .distinct()
                    .collect(toList());
        }

        // no info for globs
        return Collections.emptyList();
    }

    /**
     * @param sourceDirectory
     *            the source directory (must be contained in {@link #getSourceDirectories()})
     * @return the configured excludes (if the source directory is based on a <code>glob</code>, <code>null</code> if
     *         nothing should be excluded <code>glob</code>)
     */
    public IPath[] getExclutionPatternsForSourceDirectory(IPath sourceDirectory) {
        var fileOrGlob = requireNonNull(
            requireNonNull(sourceDirectoriesWithFilesOrGlobs, "no source directories discovered").get(sourceDirectory),
            () -> format("source directory '%s' unknown", sourceDirectory));
        if (fileOrGlob instanceof GlobEntry globEntry) {
            var excludePatterns = globEntry.getExcludePatterns();
            if (excludePatterns != null) {
                var exclusionPatterns = new IPath[excludePatterns.size()];
                for (var i = 0; i < exclusionPatterns.length; i++) {
                    exclusionPatterns[i] = Path.forPosix(excludePatterns.get(i));
                }
                return exclusionPatterns;
            }
        }

        // exclude nothing for none-globs
        return null;
    }

    /**
     * @param sourceDirectory
     *            the source directory (must be contained in {@link #getSourceDirectories()})
     * @return the configured includes (if the source directory is based on a <code>glob</code>, <code>null</code> if
     *         everything should be included)
     */
    public IPath[] getInclusionPatternsForSourceDirectory(IPath sourceDirectory) {
        var fileOrGlob = requireNonNull(
            requireNonNull(sourceDirectoriesWithFilesOrGlobs, "no source directories discovered").get(sourceDirectory),
            () -> format("source directory '%s' unknown", sourceDirectory));
        if (fileOrGlob instanceof GlobEntry globEntry) {
            var includePatterns = globEntry.getIncludePatterns();
            if (includePatterns != null) {
                var exclusionPatterns = new IPath[includePatterns.size()];
                for (var i = 0; i < exclusionPatterns.length; i++) {
                    exclusionPatterns[i] = Path.forPosix(includePatterns.get(i));
                }
                return exclusionPatterns;
            }
        }

        // include everything for none-globs
        return null;
    }

    /**
     * {@return the list of detected source directories (relative to #getBazelPackageLocation())}
     */
    public Collection<IPath> getSourceDirectories() {
        return requireNonNull(sourceDirectoriesWithFilesOrGlobs, "no source directories discovered").keySet();
    }

    public List<JavaSourceEntry> getSourceFilesWithoutCommonRoot() {
        return requireNonNull(sourceFilesWithoutCommonRoot, "no source files analyzed");
    }

    public boolean hasSourceDirectories() {
        return (sourceDirectoriesWithFilesOrGlobs != null) && !sourceDirectoriesWithFilesOrGlobs.isEmpty();
    }

    public boolean hasSourceFilesWithoutCommonRoot() {
        return (sourceFilesWithoutCommonRoot != null) && !sourceFilesWithoutCommonRoot.isEmpty();
    }

    @SuppressWarnings("deprecation") // use of TokenNameIdentifier is ok here
    private String readPackageName(JavaSourceEntry fileEntry) {
        var packageName = new StringBuilder();

        var scanner = ToolFactory.createScanner( //
            false, // tokenizeComments
            false, // tokenizeWhiteSpace
            false, // assertMode
            false // recordLineSeparator
        );
        try {
            var content = readString(fileEntry.getLocation().toPath());
            scanner.setSource(content.toCharArray());

            var token = scanner.getNextToken();
            while (true) {
                switch (token) {
                    case ITerminalSymbols.TokenNamepackage:
                        token = scanner.getNextToken();
                        while (token == ITerminalSymbols.TokenNameIdentifier) {
                            var packageNameChars = scanner.getCurrentTokenSource();
                            packageName.append(packageNameChars);
                            token = scanner.getNextToken();
                            if (token == ITerminalSymbols.TokenNameDOT) {
                                packageName.append('.');
                                token = scanner.getNextToken();
                            }
                        }
                        continue;
                    case ITerminalSymbols.TokenNameimport: // stop at imports
                    case ITerminalSymbols.TokenNameEOF: // stop at EOF
                        return packageName.toString();
                    default:
                        token = scanner.getNextToken();
                        continue;
                }
            }
        } catch (InvalidInputException | IndexOutOfBoundsException | IOException e) {
            // ignore
        }
        return packageName.toString();
    }
}
