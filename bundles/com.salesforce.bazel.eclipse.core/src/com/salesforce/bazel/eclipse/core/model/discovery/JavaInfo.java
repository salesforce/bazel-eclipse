package com.salesforce.bazel.eclipse.core.model.discovery;

import static java.lang.String.format;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.readString;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
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
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * Holds information for a Java configuration of a target or a package.
 * <p>
 * The info is initialized with a {@link BazelPackage}. This package will be used to resolve paths when needed. However,
 * nothing from the package is initially contributing to the {@link JavaInfo}. Instead, callers must call
 * {@link #addInfoFromTarget(BazelTarget)} to populate the {@link JavaInfo} with all information available from a
 * {@link BazelTarget}.
 * </p>
 * <p>
 * The {@link JavaInfo} maintains a stable order. Things added first remain first. Duplicate items will be eliminated.
 * </p>
 */
public class JavaInfo {

    public interface Entry {
    }

    public static class FileEntry implements Entry {

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
                lastSegments.removeLastSegments(1);
            }

            // all last segments match at this point
            return true;
        }

        private final IPath relativePath;

        private final IPath relativePathParent;

        private final IPath containingFolderPath;

        private IPath detectedPackagePath;

        public FileEntry(IPath relativePath, IPath containingFolderPath) {
            this.relativePath = relativePath;
            this.containingFolderPath = containingFolderPath;
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
            var other = (FileEntry) obj;
            return Objects.equals(containingFolderPath, other.containingFolderPath)
                    && Objects.equals(relativePath, other.relativePath);
        }

        /**
         * {@return absolute location of of the container of this path entry}
         */
        public IPath getContainingFolderPath() {
            return containingFolderPath;
        }

        public IPath getDetectedPackagePath() {
            return requireNonNull(detectedPackagePath, "no package path detected");
        }

        /**
         * {@return the absolute path in the local file system, i.e. container path plus the relative path}
         */
        public IPath getLocation() {
            return containingFolderPath.append(relativePath);
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
            if (endsWith(containingFolderPath.append(relativePathParent), detectedPackagePath)) {
                // this is safe call even when relativePathParent has less segments then detectedPackagePath
                return relativePathParent.removeLastSegments(detectedPackagePath.segmentCount());
            }

            return null; // not following Java package structure conventions
        }

        @Override
        public int hashCode() {
            return Objects.hash(containingFolderPath, relativePath);
        }

        @Override
        public String toString() {
            return relativePath + " (relativePathParent=" + relativePathParent + ", containingFolderPath="
                    + containingFolderPath + ", detectedPackagePath=" + detectedPackagePath + ")";
        }
    }

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

    private static final Path INVALID = new Path("_not_following_ide_standards_");

    static boolean isJavaFile(java.nio.file.Path file) {
        return isRegularFile(file) && file.getFileName().toString().endsWith(".java");
    }

    private final BazelPackage bazelPackage;
    private final List<Entry> srcs = new ArrayList<>();
    private final List<LabelEntry> deps = new ArrayList<>();
    private final List<LabelEntry> runtimeDeps = new ArrayList<>();
    private final Map<IPath, IPath> detectedPackagePathsByFileEntryPathParent = new HashMap<>();

    private List<FileEntry> sourceDirectories;
    private List<FileEntry> sourceFilesWithoutCommonRoot;

    public JavaInfo(BazelPackage bazelPackage) {
        this.bazelPackage = bazelPackage;
    }

    public void addDep(String label) {
        deps.add(new LabelEntry(new BazelLabel(label)));
    }

    /**
     * Adds all information from a target to the {@link JavaInfo}.
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
    }

    public void addRuntimeDep(String label) {
        runtimeDeps.add(new LabelEntry(new BazelLabel(label)));
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
    public IStatus analyzeProjectRecommendations(IProgressMonitor monitor) throws CoreException {
        var result = new MultiStatus(JavaInfo.class, 0, "Java Analysis Result");

        // build an index of all source files and their parent directories (does not need to maintain order)
        Map<IPath, List<FileEntry>> sourceEntriesByParentFolder = new HashMap<>();

        // group by potential source roots
        Function<FileEntry, IPath> groupingByPotentialSourceRoots = fileEntry -> {
            // detect package if necessary
            if (fileEntry.detectedPackagePath == null) {
                fileEntry.detectedPackagePath = detectPackagePath(fileEntry);
            }

            // calculate potential source root
            var potentialSourceDirectoryRoot = fileEntry.getPotentialSourceDirectoryRoot();
            if (potentialSourceDirectoryRoot == null) {
                result.add(Status.warning(format(
                    "Java file '%s' (with detected package '%s') does not meet IDE standards. Please move into a folder hierarchy which follow Java package structure!",
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
        LinkedHashMap<IPath, List<FileEntry>> sourceEntriesBySourceRoot =
                srcs.stream().filter(FileEntry.class::isInstance).map(FileEntry.class::cast)
                        .collect(groupingBy(groupingByPotentialSourceRoots, LinkedHashMap::new, toList()));

        // discover folders that contain more .java files then declared in srcs
        // (this is a strong split-package indication)
        Set<IPath> potentialSplitPackageFolders = new HashSet<>();
        for (Map.Entry<IPath, List<FileEntry>> entry : sourceEntriesByParentFolder.entrySet()) {
            var declaredJavaFilesInFolder = entry.getValue().size();
            if (declaredJavaFilesInFolder == 0) {
                continue; // don't look at folders without Java files
            }

            var entryParentPath = entry.getKey();
            var entryParentLocation = bazelPackage.getLocation().append(entryParentPath).toFile().toPath();
            try {
                var javaFilesInParent = Files.list(entryParentLocation).filter(JavaInfo::isJavaFile).count();
                if (javaFilesInParent != declaredJavaFilesInFolder) {
                    if (potentialSplitPackageFolders.add(entryParentPath)) {
                        result.add(Status.warning(format(
                            "Folder '%s' contains more Java source files then configured in Bazel. This is a split-package scenario which is challenging to support in IDEs! Consider re-structuring your source code into separate folder hierarchies or packages.",
                            entryParentPath)));
                    }
                    continue; // continue with next so we capture all possible warnings (we could also abort, though)
                }
            } catch (IOException e) {
                throw new CoreException(Status.error(format("Error searching files in '%s'", entryParentLocation), e));
            }
        }

        // when there are no split packages we found a good setup
        // (if there are multiple source roots we could do an extra effort and try to filter the ones without split packages; but is this worth supporting?)
        if (potentialSplitPackageFolders.isEmpty()) {
            // create source directories
            this.sourceDirectories = sourceEntriesBySourceRoot.keySet().stream().filter(p -> p != INVALID)
                    .map(p -> new FileEntry(p, bazelPackage.getLocation())).collect(toList());

            // collect remaining files without a root
            if (sourceEntriesBySourceRoot.containsKey(INVALID)) {
                sourceFilesWithoutCommonRoot = sourceEntriesBySourceRoot.get(INVALID).stream().collect(toList());
            }
        } else {
            sourceFilesWithoutCommonRoot =
                    srcs.stream().filter(FileEntry.class::isInstance).map(FileEntry.class::cast).collect(toList());
        }

        return result.isOK() ? Status.OK_STATUS : result;
    }

    private IPath detectPackagePath(FileEntry fileEntry) {
        // we inspect at most one file per directory (anything else is too weird to support)
        var previouslyDetectedPackagePath = detectedPackagePathsByFileEntryPathParent.get(fileEntry.getPathParent());
        if (previouslyDetectedPackagePath != null) {
            return previouslyDetectedPackagePath;
        }

        // assume empty by default
        IPath packagePath = Path.EMPTY;
        var packageName = readPackageName(fileEntry);
        if (packageName.length > 0) {
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
            return new FileEntry(new Path(srcFileOrLabel), bazelPackage.getLocation());
        }

        // treat as label if package has one matching the name
        if (bazelPackage.hasBazelTarget(srcFileOrLabel)) {
            return new LabelEntry(bazelPackage.getBazelTarget(srcFileOrLabel).getLabel());
        }

        // treat as file
        return new FileEntry(new Path(srcFileOrLabel), bazelPackage.getLocation());
    }

    /**
     * @return the Bazel package used for computing paths
     */
    public BazelPackage getBazelPackage() {
        return bazelPackage;
    }

    public List<FileEntry> getSourceDirectories() {
        return requireNonNull(sourceDirectories, "no source directories discovered");
    }

    public List<FileEntry> getSourceFilesWithoutCommonRoot() {
        return requireNonNull(sourceFilesWithoutCommonRoot, "no source files analyzed");
    }

    public boolean hasSourceDirectories() {
        return (sourceDirectories != null) && !sourceDirectories.isEmpty();
    }

    public boolean hasSourceFilesWithoutCommonRoot() {
        return (sourceFilesWithoutCommonRoot != null) && !sourceFilesWithoutCommonRoot.isEmpty();
    }

    @SuppressWarnings("deprecation") // use of TokenNameIdentifier is ok here
    private char[] readPackageName(FileEntry fileEntry) {
        char[] packageName = {};

        var scanner = ToolFactory.createScanner( //
            false, // tokenizeComments
            false, // tokenizeWhiteSpace
            false, // assertMode
            false // recordLineSeparator
        );
        try {
            var content = readString(fileEntry.getLocation().toFile().toPath());
            scanner.setSource(content.toCharArray());

            var token = scanner.getNextToken();
            while (true) {
                switch (token) {
                    case ITerminalSymbols.TokenNamepackage:
                        token = scanner.getNextToken();
                        if (token == ITerminalSymbols.TokenNameIdentifier) {
                            packageName = scanner.getCurrentTokenSource();
                        }
                        return packageName;
                    case ITerminalSymbols.TokenNameimport: // stop at imports
                    case ITerminalSymbols.TokenNameEOF: // stop at EOF
                        return packageName;
                    default:
                        token = scanner.getNextToken();
                        continue;
                }
            }
        } catch (InvalidInputException | IndexOutOfBoundsException | IOException e) {
            // ignore
        }
        return packageName;
    }

}
