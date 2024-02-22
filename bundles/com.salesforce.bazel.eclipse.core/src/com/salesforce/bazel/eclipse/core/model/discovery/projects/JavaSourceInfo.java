package com.salesforce.bazel.eclipse.core.model.discovery.projects;

import static java.lang.String.format;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.find;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.list;
import static java.nio.file.Files.readString;
import static java.nio.file.Files.walkFileTree;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
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
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeSet;
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
    private static final IPath MISSING_PACKAGE = IPath.forPosix("_missing_package_declaration_");

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
        bazelPackageLocation = bazelPackage.getLocation();
        sharedSourceInfo = null;
        bazelWorkspace = bazelPackage.getBazelWorkspace();
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
        bazelPackageLocation = bazelPackage.getLocation();
        this.sharedSourceInfo = sharedSourceInfo;
        bazelWorkspace = bazelPackage.getBazelWorkspace();
    }

    @SuppressWarnings("unchecked")
    public void analyzeSourceDirectories(MultiStatus result,
            boolean reportFoldersWithMoreJavaSourcesThanDeclaredAsProblem) throws CoreException {
        // build an index of all source files and their parent directories (does not need to maintain order)
        Map<IPath, List<? super JavaSourceEntry>> sourceEntriesByParentFolder = new HashMap<>();

        // group by potential source roots
        Function<? super JavaSourceEntry, IPath> groupingByPotentialSourceRoots = fileEntry -> {
            // detect package if necessary
            if (fileEntry.detectedPackagePath == null) {
                fileEntry.detectedPackagePath = detectPackagePath(fileEntry);

                // if unable to detect; collect for rescue in 2nd pass later
                if (fileEntry.detectedPackagePath == null) {
                    return MISSING_PACKAGE;
                }
            }

            // calculate potential source root
            var potentialSourceDirectoryRoot = fileEntry.getPotentialSourceDirectoryRoot();
            if (potentialSourceDirectoryRoot == null) {
                return NOT_FOLLOWING_JAVA_PACKAGE_STRUCTURE;
            }

            // return the potential source root
            return potentialSourceDirectoryRoot;

        };

        // collect the potential list of source directories (value is list of JavaSourceEntry or single GlobEntry)
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
                if (!bazelWorkspace.isRootedAtThisWorkspace(labelEntry.getLabel())) {
                    result.add(
                        Status.error(
                            format(
                                "The referenced sources '%s' are outside of this workspace. The project will not compile. Please consider excluding that target.",
                                labelEntry.getLabel())));
                    continue;

                }
                var bazelTarget = bazelWorkspace.getBazelTarget(labelEntry.getLabel());
                var srcJars = bazelTarget.getRuleOutput()
                        .stream()
                        .filter(p -> "srcjar".equals(p.getFileExtension()))
                        .collect(toList());
                for (IPath srcjar : srcJars) {
                    var srcjarFolder = extractSrcJar(bazelTarget, srcjar);
                    if (srcjarFolder == null) {
                        result.add(
                            Status.error(
                                format(
                                    "The generated sources '%s' (produced by '%s') are missing. Please check the build output for errors. The project will not compile.",
                                    srcjar,
                                    bazelTarget.getLabel())));
                    } else {
                        // collect all sources as JavaSourceEntry
                        // it will be possible to differentiate them later because their directory is absolute
                        // (it's outside the package)
                        collectJavaSourcesInFolder(srcjarFolder).forEach(javaSourceEntryCollector::apply);
                    }
                }
            } else {
                throw new CoreException(Status.error(format("Unexpected source '%s'!", srcEntry)));
            }
        }

        // rescue missing packages
        if (sourceEntriesBySourceRoot.containsKey(MISSING_PACKAGE)) {
            // we use the MISSING_PACKAGE when a package could not be calculated
            // in this pass we try to "rescue" them
            var entriesWithMissingPackageInfo =
                    (List<JavaSourceEntry>) sourceEntriesBySourceRoot.remove(MISSING_PACKAGE);
            for (JavaSourceEntry javaSourceEntry : entriesWithMissingPackageInfo) {
                // try applying one more time (maybe this time a directory is cached)
                javaSourceEntryCollector.apply(javaSourceEntry);

                // find a source that that's containing the file if it's still missing
                if (javaSourceEntry.detectedPackagePath == null) {
                    result.add(
                        Status.error(
                            format(
                                "Unable to detect package for Java file '%s'. Please double check it has a package declaration!",
                                javaSourceEntry.getPath())));
                } else {
                    // build second index of parent for all entries with a potential source root
                    // this is needed in order to identify split packages (in same directory) later
                    sourceEntriesByParentFolder.putIfAbsent(javaSourceEntry.getPathParent(), new ArrayList<>());
                    sourceEntriesByParentFolder.get(javaSourceEntry.getPathParent()).add(javaSourceEntry);
                }
            }

            // migrate all remaining entries to NOT_FOLLOWING_JAVA_PACKAGE_STRUCTURE
            // this may allow to rescue some (but more importantly, we do not want the MISSING_PACKAGE as source folder reported)
            if (sourceEntriesBySourceRoot.containsKey(MISSING_PACKAGE)) {
                entriesWithMissingPackageInfo =
                        (List<JavaSourceEntry>) sourceEntriesBySourceRoot.remove(MISSING_PACKAGE);
                if (!sourceEntriesBySourceRoot.containsKey(NOT_FOLLOWING_JAVA_PACKAGE_STRUCTURE)) {
                    sourceEntriesBySourceRoot.put(NOT_FOLLOWING_JAVA_PACKAGE_STRUCTURE, entriesWithMissingPackageInfo);
                } else {
                    ((List<JavaSourceEntry>) sourceEntriesBySourceRoot.get(NOT_FOLLOWING_JAVA_PACKAGE_STRUCTURE))
                            .addAll(entriesWithMissingPackageInfo);
                }
            }
        }

        // rescue wrong package declarations
        if (sourceEntriesBySourceRoot.containsKey(NOT_FOLLOWING_JAVA_PACKAGE_STRUCTURE)) {
            // we use the NOT_FOLLOWING_JAVA_PACKAGE_STRUCTURE when a package could be calculated
            // but does not meet the IDE standards; however, if there is a common source folder we attempt to rescue it
            var entriesWithWrongPackageInfo =
                    (List<JavaSourceEntry>) sourceEntriesBySourceRoot.get(NOT_FOLLOWING_JAVA_PACKAGE_STRUCTURE);
            var rescuedEntries = new HashSet<JavaSourceEntry>();
            for (IPath sourceRoot : sourceEntriesBySourceRoot.keySet()) {
                if (sourceRoot.equals(MISSING_PACKAGE) || sourceRoot.equals(NOT_FOLLOWING_JAVA_PACKAGE_STRUCTURE)) {
                    continue;
                }
                // only rescue when we have a list but not a glob
                if (sourceEntriesBySourceRoot.get(sourceRoot) instanceof List sourceEntries) {
                    for (JavaSourceEntry javaSourceEntry : entriesWithWrongPackageInfo) {
                        // rescue the entry, the source folder matches
                        if (sourceRoot.isPrefixOf(javaSourceEntry.getPathParent())) {
                            sourceEntries.add(javaSourceEntry);
                            rescuedEntries.add(javaSourceEntry);
                        }
                    }
                    // remove all rescued entries
                    entriesWithWrongPackageInfo.removeIf(rescuedEntries::contains);
                }
            }
            if (entriesWithWrongPackageInfo.isEmpty()) {
                // all rescued
                sourceEntriesBySourceRoot.remove(NOT_FOLLOWING_JAVA_PACKAGE_STRUCTURE);
            } else {
                // report problems for entries impossible to rescue
                for (JavaSourceEntry javaSourceEntry : entriesWithWrongPackageInfo) {
                    result.add(
                        Status.error(
                            format(
                                "Java file '%s' (with detected package '%s') does not meet IDE standards. Please move into a folder hierarchy which follows Java package structure!",
                                javaSourceEntry.getPath(),
                                javaSourceEntry.getDetectedPackagePath())));

                }
            }
        }

        Set<IPath> potentialSplitPackageOrSubsetFolders = new HashSet<>();
        if (reportFoldersWithMoreJavaSourcesThanDeclaredAsProblem) {
            // discover folders that contain more .java files then declared in srcs
            // (this is a strong split-package indication)
            for (Map.Entry<IPath, List<? super JavaSourceEntry>> entry : sourceEntriesByParentFolder.entrySet()) {
                var potentialSourceRoot = entry.getKey();
                if (isContainedInSharedSourceDirectories(potentialSourceRoot)) {
                    // don't check for split packages for stuff covered in shared sources already
                    continue;
                }

                var entryParentLocation = bazelPackageLocation.append(potentialSourceRoot).toPath();
                try {
                    // when there are declared Java files, expect them to match
                    var declaredJavaFilesInFolder = entry.getValue().size();
                    if (declaredJavaFilesInFolder > 0) {
                        var foundJavaFiles = findJavaFilesNoneRecursive(entryParentLocation);
                        var javaFilesInParent = foundJavaFiles.size();
                        if (javaFilesInParent != declaredJavaFilesInFolder) {
                            if (potentialSplitPackageOrSubsetFolders.add(potentialSourceRoot)) {
                                reportDeltaAsProblem(result, entryParentLocation, entry.getValue(), foundJavaFiles);
                            }
                            continue; // continue with next so we capture all possible warnings (we could also abort, though)
                        }
                    }
                } catch (IOException e) {
                    throw new CoreException(
                            Status.error(format("Error searching files in '%s'", entryParentLocation), e));
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
                    var foundJavaFiles = findJavaFilesRecursive(potentialSourceRootPath);
                    var foundJavaFilesInSourceRoot = foundJavaFiles.size();
                    if ((registeredFiles != foundJavaFilesInSourceRoot)
                            && potentialSplitPackageOrSubsetFolders.add(potentialSourceRoot)) {
                        List<? super JavaSourceEntry> declaredEntries =
                                (List<? super JavaSourceEntry>) potentialSourceRootAndSourceEntries.getValue();
                        reportDeltaAsProblem(result, potentialSourceRootPath, declaredEntries, foundJavaFiles);
                    }
                } catch (IOException e) {
                    throw new CoreException(
                            Status.error(format("Error searching files in '%s'", potentialSourceRootPath), e));
                }
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
            sourceDirectoriesWithFilesOrGlobs = sourceEntriesBySourceRoot;

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
        var parentPath = fileEntry.getPathParent();
        var previouslyDetectedPackagePath = detectedPackagePathsByFileEntryPathParent.get(parentPath);
        if (previouslyDetectedPackagePath != null) {
            return previouslyDetectedPackagePath;
        }

        // read package from .java file
        var packageName = readPackageName(fileEntry);
        if (packageName == null) {
            // try to walk up the path for a possible match
            List<String> removedSegments = new ArrayList<>();
            var parent = parentPath;
            while (parent.segmentCount() > 0) {
                removedSegments.add(0, parent.lastSegment());
                parent = parent.removeLastSegments(1);

                var packagePath = detectedPackagePathsByFileEntryPathParent.get(parent);
                if (packagePath != null) {
                    // we have a hit, re-add the removed segments
                    for (String segment : removedSegments) {
                        packagePath = packagePath.append(segment);
                    }
                    // just return, don't remember this one in cache (maybe we should?)
                    return packagePath;
                }
                parent = parent.removeLastSegments(1);
            }
            return null; // unable to compute
        }

        // assume empty by default
        var packagePath = IPath.EMPTY;
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
     * @return absolute file system path to the directory containing the extracted sources or <code>null</code> if the
     *         srcjar does not exists
     * @throws CoreException
     */
    private IPath extractSrcJar(BazelTarget bazelTarget, IPath srcjar) throws CoreException {
        var jarFile = bazelWorkspace.getBazelBinLocation()
                .append(bazelTarget.getBazelPackage().getWorkspaceRelativePath())
                .append(srcjar);
        if (!isRegularFile(jarFile.toPath())) {
            return null;
        }

        var targetDirectory = jarFile.removeLastSegments(1).append("_eclipse").append(srcjar.lastSegment());

        var destination = targetDirectory.toPath();
        var extractedFiles = new HashSet<Path>();
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
                    var destinationFile = entryDest.toFile();
                    try (var is = archive.getInputStream(entry)) {
                        destinationFile.setWritable(true);
                        copy(is, entryDest, StandardCopyOption.REPLACE_EXISTING);
                        destinationFile.setWritable(false);
                    }
                    extractedFiles.add(entryDest);
                }
            }
        } catch (IOException e) {
            throw new CoreException(Status.error(format("Error extracting srcjar '%s'", srcjar), e));
        }

        // purge no longer needed files
        try {
            walkFileTree(destination, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!extractedFiles.contains(file)) {
                        file.toFile().setWritable(true);
                        delete(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new CoreException(
                    Status.error(
                        format("Error purging no longer needed files after extracting srcjar '%s'", srcjar),
                        e));
        }

        return targetDirectory;
    }

    private List<Path> findJavaFilesNoneRecursive(Path directory) throws IOException {
        try (var stream = list(directory)) {
            return stream.filter(JavaSourceInfo::isJavaFile).collect(toList());
        }
    }

    private List<Path> findJavaFilesRecursive(Path directory) throws IOException {
        try (var stream = find(directory, Integer.MAX_VALUE, (p, a) -> isJavaFile(p), FileVisitOption.FOLLOW_LINKS)) {
            return stream.collect(toList());
        }
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
            StringBuilder packageName = null;
            while (true) {
                switch (token) {
                    case ITerminalSymbols.TokenNamepackage:
                        token = scanner.getNextToken();
                        packageName = new StringBuilder();
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
                        return packageName != null ? packageName.toString() : null;
                    default:
                        token = scanner.getNextToken();
                        continue;
                }
            }
        } catch (InvalidInputException | IndexOutOfBoundsException | IOException e) {
            // ignore
        }

        // give up
        return null;
    }

    private void reportDeltaAsProblem(MultiStatus result, Path rootDirectory,
            List<? super JavaSourceEntry> declaredEntries, List<Path> foundJavaFiles) {
        SortedSet<Path> registeredFilesSet = declaredEntries.stream()
                .map(o -> ((JavaSourceEntry) o).getLocation().toPath())
                .collect(toCollection(TreeSet::new));
        SortedSet<Path> foundFilesSet = new TreeSet<>(foundJavaFiles);
        var packageRoot = bazelPackageLocation.toPath();
        String delta;
        if (registeredFilesSet.size() < foundFilesSet.size()) {
            delta = foundFilesSet.stream()
                    .filter(not(registeredFilesSet::contains))
                    .map(p -> packageRoot.relativize(p).toString())
                    .collect(joining("\n - ", " - ", "\n"));
        } else {
            delta = registeredFilesSet.stream()
                    .filter(not(foundFilesSet::contains))
                    .map(p -> packageRoot.relativize(p).toString())
                    .collect(joining("\n - ", " - ", "\n"));
        }
        result.add(
            Status.error(
                format(
                    "Folder '%s' contains more Java files then configured in Bazel. This is a scenario which is challenging to support in IDEs! Consider re-structuring your source code into separate folder hierarchies and Bazel packages.\n%s",
                    rootDirectory,
                    delta)));
    }
}
