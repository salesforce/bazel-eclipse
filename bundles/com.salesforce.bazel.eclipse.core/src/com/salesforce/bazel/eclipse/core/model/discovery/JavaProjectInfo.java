package com.salesforce.bazel.eclipse.core.model.discovery;

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
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Function;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;

import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.buildfile.GlobInfo;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * Holds information for computing Java project configuration of a target or a package.
 * <p>
 * The info is initialized with a {@link BazelPackage}. This package will be used to resolve paths when needed. However,
 * nothing from the package is initially contributing to the {@link JavaProjectInfo}. Instead, callers must call
 * {@link #addInfoFromTarget(BazelTarget)} to populate the {@link JavaProjectInfo} with all information available from a
 * {@link BazelTarget}.
 * </p>
 * <p>
 * The {@link JavaProjectInfo} maintains a stable order. Things added first remain first. Duplicate items will be
 * eliminated.
 * </p>
 */
public class JavaProjectInfo {

    /**
     * An entry in the project info
     */
    public interface Entry {
    }

    /**
     * A glob is used to denote directory with files and potentially excludes.
     */
    public static class GlobEntry implements Entry {

        private final IPath relativeDirectoryPath;
        private final String includePattern;
        private final List<String> excludePatterns;

        public GlobEntry(IPath relativeDirectoryPath, String includePattern, List<String> excludePatterns) {
            this.relativeDirectoryPath = relativeDirectoryPath;
            this.includePattern = includePattern;
            this.excludePatterns = excludePatterns;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            var other = (GlobEntry) obj;
            return Objects.equals(excludePatterns, other.excludePatterns)
                    && Objects.equals(includePattern, other.includePattern)
                    && Objects.equals(relativeDirectoryPath, other.relativeDirectoryPath);
        }

        public List<String> getExcludePatterns() {
            return excludePatterns;
        }

        public String getIncludePattern() {
            return includePattern;
        }

        public IPath getRelativeDirectoryPath() {
            return relativeDirectoryPath;
        }

        @Override
        public int hashCode() {
            return Objects.hash(excludePatterns, includePattern, relativeDirectoryPath);
        }

        @Override
        public String toString() {
            if (excludePatterns == null) {
                return relativeDirectoryPath + "/" + includePattern;
            }
            return relativeDirectoryPath + "/" + includePattern + " (excluding " + excludePatterns + ")";
        }
    }

    /**
     * A source entry points to exactly one <code>.java</code> source file. It contains additional logic for extracting
     * the package path from the location.
     */
    public static class JavaSourceEntry implements Entry {

        private static boolean endsWith(IPath path, IPath lastSegments) {
            if (path.segmentCount() < lastSegments.segmentCount()) {
                return false;
            }

            lastSegments = lastSegments.makeRelative().removeTrailingSeparator();

            while (!lastSegments.isEmpty()) {
                if (!path.lastSegment().equals(lastSegments.lastSegment())) {
                    return false;
                }

                path = path.removeLastSegments(1);
                lastSegments = lastSegments.removeLastSegments(1);
            }

            // all last segments match at this point
            return true;
        }

        private final IPath relativePath;
        private final IPath relativePathParent;
        private final IPath bazelPackageLocation;

        private IPath detectedPackagePath;

        public JavaSourceEntry(IPath relativePath, IPath bazelPackageLocation) {
            this.relativePath = relativePath;
            this.bazelPackageLocation = bazelPackageLocation;
            relativePathParent = relativePath.removeLastSegments(1).removeTrailingSeparator();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            var other = (JavaSourceEntry) obj;
            return Objects.equals(bazelPackageLocation, other.bazelPackageLocation)
                    && Objects.equals(relativePath, other.relativePath);
        }

        /**
         * {@return absolute location of of the container of this path entry}
         */
        public IPath getContainingFolderPath() {
            return bazelPackageLocation;
        }

        public IPath getDetectedPackagePath() {
            return requireNonNull(detectedPackagePath, "no package path detected");
        }

        /**
         * {@return the absolute path in the local file system, i.e. container path plus the relative path}
         */
        public IPath getLocation() {
            return bazelPackageLocation.append(relativePath);
        }

        /**
         * {@return the relative path within the container}
         */
        public IPath getPath() {
            return relativePath;
        }

        /**
         * {@return the parent folder path of <code>#getPath()</code>}
         */
        public IPath getPathParent() {
            return relativePathParent;
        }

        /**
         * @return first few segments of {@link #getPathParent()} which could be the source directory, or
         *         <code>null</code> if unlikely
         */
        public IPath getPotentialSourceDirectoryRoot() {
            var detectedPackagePath = getDetectedPackagePath();

            // note, we check the full path because we *want* to identify files from targets defined within a Java package
            if (endsWith(bazelPackageLocation.append(relativePathParent), detectedPackagePath)) {
                // this is safe call even when relativePathParent has less segments then detectedPackagePath
                return relativePathParent.removeLastSegments(detectedPackagePath.segmentCount());
            }

            return null; // not following Java package structure conventions
        }

        @Override
        public int hashCode() {
            return Objects.hash(bazelPackageLocation, relativePath);
        }

        @Override
        public String toString() {
            return relativePath + " (relativePathParent=" + relativePathParent + ", bazelPackageLocation="
                    + bazelPackageLocation + ", detectedPackagePath=" + detectedPackagePath + ")";
        }
    }

    /**
     * An entry pointing to another target, which may be generated output.
     */
    public static class LabelEntry implements Entry {

        private final BazelLabel label;

        public LabelEntry(BazelLabel label) {
            this.label = label;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            var other = (LabelEntry) obj;
            return Objects.equals(label, other.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(label);
        }

        @Override
        public String toString() {
            return label.toString();
        }

    }

    /**
     * A single file entry pointing to a single resource.
     */
    public static class ResourceEntry implements Entry {

        private final IPath relativePath;

        public ResourceEntry(IPath relativePath) {
            this.relativePath = relativePath;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            var other = (ResourceEntry) obj;
            return Objects.equals(relativePath, other.relativePath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(relativePath);
        }

        @Override
        public String toString() {
            return "Resource[" + relativePath + "]";
        }

    }

    private static final IPath INVALID = new Path("_not_following_ide_standards_");

    static boolean isJavaFile(java.nio.file.Path file) {
        return isRegularFile(file) && file.getFileName().toString().endsWith(".java");
    }

    private final BazelPackage bazelPackage;
    private final List<Entry> srcs = new ArrayList<>();
    private final List<Entry> resources = new ArrayList<>();
    private final List<LabelEntry> deps = new ArrayList<>();
    private final List<LabelEntry> runtimeDeps = new ArrayList<>();
    private final List<LabelEntry> pluginDeps = new ArrayList<>();

    private final List<Entry> testSrcs = new ArrayList<>();
    private final List<Entry> testResources = new ArrayList<>();
    private final List<LabelEntry> testDeps = new ArrayList<>();
    private final List<LabelEntry> testRuntimeDeps = new ArrayList<>();
    private final List<LabelEntry> testPluginDeps = new ArrayList<>();

    private final Map<IPath, IPath> detectedPackagePathsByFileEntryPathParent = new HashMap<>();

    /**
     * a map of all discovered source directors and their content (which may either be a {@link List} of
     * {@link JavaSourceEntry} or a single {@link GlobEntry}.
     */
    private Map<IPath, Object> sourceDirectoriesWithFilesOrGlobs;

    /**
     * a map of all discovered resource directors and their content (which may either be a {@link List} of
     * {@link ResourceEntry} or a single {@link GlobEntry}.
     */
    private Map<IPath, Object> resourceDirectoriesWithFilesOrGlobs;

    private List<JavaSourceEntry> sourceFilesWithoutCommonRoot;

    public JavaProjectInfo(BazelPackage bazelPackage) {
        this.bazelPackage = bazelPackage;
    }

    public void addDep(String label) {
        deps.add(new LabelEntry(new BazelLabel(label)));
    }

    /**
     * Adds all information from a target to the {@link JavaProjectInfo}.
     * <p>
     * Insertion order is maintained. Duplicate information is avoided.
     * </p>
     *
     * @param target
     * @throws CoreException
     */
    public void addInfoFromTarget(BazelTarget target) throws CoreException {
        var attributes = target.getRuleAttributes();
        var srcs = attributes.getStringList("srcs");
        if (srcs != null) {
            for (String src : srcs) {
                addSrc(src);
            }
        }

        var resources = attributes.getStringList("resources");
        if (resources != null) {
            for (String resource : resources) {
                addResource(resource);
            }
        }

        var deps = attributes.getStringList("deps");
        if (deps != null) {
            for (String dep : deps) {
                addDep(dep);
            }
        }

        var runtimeDeps = attributes.getStringList("runtime_deps");
        if (runtimeDeps != null) {
            for (String dep : runtimeDeps) {
                addRuntimeDep(dep);
            }
        }

        var pluginDeps = attributes.getStringList("plugins");
        if (pluginDeps != null) {
            for (String dep : runtimeDeps) {
                addPluginDep(dep);
            }
        }
    }

    private void addPluginDep(String label) {
        pluginDeps.add(new LabelEntry(new BazelLabel(label)));
    }

    /**
     * Adds a resources entry.
     * <p>
     * Insertion order is maintained.
     * </p>
     *
     * @param resourceFileOrLabel
     *            file or label
     * @throws CoreException
     */
    public void addResource(String resourceFileOrLabel) throws CoreException {
        resources.add(fileOrLabel(resourceFileOrLabel));
    }

    public void addRuntimeDep(String label) {
        runtimeDeps.add(new LabelEntry(new BazelLabel(label)));
    }

    /**
     * Adds a srcs glob entry.
     * <p>
     * Insertion order is maintained.
     * </p>
     *
     * @param relativeDirectoryPath
     * @param includePattern
     * @param excludePatterns
     * @throws CoreException
     */
    public void addSrc(GlobInfo globInfo) throws CoreException {
        addToSrc(this.srcs, globInfo);
    }

    /**
     * Adds a srcs entry.
     * <p>
     * Insertion order is maintained.
     * </p>
     *
     * @param srcFileOrLabel
     *            file or label
     * @throws CoreException
     */
    public void addSrc(String srcFileOrLabel) throws CoreException {
        addToSrc(this.srcs, srcFileOrLabel);
    }

    public void addTestSrc(GlobInfo globInfo) {
        addToSrc(this.testSrcs, globInfo);
    }

    public void addTestSrc(String srcFileOrLabel) throws CoreException {
        addToSrc(this.testSrcs, srcFileOrLabel);
    }

    private void addToSrc(List<Entry> srcs, GlobInfo globInfo) {
        for (String include : globInfo.include()) {
            var patternStart = include.indexOf('*');
            if (patternStart < 0) {
                throw new IllegalArgumentException(
                        "Invalid glob - missing '*' to start an include pattern: " + include);
            }

            // since this is coming from a glob, the path is expected to use '/'
            var directoryEnd = include.substring(0, patternStart).lastIndexOf('/');
            if (directoryEnd < 0) {
                srcs.add(new GlobEntry(IPath.EMPTY, include, globInfo.exclude()));
            } else {
                var directory =
                        IPath.forPosix(include.substring(0, directoryEnd)).makeRelative().removeTrailingSeparator();
                srcs.add(new GlobEntry(directory, include.substring(directoryEnd + 1), globInfo.exclude()));
            }
        }
    }

    private void addToSrc(List<Entry> srcs, String srcFileOrLabel) throws CoreException {
        srcs.add(fileOrLabel(srcFileOrLabel));
    }

    /**
     * Analyzes the gathered information for recommending a project setup suitable to local development.
     * <p>
     * This essentially implements some optimization hacks to detect things such as <code>glob</code> pointing to a
     * folder. We don't get this information from Bazel with <code>bazel query</code>.
     * </p>
     *
     * @param monitor
     *            monitor for reporting progress and tracking cancellation (never <code>null</code>)
     * @return a status (with at most one level of children) indicating potential problems (never <code>null</code>)
     * @throws CoreException
     * @todo Review if this belongs here or should be moved outside. It kind a meshs Bazel Java information with Eclipse
     *       project constraints.
     */
    @SuppressWarnings("unchecked")
    public IStatus analyzeProjectRecommendations(IProgressMonitor monitor) throws CoreException {
        var result = new MultiStatus(JavaProjectInfo.class, 0, "Java Analysis Result");

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
                result.add(Status.warning(format(
                    "Java file '%s' (with detected package '%s') does not meet IDE standards. Please move into a folder hierarchy which follows Java package structure!",
                    fileEntry.getPath(), fileEntry.getDetectedPackagePath())));
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
                        result.add(Status.error(format(
                            "It looks like source root '%s' is already mapped to a glob pattern. Please split into a separate targets. We cannot support this in the IDE.",
                            sourceDirectory)));
                    }
                }
            } else if (srcEntry instanceof GlobEntry globEntry) {
                if (sourceEntriesByParentFolder.containsKey(globEntry.getRelativeDirectoryPath())) {
                    result.add(Status.error(format(
                        "It looks like source root '%s' is already mapped to more than one glob pattern. Please split into a separate targets. We cannot support this in the IDE.",
                        globEntry.getRelativeDirectoryPath())));
                } else {
                    sourceEntriesBySourceRoot.put(globEntry.getRelativeDirectoryPath(), globEntry);
                }
            } else {
                // check if the source has label dependencies
                result.add(Status.warning(
                    format("Found source label reference '%s'. The project may not be fully supported in the IDE.",
                        srcEntry)));
            }
        }

        // discover folders that contain more .java files then declared in srcs
        // (this is a strong split-package indication)
        Set<IPath> potentialSplitPackageOrSubsetFolders = new HashSet<>();
        for (Map.Entry<IPath, List<JavaSourceEntry>> entry : sourceEntriesByParentFolder.entrySet()) {
            var entryParentPath = entry.getKey();
            var entryParentLocation = bazelPackage.getLocation().append(entryParentPath).toPath();
            var declaredJavaFilesInFolder = entry.getValue().size();
            try {
                // when there are declared Java files, expect them to match
                if (declaredJavaFilesInFolder > 0) {
                    var javaFilesInParent = Files.list(entryParentLocation).filter(JavaProjectInfo::isJavaFile).count();
                    if (javaFilesInParent != declaredJavaFilesInFolder) {
                        if (potentialSplitPackageOrSubsetFolders.add(entryParentPath)) {
                            result.add(Status.warning(format(
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

            var potentialSourceRootPath = bazelPackage.getLocation().append(potentialSourceRoot).toPath();
            try {
                var registeredFiles = ((List<?>) potentialSourceRootAndSourceEntries.getValue()).size();
                var foundJavaFilesInSourceRoot = find(potentialSourceRootPath, Integer.MAX_VALUE,
                    (p, a) -> isJavaFile(p), FileVisitOption.FOLLOW_LINKS).count();
                if ((registeredFiles != foundJavaFilesInSourceRoot)
                        && potentialSplitPackageOrSubsetFolders.add(potentialSourceRoot)) {
                    result.add(Status.warning(format(
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

        return result.isOK() ? Status.OK_STATUS : result;
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

    private Entry fileOrLabel(String srcFileOrLabel) throws CoreException {
        // test if this may be a file in this package
        var myPackagePath = bazelPackage.getLabel().toString();
        if (srcFileOrLabel.startsWith(myPackagePath + BazelLabel.BAZEL_COLON)) {
            // drop the package name to identify a reference within package
            srcFileOrLabel = srcFileOrLabel.substring(myPackagePath.length() + 1);
        }

        // starts with : then it must be treated as label
        if (srcFileOrLabel.startsWith(BazelLabel.BAZEL_COLON)) {
            return new LabelEntry(bazelPackage.getBazelTarget(srcFileOrLabel.substring(1)).getLabel());
        }

        // starts with // or @ then it must be treated as label
        if (srcFileOrLabel.startsWith(BazelLabel.BAZEL_ROOT_SLASHES)
                || srcFileOrLabel.startsWith(BazelLabel.BAZEL_EXTERNALREPO_AT)) {
            return new LabelEntry(new BazelLabel(srcFileOrLabel));
        }

        // doesn't start with // but contains one, unlikely a label!
        if (srcFileOrLabel.indexOf('/') >= 1) {
            return new JavaSourceEntry(new Path(srcFileOrLabel), bazelPackage.getLocation());
        }

        // treat as label if package has one matching the name
        if (bazelPackage.hasBazelTarget(srcFileOrLabel)) {
            return new LabelEntry(bazelPackage.getBazelTarget(srcFileOrLabel).getLabel());
        }

        // treat as file
        return new JavaSourceEntry(new Path(srcFileOrLabel), bazelPackage.getLocation());
    }

    /**
     * {@return the Bazel package used for computing paths}
     */
    public BazelPackage getBazelPackage() {
        return bazelPackage;
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
     * @param resourceDirectory
     *            the resource directory (must be contained in {@link #getSourceDirectories()})
     * @return the configured excludes (if the resource directory is based on a <code>glob</code>, empty list otherwise)
     */
    public List<String> getExcludePatternsForResourceDirectory(IPath resourceDirectory) {
        var fileOrGlob =
                requireNonNull(requireNonNull(resourceDirectoriesWithFilesOrGlobs, "no resource directories discovered")
                        .get(resourceDirectory),
                    () -> format("resource directory '%s' unknown", resourceDirectory));
        if (fileOrGlob instanceof GlobEntry globEntry) {
            var excludePatterns = globEntry.getExcludePatterns();
            return excludePatterns != null ? excludePatterns : Collections.emptyList();
        }

        // no info for none-globs
        return Collections.emptyList();
    }

    /**
     * @param sourceDirectory
     *            the source directory (must be contained in {@link #getSourceDirectories()})
     * @return the configured excludes (if the source directory is based on a <code>glob</code>, empty list otherwise)
     */
    public List<String> getExcludePatternsForSourceDirectory(IPath sourceDirectory) {
        var fileOrGlob = requireNonNull(
            requireNonNull(sourceDirectoriesWithFilesOrGlobs, "no source directories discovered").get(sourceDirectory),
            () -> format("source directory '%s' unknown", sourceDirectory));
        if (fileOrGlob instanceof GlobEntry globEntry) {
            var excludePatterns = globEntry.getExcludePatterns();
            return excludePatterns != null ? excludePatterns : Collections.emptyList();
        }

        // no info for none-globs
        return Collections.emptyList();
    }

    /**
     * @param resourceDirectory
     *            the resource directory (must be contained in {@link #getSourceDirectories()})
     * @return the configured include pattern (if the resource directory is based on a <code>glob</code>,
     *         <code>null</code> otherwise)
     */
    public String getIncludePatternForResourceDirectory(IPath resourceDirectory) {
        var fileOrGlob =
                requireNonNull(requireNonNull(resourceDirectoriesWithFilesOrGlobs, "no resource directories discovered")
                        .get(resourceDirectory),
                    () -> format("resource directory '%s' unknown", resourceDirectory));
        if (fileOrGlob instanceof GlobEntry globEntry) {
            return globEntry.getIncludePattern();
        }

        // no info for none-globs
        return null;
    }

    /**
     * {@return the list of detected source directories (relative to #getBazelPackage())}
     */
    public Collection<IPath> getSourceDirectories() {
        return requireNonNull(sourceDirectoriesWithFilesOrGlobs, "no source directories discovered").keySet();
    }

    public List<JavaSourceEntry> getSourceFilesWithoutCommonRoot() {
        return requireNonNull(sourceFilesWithoutCommonRoot, "no source files analyzed");
    }

    public Collection<GlobEntry> getTestSourceDirectories() {
        return testSrcs.stream().filter(GlobEntry.class::isInstance).map(GlobEntry.class::cast).collect(toList());
    }

    public boolean hasAnnotationProcessorPlugins() {
        return (pluginDeps != null) && !pluginDeps.isEmpty();
    }

    public boolean hasSourceDirectories() {
        return (sourceDirectoriesWithFilesOrGlobs != null) && !sourceDirectoriesWithFilesOrGlobs.isEmpty();
    }

    public boolean hasSourceFilesWithoutCommonRoot() {
        return (sourceFilesWithoutCommonRoot != null) && !sourceFilesWithoutCommonRoot.isEmpty();
    }

    public boolean hasTestSourceDirectories() {
        return testSrcs.stream().anyMatch(GlobEntry.class::isInstance);
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
