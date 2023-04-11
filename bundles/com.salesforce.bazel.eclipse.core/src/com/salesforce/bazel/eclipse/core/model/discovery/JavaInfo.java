package com.salesforce.bazel.eclipse.core.model.discovery;

import static java.lang.String.format;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.readString;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;

import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * Holds information for a Java configuration of a target or a package.
 */
public class JavaInfo {

    public static class Entry {
    }

    public static class FileEntry extends Entry {

        private final IPath relativePath;

        private final IPath relativePathParent;
        private final IPath containingFolderPath;
        private IPath detectedPackagePath;

        public FileEntry(IPath relativePath, IPath containingFolderPath) {
            this.relativePath = relativePath;
            this.containingFolderPath = containingFolderPath;
            relativePathParent = relativePath.removeLastSegments(1);
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

        public IPath getLocation() {
            return containingFolderPath.append(relativePath);
        }

        public IPath getPackageRoot() {
            return relativePathParent.removeLastSegments(getDetectedPackagePath().segmentCount());
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

        @Override
        public String toString() {
            return relativePath + " (relativePathParent=" + relativePathParent + ", containingFolderPath="
                    + containingFolderPath + ", detectedPackagePath=" + detectedPackagePath + ")";
        }
    }

    public static class LabelEntry extends Entry {

        private final BazelLabel label;

        public LabelEntry(BazelLabel label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label.toString();
        }

    }

    static boolean isJavaFile(java.nio.file.Path file) {
        return isRegularFile(file) && file.getFileName().toString().endsWith(".java");
    }

    private final BazelPackage bazelPackage;

    private final BazelWorkspace bazelWorkspace;
    private final List<Entry> srcs = new ArrayList<>();
    private final List<LabelEntry> deps = new ArrayList<>();

    private final List<LabelEntry> runtimeDeps = new ArrayList<>();
    private final Map<IPath, IPath> detectedPackagePathsByFileEntryPathParent = new HashMap<>();

    private List<FileEntry> sourceDirectories;
    private List<FileEntry> sourceFilesWithoutCommonRoot;

    public JavaInfo(BazelPackage bazelPackage, BazelWorkspace bazelWorkspace) {
        this.bazelPackage = bazelPackage;
        this.bazelWorkspace = bazelWorkspace;
    }

    public void addDep(String label) {
        deps.add(new LabelEntry(new BazelLabel(label)));
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
        // detect packages and build an index
        Map<IPath, List<FileEntry>> seenFileEntryPathParents = new HashMap<>();
        Set<IPath> potentialSourceDirectories = new LinkedHashSet<>(); // maintain insertion order
        var foundEmptyPackageRoot = false;
        for (Entry src : srcs) {
            if (src instanceof FileEntry fileEntry) {
                fileEntry.detectedPackagePath = detectPackagePath(fileEntry);
                seenFileEntryPathParents.putIfAbsent(fileEntry.getPathParent(), new ArrayList<>());
                seenFileEntryPathParents.get(fileEntry.getPathParent()).add(fileEntry);

                var packageRoot = fileEntry.getPackageRoot();
                if (packageRoot.makeRelative().isEmpty()) {
                    foundEmptyPackageRoot = true;
                } else {
                    potentialSourceDirectories.add(packageRoot);
                }
            }
        }

        // discover folders that contain more .java files then declared in srcs
        // (this is a strong split-package indication)
        var foundSplitPackageConfig = false;
        for (Map.Entry<IPath, List<FileEntry>> entry : seenFileEntryPathParents.entrySet()) {
            var javaFilesInSrcs = entry.getValue().size();
            if (javaFilesInSrcs == 0) {
                continue;
            }

            var entryParentLocation = bazelPackage.getLocation().append(entry.getKey()).toFile().toPath();
            try {
                var javaFilesInParent = Files.list(entryParentLocation).filter(JavaInfo::isJavaFile).count();
                if (javaFilesInParent != javaFilesInSrcs) {
                    foundSplitPackageConfig = true;
                    break; // abort early
                }
            } catch (IOException e) {
                throw new CoreException(Status.error(format("Error searching files in '%s'", entryParentLocation), e));
            }
        }

        // when there are no split packages *and* there is no empty package root we try to identify common source directories
        if (!foundSplitPackageConfig && !foundEmptyPackageRoot) {
            List<FileEntry> sourceDirectories = new ArrayList<>();
            for (IPath sourceDirectory : potentialSourceDirectories) {
                sourceDirectories.add(new FileEntry(sourceDirectory, bazelPackage.getLocation()));
            }
            this.sourceDirectories = sourceDirectories;
            sourceFilesWithoutCommonRoot = srcs.stream().filter(FileEntry.class::isInstance).map(FileEntry.class::cast)
                    .filter(e -> !potentialSourceDirectories.contains(e.getPackageRoot())).collect(toList());
        } else {
            sourceFilesWithoutCommonRoot =
                    srcs.stream().filter(FileEntry.class::isInstance).map(FileEntry.class::cast).collect(toList());
        }

        return Status.OK_STATUS;
    }

    private IPath detectPackagePath(FileEntry fileEntry) {
        // we inspect at most one file per directory (anything else is not too weird to support)
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

    public BazelWorkspace getBazelWorkspace() {
        return bazelWorkspace;
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
                    default:
                        token = scanner.getNextToken();
                        continue;
                    case ITerminalSymbols.TokenNameEOF:
                        return packageName;
                }
            }
        } catch (InvalidInputException | IndexOutOfBoundsException | IOException e) {
            // ignore
        }
        return packageName;
    }

}
