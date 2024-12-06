package com.salesforce.bazel.eclipse.core.model.discovery.projects;

import static java.nio.file.Files.isRegularFile;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.eclipse.core.runtime.IPath.forPosix;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.idea.blaze.base.model.primitives.Label;
import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.buildfile.GlobInfo;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * Holds information for computing Java project configuration of a target or a package.
 * <p>
 * The info is initialized with a {@link BazelPackage}. This package will be used to resolve paths when needed. However,
 * nothing from the package is initially contributing to the {@link JavaProjectInfo}. Instead, callers must call the
 * various add methods to populate the project info. Once done, {@link #analyzeProjectRecommendations(IProgressMonitor)}
 * should be called to apply heuristics for computing a recommended project layout.
 * </p>
 * <p>
 * The {@link JavaProjectInfo} maintains a stable order. Things added first remain first. Duplicate items will be
 * eliminated.
 * </p>
 */
public class JavaProjectInfo {

    private static Logger LOG = LoggerFactory.getLogger(JavaProjectInfo.class);

    private final BazelPackage bazelPackage;

    private final LinkedHashMap<Entry, EntrySettings> srcs = new LinkedHashMap<>();
    private final LinkedHashSet<Entry> resources = new LinkedHashSet<>();

    private final LinkedHashMap<Entry, EntrySettings> testSrcs = new LinkedHashMap<>();
    private final LinkedHashSet<Entry> testResources = new LinkedHashSet<>();

    /** key = jar, value = srcjar (optional, maybe <code>null</code>) */
    private final LinkedHashMap<Entry, Entry> jars = new LinkedHashMap<>();
    /** key = jar, value = srcjar (optional, maybe <code>null</code>) */
    private final LinkedHashMap<Entry, Entry> testJars = new LinkedHashMap<>();

    private final LinkedHashSet<LabelEntry> pluginDeps = new LinkedHashSet<>();
    private final LinkedHashSet<String> javacOpts = new LinkedHashSet<>();

    private JavaSourceInfo sourceInfo;
    private JavaSourceInfo testSourceInfo;
    private JavaResourceInfo resourceInfo;
    private JavaResourceInfo testResourceInfo;

    private JavaArchiveInfo jarInfo;
    private JavaArchiveInfo testJarInfo;

    private JavaPluginInfo pluginInfo;

    public JavaProjectInfo(BazelPackage bazelPackage) {
        this.bazelPackage = bazelPackage;
    }

    /**
     * Adds a jars entry.
     * <p>
     * Insertion order is maintained.
     * </p>
     *
     * @param jarFileOrLabel
     *            file or label
     * @param srcJarFileOrLabel
     *            file or label (optional, maybe <code>null</code>)
     * @throws CoreException
     */
    public void addJar(String jarFileOrLabel, String srcJarFileOrLabel) throws CoreException {
        jars.put(
            toResourceFileOrLabelEntry(jarFileOrLabel, null),
            srcJarFileOrLabel != null ? toResourceFileOrLabelEntry(srcJarFileOrLabel, null) : null);
    }

    public void addJavacOpt(String javacOpt) {
        javacOpts.add(javacOpt);
    }

    public void addPluginDep(String label) throws CoreException {
        pluginDeps.add(new LabelEntry(new BazelLabel(label)));
    }

    public void addResource(GlobInfo globInfo) throws CoreException {
        addToResources(resources, globInfo);
    }

    /**
     * Adds a resources entry.
     * <p>
     * Insertion order is maintained.
     * </p>
     *
     * @param resourceFileOrLabel
     *            file or label
     * @param resourceStripPrefix
     *            the resource_strip_prefix attribute value (maybe <code>null</code> if not set)
     * @throws CoreException
     */
    public void addResource(String resourceFileOrLabel, String resourceStripPrefix) throws CoreException {
        addToResources(resources, resourceFileOrLabel, resourceStripPrefix);
    }

    /**
     * Adds a srcs glob entry.
     * <p>
     * Insertion order is maintained.
     * </p>
     *
     * @param globInfo
     *            the gliob info
     * @param entrySettings
     *            optional entry setting (maybe <code>null</code>)
     * @throws CoreException
     */
    public void addSrc(GlobInfo globInfo, EntrySettings entrySettings) throws CoreException {
        addToSrc(srcs, globInfo, entrySettings);
    }

    /**
     * Adds a srcs entry.
     * <p>
     * Insertion order is maintained.
     * </p>
     *
     * @param srcFileOrLabel
     *            file or label
     * @param entrySettings
     *            optional entry setting (maybe <code>null</code>)
     * @throws CoreException
     */
    public void addSrc(String srcFileOrLabel, EntrySettings entrySettings) throws CoreException {
        addToSrc(srcs, srcFileOrLabel, entrySettings);
    }

    public void addTestJar(String jarFileOrLabel, String srcJarFileOrLabel) throws CoreException {
        testJars.put(
            toResourceFileOrLabelEntry(jarFileOrLabel, null),
            srcJarFileOrLabel != null ? toResourceFileOrLabelEntry(srcJarFileOrLabel, null) : null);
    }

    public void addTestResource(GlobInfo globInfo) throws CoreException {
        addToResources(testResources, globInfo);
    }

    /**
     * Adds a resources entry for test classpath.
     * <p>
     * Insertion order is maintained.
     * </p>
     *
     * @param resourceFileOrLabel
     *            file or label
     * @param resourceStripPrefix
     *            the resource_strip_prefix attribute value (maybe <code>null</code> if not set)
     * @throws CoreException
     */
    public void addTestResource(String resourceFileOrLabel, String resourceStripPrefix) throws CoreException {
        addToResources(testResources, resourceFileOrLabel, resourceStripPrefix);
    }

    public void addTestSrc(GlobInfo globInfo, EntrySettings entrySettings) {
        addToSrc(testSrcs, globInfo, entrySettings);
    }

    public void addTestSrc(String srcFileOrLabel, EntrySettings entrySettings) throws CoreException {
        addToSrc(testSrcs, srcFileOrLabel, entrySettings);
    }

    private void addToResources(Collection<Entry> resources, GlobInfo globInfo) {
        resources.add(toGlobEntry(globInfo));
    }

    private void addToResources(Collection<Entry> resources, String resourceFileOrLabel, String resourceStripPrefix)
            throws CoreException {
        resources.add(toResourceFileOrLabelEntry(resourceFileOrLabel, resourceStripPrefix));
    }

    private void addToSrc(Map<Entry, EntrySettings> srcs, GlobInfo globInfo, EntrySettings entrySettings) {
        srcs.put(toGlobEntry(globInfo), entrySettings != null ? entrySettings : EntrySettings.DEFAULT_SETTINGS);
    }

    private void addToSrc(Map<Entry, EntrySettings> srcs, String srcFileOrLabel, EntrySettings entrySettings)
            throws CoreException {
        srcs.put(
            toJavaSourceFileOrLabelEntry(srcFileOrLabel),
            entrySettings != null ? entrySettings : EntrySettings.DEFAULT_SETTINGS);
    }

    /**
     * Analyzes the gathered information for recommending a project setup suitable to local development.
     * <p>
     * This essentially implements some optimization hacks to detect things such as <code>glob</code> pointing to a
     * folder. We don't get this information from Bazel with <code>bazel query</code>.
     * </p>
     *
     * @param reportSourceFoldersWithMoreJavaSourcesThanDeclaredAsProblem
     *            <code>true</code> to report source folders with more Java files then expected as a problem,
     *            <code>false</code> otherwise
     * @param monitor
     *            monitor for reporting progress and tracking cancellation (never <code>null</code>)
     * @return a status (with at most one level of children) indicating potential problems (never <code>null</code>)
     * @throws CoreException
     * @todo Review if this belongs here or should be moved outside. It kind a meshs Bazel Java information with Eclipse
     *       project constraints.
     */
    public IStatus analyzeProjectRecommendations(boolean reportSourceFoldersWithMoreJavaSourcesThanDeclaredAsProblem,
            IProgressMonitor monitor) throws CoreException {
        var result = new MultiStatus(JavaProjectInfo.class, 0, "Java Analysis Result");

        sourceInfo = new JavaSourceInfo(srcs, bazelPackage);
        sourceInfo.analyzeSourceDirectories(result, reportSourceFoldersWithMoreJavaSourcesThanDeclaredAsProblem);

        resourceInfo = new JavaResourceInfo(resources, bazelPackage);
        resourceInfo.analyzeResourceDirectories(result);

        jarInfo = new JavaArchiveInfo(jars, bazelPackage);
        jarInfo.analyzeJars(result);

        testSourceInfo = new JavaSourceInfo(testSrcs, bazelPackage, sourceInfo);
        testSourceInfo.analyzeSourceDirectories(result, reportSourceFoldersWithMoreJavaSourcesThanDeclaredAsProblem);

        testResourceInfo = new JavaResourceInfo(testResources, bazelPackage);
        testResourceInfo.analyzeResourceDirectories(result);

        testJarInfo = new JavaArchiveInfo(testJars, bazelPackage);
        testJarInfo.analyzeJars(result);

        pluginInfo = new JavaPluginInfo(pluginDeps, bazelPackage);
        pluginInfo.analyzeJars(result);

        return result.isOK() ? Status.OK_STATUS : result;
    }

    /**
     * {@return the Bazel package used for computing paths}
     */
    public BazelPackage getBazelPackage() {
        return bazelPackage;
    }

    public JavaArchiveInfo getJarInfo() {
        return requireNonNull(jarInfo, "Jar info not computed. Did you call analyzeProjectRecommendations?");
    }

    public Collection<String> getJavacOpts() {
        return javacOpts;
    }

    public Collection<LabelEntry> getPluginDeps() {
        return pluginDeps;
    }

    public JavaResourceInfo getResourceInfo() {
        return resourceInfo;
    }

    public JavaSourceInfo getSourceInfo() {
        return requireNonNull(sourceInfo, "Source info not computed. Did you call analyzeProjectRecommendations?");
    }

    public JavaArchiveInfo getTestJarInfo() {
        return requireNonNull(testJarInfo, "Test jar info not computed. Did you call analyzeProjectRecommendations?");
    }

    public JavaResourceInfo getTestResourceInfo() {
        return testResourceInfo;
    }

    public JavaSourceInfo getTestSourceInfo() {
        return requireNonNull(
            testSourceInfo,
            "Test source info not computed. Did you call analyzeProjectRecommendations?");
    }

    /**
     * Checks whether the specified string is a label pointing into this package of this project and make it relative in
     * this case.
     * <p>
     * Otherwise the passed in value is returned.
     * </p>
     *
     * @param srcFileOrLabel
     *            maybe label
     * @return the passed in value or a relative path to a file in this package
     */
    private String relativizeLabelToPackageIfPossible(String srcFileOrLabel) {
        var myPackageLabel = bazelPackage.getLabel().toString();
        if (srcFileOrLabel.startsWith(myPackageLabel + BazelLabel.BAZEL_COLON)) {
            // drop the package name to identify a reference within package
            return srcFileOrLabel.substring(myPackageLabel.length() + 1);
        }

        // return original passed in value
        return srcFileOrLabel;
    }

    /**
     * Indicates if the passed in string should be treated as a label (<code>true</code>) or file in this package
     * (<code>false</code>).
     *
     * @param srcFileOrLabel
     *            path to file or label
     * @return <code>true</code> if the specified string should be treated as label, <code>false</code> otherwise
     */
    private boolean shouldTreatAsLabel(String srcFileOrLabel) {
        /*
         *  Treating as file vs label technically requires querying Bazel to be correct.
         *  However, we would like to avoid querying Bazel because it may have a performance impact.
         *  Therefore, we apply some heuristics to decide whether if it is a label or a file:
         *
         *  It can only be a label if:
         *  - starts with ':' (relative label) OR
         *  - starts with '@' or '//' OR
         *  - it does validate ok as label
         *     - AND it does not look like a path
         *     - AND no such a file exists
         */

        if (srcFileOrLabel.startsWith(BazelLabel.BAZEL_COLON)
                || srcFileOrLabel.startsWith(BazelLabel.BAZEL_ROOT_SLASHES)
                || srcFileOrLabel.startsWith(BazelLabel.BAZEL_EXTERNALREPO_AT)) {
            return true;
        }

        return (Label.validate(srcFileOrLabel) == null) && (srcFileOrLabel.indexOf('/') == -1)
                && !isRegularFile(bazelPackage.getLocation().append(new Path(srcFileOrLabel)).toPath());
    }

    private GlobEntry toGlobEntry(GlobInfo globInfo) {
        // the first include determines the path
        var includes = globInfo.include();
        if (includes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid glob. Need one include pattern for detecting the source directory path.");
        }

        var include = includes.iterator().next();
        var patternStart = include.indexOf('*');
        if (patternStart < 0) {
            throw new IllegalArgumentException("Invalid glob - missing '*' to start an include pattern: " + include);
        }

        // since this is coming from a glob, the path is expected to use '/'
        var directoryEnd = include.substring(0, patternStart).lastIndexOf('/');
        if (directoryEnd < 0) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using empty source directory for glob '{}' to package '{}'", globInfo, bazelPackage);
            }
            return new GlobEntry(IPath.EMPTY, globInfo.include(), globInfo.exclude());
        }

        // remove the directory from all includes if present
        var directory = forPosix(include.substring(0, directoryEnd)).makeRelative().removeTrailingSeparator();
        List<String> includePatterns = globInfo.include()
                .stream()
                .map(s -> s.startsWith(directory.toString()) ? s.substring(directory.toString().length()) : s)
                .collect(toList());
        if (LOG.isDebugEnabled()) {
            LOG.debug(
                "Using directory '{}' with includes '{}' for glob '{}' to package '{}'",
                directory,
                includePatterns,
                globInfo,
                bazelPackage);
        }
        return new GlobEntry(directory, includePatterns, globInfo.exclude());
    }

    private Entry toJavaSourceFileOrLabelEntry(String srcFileOrLabel) throws CoreException {
        // test if this may be a file in this package
        srcFileOrLabel = relativizeLabelToPackageIfPossible(srcFileOrLabel);

        // treat as label if it looks like one
        if (shouldTreatAsLabel(srcFileOrLabel)) {
            return new LabelEntry(bazelPackage.getBazelTarget(srcFileOrLabel).getLabel());
        }

        // treat as file
        return new JavaSourceEntry(new Path(srcFileOrLabel), bazelPackage.getLocation());
    }

    private Entry toResourceFileOrLabelEntry(String srcFileOrLabel, String resourceStripPrefix) throws CoreException {
        srcFileOrLabel = relativizeLabelToPackageIfPossible(srcFileOrLabel);

        // treat as label if it looks like one
        if (shouldTreatAsLabel(srcFileOrLabel)) {
            return new LabelEntry(bazelPackage.getBazelTarget(srcFileOrLabel).getLabel());
        }

        // treat as file
        return new ResourceEntry(
                forPosix(srcFileOrLabel),
                resourceStripPrefix != null ? forPosix(resourceStripPrefix) : IPath.EMPTY);
    }

}
