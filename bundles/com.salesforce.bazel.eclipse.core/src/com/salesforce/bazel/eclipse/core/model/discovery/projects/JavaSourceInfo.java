package com.salesforce.bazel.eclipse.core.model.discovery.projects;

import static java.lang.String.format;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.find;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.readString;
import static java.nio.file.Files.walkFileTree;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;

import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;

/**
 * Source information used by {@link JavaProjectInfo} to analyze the <code>srcs</code> information in order to identify
 * root directories or split packages and recommend a layout.
 */
public class JavaSourceInfo {

    private static final IPath NOT_FOLLOWING_JAVA_PACKAGE_STRUCTURE =
            IPath.forPosix("_not_following_java_package_structure_");

    private static boolean isJavaFile(java.nio.file.Path file) {
        return isRegularFile(file) && file.getFileName().toString().endsWith(".java");
    }

    private final Map<IPath, IPath> detectedPackagePathsByFileEntryPathParent = new HashMap<>();
    private final Collection<Entry> srcs;
    private final IPath bazelPackageLocation;
    private final JavaSourceInfo sharedSourceInfo;
    private final BazelWorkspace bazelWorkspace;

    /**
     * A list of all source files impossible to identify a common root directory
     */
    private List<JavaSourceEntry> sourceFilesWithoutCommonRoot;

    /**
     * a map of all discovered source directors and their content (which may either be a {@link List} of
     * {@link JavaSourceEntry} or a single {@link GlobEntry}.
     */
    private Map<IPath, Object> sourceDirectoriesWithFilesOrGlobs;

    public JavaSourceInfo(Collection<Entry> srcs, BazelPackage bazelPackage) {
        this.srcs = srcs;
        this.bazelPackageLocation = bazelPackage.getLocation();
        this.sharedSourceInfo = null;
        this.bazelWorkspace = bazelPackage.getBazelWorkspace();
    }

    /**
     * Use this constructor for test sources, i.e. sources which may have targets sharing sources.
     * <p>
     * Bazel allows to re-use sources in multiple targets. It will then compile those multiple times. An example setup
     * is where all code is exposed as <code>java_library</code> as well as many targets for <code>java_test</code> with
     * only one test class. If this is the case, we want to not issue "split package" warnings when the test class is
     * already handled at the <code>java_library</code> level.
     * </p>
     *
     * @param srcs
     * @param bazelPackageLocation
     * @param sharedSourceInfo
     */
    public JavaSourceInfo(Collection<Entry> srcs, BazelPackage bazelPackage, JavaSourceInfo sharedSourceInfo) {
        this.srcs = srcs;
        this.bazelPackageLocation = bazelPackage.getLocation();
        this.sharedSourceInfo = sharedSourceInfo;
        this.bazelWorkspace = bazelPackage.getBazelWorkspace();
    }

    @SuppressWarnings("unchecked")
    public void analyzeSourceDirectories(MultiStatus result) throws CoreException {
        // build an index of all source files and their parent directories (does not need to maintain order)
        Map<IPath, List<? super JavaSourceEntry>> sourceEntriesByParentFolder = new HashMap<>();

        // group by potential source roots
        Function<? super JavaSourceEntry, IPath> groupingByPotentialSourceRoots = fileEntry -> {
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
                return NOT_FOLLOWING_JAVA_PACKAGE_STRUCTURE;
            }

            // return the potential source root
            return potentialSourceDirectoryRoot;

        };

        // collect the potential list of source directories
        var sourceEntriesBySourceRoot = new LinkedHashMap<IPath, Object>();

        // define a function for JavaSourceEntry so we can reuse it for sources and extracted srcjars
        Function<JavaSourceEntry, Void> javaSourceEntryCollector = javaSourceFile -> {
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
            return null; // not relevant
        };

        for (Entry srcEntry : srcs) {
            if (srcEntry instanceof JavaSourceEntry javaSourceFile) {
                javaSourceEntryCollector.apply(javaSourceFile);

                // build second index of parent for all entries with a potential source root
                // this is needed in order to identify split packages (in same directory) later
                sourceEntriesByParentFolder.putIfAbsent(javaSourceFile.getPathParent(), new ArrayList<>());
                sourceEntriesByParentFolder.get(javaSourceFile.getPathParent()).add(javaSourceFile);
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
            } else if (srcEntry instanceof LabelEntry labelEntry) {
                var bazelTarget = bazelWorkspace.getBazelTarget(labelEntry.getLabel());
                var srcJars = bazelTarget.getRuleOutput()
                        .stream()
                        .filter(p -> "srcjar".equals(p.getFileExtension()))
                        .collect(toList());
                for (IPath srcjar : srcJars) {
                    var srcjarFolder = extractSrcJar(bazelTarget, srcjar);
                    collectJavaSourcesInFolder(srcjarFolder).forEach(javaSourceEntryCollector::apply);
                }
            } else {
                throw new CoreException(Status.error(format("Unexpected source '%s'!", srcEntry)));
            }
        }

        // discover folders that contain more .java files then declared in srcs
        // (this is a strong split-package indication)
        Set<IPath> potentialSplitPackageOrSubsetFolders = new HashSet<>();
        for (Map.Entry<IPath, List<? super JavaSourceEntry>> entry : sourceEntriesByParentFolder.entrySet()) {
            var potentialSourceRoot = entry.getKey();
            if (isContainedInSharedSourceDirectories(potentialSourceRoot)) {
                // don't check for split packages for stuff covered in shared sources already
                continue;
            }

            var entryParentLocation = bazelPackageLocation.append(potentialSourceRoot).toPath();
            var declaredJavaFilesInFolder = entry.getValue().size();
            try {
                // when there are declared Java files, expect them to match
                if (declaredJavaFilesInFolder > 0) {
                    try (var files = Files.list(entryParentLocation)) {
                        var javaFilesInParent = files.filter(JavaSourceInfo::isJavaFile).count();
                        if (javaFilesInParent != declaredJavaFilesInFolder) {
                            if (potentialSplitPackageOrSubsetFolders.add(potentialSourceRoot)) {
                                result.add(
                                    Status.warning(
                                        format(
                                            "Folder '%s' contains more Java files then configured in Bazel. This is a split-package scenario which is challenging to support in IDEs! Consider re-structuring your source code into separate folder hierarchies and Bazel packages.",
                                            entryParentLocation)));
                            }
                            continue; // continue with next so we capture all possible warnings (we could also abort, though)
                        }
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
            if (NOT_FOLLOWING_JAVA_PACKAGE_STRUCTURE.equals(potentialSourceRoot)) {
                continue;
            }
            if (!(potentialSourceRootAndSourceEntries.getValue() instanceof List)) {
                continue;
            }
            if (isContainedInSharedSourceDirectories(potentialSourceRoot)) {
                // don't check for split packages for stuff covered in shared sources already
                continue;
            }
            if (potentialSourceRoot.isAbsolute()) {
                // don't check for split packages in srcjars (generated code)
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

        // don't issue split packages warning for stuff covered in shared sources already
        // (test code is allowed to have same package)
        if ((sharedSourceInfo != null) && sharedSourceInfo.hasSourceDirectories()) {
            potentialSplitPackageOrSubsetFolders.removeIf(this::isContainedInSharedSourceDirectories);
        }

        // when there are no split packages we found a good setup
        if (potentialSplitPackageOrSubsetFolders.isEmpty()) {
            // collect remaining files without a root
            if (sourceEntriesBySourceRoot.containsKey(NOT_FOLLOWING_JAVA_PACKAGE_STRUCTURE)) {
                sourceFilesWithoutCommonRoot =
                        (List<JavaSourceEntry>) sourceEntriesBySourceRoot.remove(NOT_FOLLOWING_JAVA_PACKAGE_STRUCTURE);
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

    private Collection<JavaSourceEntry> collectJavaSourcesInFolder(IPath directory) throws CoreException {
        try {
            List<JavaSourceEntry> result = new ArrayList<>();
            walkFileTree(directory.toPath(), new SimpleFileVisitor<>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    var path = IPath.fromPath(file);
                    if ("java".equals(path.getFileExtension())) {
                        result.add(new JavaSrcJarEntry(path.removeFirstSegments(directory.segmentCount()), directory));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return result;
        } catch (IOException e) {
            throw new CoreException(
                    Status.error(format("Unable to scan directory '%s' for .java source files.", directory), e));
        }
    }

    private IPath detectPackagePath(JavaSourceEntry fileEntry) {
        // we inspect at most one file per directory (anything else is too weird to support)
        var previouslyDetectedPackagePath = detectedPackagePathsByFileEntryPathParent.get(fileEntry.getPathParent());
        if (previouslyDetectedPackagePath != null) {
            return previouslyDetectedPackagePath;
        }

        // assume empty by default
        var packagePath = IPath.EMPTY;
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

    /**
     * Extract the source jar (typically found in the bazel-bin directory of the package) into a directory for
     * consumption as source folder in an Eclipse project.
     * <p>
     * The srcjar will be extracted into a directory inside bazel-bin.
     * </p>
     *
     * @param bazelTarget
     *            the target producing the source jar
     * @param srcjar
     *            the path to the source jar
     * @return absolute file system path to the directory containing the extracted sources
     * @throws CoreException
     */
    private IPath extractSrcJar(BazelTarget bazelTarget, IPath srcjar) throws CoreException {
        var jarFile = bazelWorkspace.getBazelBinLocation()
                .append(bazelTarget.getBazelPackage().getWorkspaceRelativePath())
                .append(srcjar);
        var targetDirectory = jarFile.removeLastSegments(1).append("_eclipse").append(srcjar.lastSegment());

        // TODO: need a more sensitive delta/diff (including removal) for Eclipse resource refresh

        var destination = targetDirectory.toPath();
        try (var archive = new ZipFile(jarFile.toFile())) {
            // sort entries by name to always create folders first
            List<? extends ZipEntry> entries =
                    archive.stream().sorted(Comparator.comparing(ZipEntry::getName)).collect(toList());
            for (ZipEntry entry : entries) {
                var entryDest = destination.resolve(entry.getName());
                if (entry.isDirectory()) {
                    createDirectories(entryDest);
                } else {
                    createDirectories(entryDest.getParent());
                    try (var is = archive.getInputStream(entry)) {
                        copy(is, entryDest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException e) {
            throw new CoreException(Status.error(format("Error extracting srcjar '%s'", srcjar), e));
        }

        return targetDirectory;
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
                    exclusionPatterns[i] = IPath.forPosix(excludePatterns.get(i));
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
                    exclusionPatterns[i] = IPath.forPosix(includePatterns.get(i));
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

    private boolean isContainedInSharedSourceDirectories(IPath potentialSourceRoot) {
        if ((sharedSourceInfo == null) || !sharedSourceInfo.hasSourceDirectories()) {
            return false;
        }

        /*
         * Bazel allows to re-use sources in multiple targets. It will then compile those multiple times. An example setup
         * is where all code is exposed as <code>java_library</code> as well as many targets for <code>java_test</code> with
         * only one test class. If this is the case, we want to not issue "split package" warnings when the test class is
         * already handled at the <code>java_library</code> level.
         */

        var sharedSourceDirectories = sharedSourceInfo.getSourceDirectories();
        return sharedSourceDirectories.contains(potentialSourceRoot)
                || sharedSourceDirectories.stream().anyMatch(p -> p.isPrefixOf(potentialSourceRoot));
    }

    /**
     * Performs a check of all entries discovered for a source directory to match a given predicate.
     *
     * @param sourceDirectory
     *            the source directory (must be contained in {@link #getSourceDirectories()})
     * @param predicate
     *            the predicate that all entries for the specified source directory must match
     * @return <code>true</code> if all match, <code>false</code> otherwise
     */
    public boolean matchAllSourceDirectoryEntries(IPath sourceDirectory, Predicate<? super Entry> predicate) {
        var fileOrGlob = requireNonNull(
            requireNonNull(sourceDirectoriesWithFilesOrGlobs, "no source directories discovered").get(sourceDirectory),
            () -> format("source directory '%s' unknown", sourceDirectory));
        if (fileOrGlob instanceof GlobEntry globEntry) {
            return predicate.test(globEntry);
        }
        if (fileOrGlob instanceof List<?> listOfEntries) {
            // the case is save assuming no programming mistakes in this class
            return listOfEntries.stream().map(JavaSourceEntry.class::cast).allMatch(predicate);
        }
        return false;
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
