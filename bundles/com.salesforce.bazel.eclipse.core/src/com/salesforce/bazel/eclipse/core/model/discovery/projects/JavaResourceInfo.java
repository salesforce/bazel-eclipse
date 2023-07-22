package com.salesforce.bazel.eclipse.core.model.discovery.projects;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;

import com.salesforce.bazel.eclipse.core.model.BazelPackage;

/**
 * Similar to {@link JavaSourceInfo} this class provides resource information to {@link JavaProjectInfo}.
 * <p>
 * Unfortunately it's much more difficult to provide recommendations because resources don't hava Java package
 * information. Thus, it's challenging to compute common resource root directories.
 * </p>
 */
public class JavaResourceInfo {

    private static final IPath INVALID = new Path("_not_following_ide_standards_");

    private final Collection<Entry> resources;
    private final BazelPackage bazelPackage;
    private final Map<IPath, IPath> detectedRootPathsByFileEntryPathParent = new HashMap<>();

    /**
     * a map of all discovered resource directors and their content (which may either be a {@link List} of
     * {@link ResourceEntry} or a single {@link GlobEntry}.
     */
    private Map<IPath, Object> resourceDirectoriesWithFilesOrGlobs;

    private List<ResourceEntry> resourceFilesWithoutCommonRoot;

    /**
     * @param resources
     *            the list of resources to analyze
     * @param bazelPackage
     *            the {@link BazelPackage} which defines the scope of any analysis
     */
    public JavaResourceInfo(Collection<Entry> resources, BazelPackage bazelPackage) {
        this.resources = resources;
        this.bazelPackage = bazelPackage;
    }

    /**
     * Analyzes a list of resource for common resource root locations
     *
     * @param result
     *            a multi status for collecting problems
     * @throws CoreException
     */
    @SuppressWarnings("unchecked")
    public void analyzeResourceDirectories(MultiStatus result) throws CoreException {
        // build an index of all source files and their parent directories (does not need to maintain order)
        Map<IPath, List<ResourceEntry>> resourceEntriesByParentFolder = new HashMap<>();

        // group by potential source roots
        Function<ResourceEntry, IPath> groupingByPotentialSourceRoots = fileEntry -> {
            // detect package if necessary
            if (fileEntry.detectedRootPath == null) {
                try {
                    fileEntry.detectedRootPath = detectRootPath(fileEntry);
                } catch (IllegalStateException e) {
                    result.add(Status.error(format("%s We cannot support this in the IDE.", e.getMessage())));
                    return INVALID;
                }
            }

            // return the potential source root (relative)
            return fileEntry.detectedRootPath.makeRelative().removeTrailingSeparator();
        };

        // collect the potential list of resource directories
        var resourceEntriesBySourceRoot = new LinkedHashMap<IPath, Object>();
        for (Entry entry : resources) {
            if (entry instanceof ResourceEntry resourceFile) {
                var resourceDirectory = groupingByPotentialSourceRoots.apply(resourceFile);
                if (!resourceEntriesBySourceRoot.containsKey(resourceDirectory)) {
                    var list = new ArrayList<>();
                    list.add(resourceFile);
                    resourceEntriesBySourceRoot.put(resourceDirectory, list);
                } else {
                    var maybeList = resourceEntriesBySourceRoot.get(resourceDirectory);
                    if (maybeList instanceof List list) {
                        list.add(resourceFile);
                    } else {
                        result.add(
                            Status.error(
                                format(
                                    "It looks like resource root '%s' is already mapped to a glob pattern. Please split into a separate targets. We cannot support this in the IDE.",
                                    resourceDirectory)));
                    }
                }
            } else if (entry instanceof GlobEntry globEntry) {
                if (resourceEntriesByParentFolder.containsKey(globEntry.getRelativeDirectoryPath())) {
                    result.add(
                        Status.error(
                            format(
                                "It looks like resource root '%s' is already mapped to more than one glob pattern. Please split into a separate targets. We cannot support this in the IDE.",
                                globEntry.getRelativeDirectoryPath())));
                } else {
                    resourceEntriesBySourceRoot.put(globEntry.getRelativeDirectoryPath(), globEntry);
                }
            } else {
                // check if the resource has label dependencies
                result.add(
                    Status.warning(
                        format(
                            "Found resource label reference '%s'. The project may not be fully supported in the IDE.",
                            entry)));
            }
        }

        // we don't check for split package scenarios in resources
        // if this happens the user should re-structure the targets

        // remove invalid from the result
        if (resourceEntriesBySourceRoot.containsKey(INVALID)) {
            resourceFilesWithoutCommonRoot = (List<ResourceEntry>) resourceEntriesBySourceRoot.remove(INVALID);
        }

        // create resource directories
        this.resourceDirectoriesWithFilesOrGlobs = resourceEntriesBySourceRoot;
    }

    private IPath detectRootPath(ResourceEntry fileEntry) {
        // we inspect at most one file per directory (anything else is too weird to support)
        var fileEntryParent = fileEntry.getRelativePath().removeLastSegments(1);
        var previouslyDetectedRootPathForParent = detectedRootPathsByFileEntryPathParent.get(fileEntryParent);
        if (previouslyDetectedRootPathForParent != null) {
            return previouslyDetectedRootPathForParent;
        }

        // assume empty by default
        IPath rootPath = Path.EMPTY;

        /*
         * From the Bazel documentation (https://bazel.build/reference/be/java#java_library):
         *
         * The location of the resources inside of the jar file is determined by the project structure.
         * Bazel first looks for Maven's standard directory layout, (a "src" directory followed by a "resources" directory grandchild).
         * If that is not found, Bazel then looks for the topmost directory named "java" or "javatests" (so, for example, if a resource
         * is at <workspace root>/x/java/y/java/z, the path of the resource will be y/java/z. This heuristic cannot be overridden,
         * however, the resource_strip_prefix attribute can be used to specify a specific alternative directory for resource files.
         */

        // if there is a resource_strip_prefix this is easy
        var resourceStripPrefix = fileEntry.getResourceStripPrefix();
        if ((resourceStripPrefix != null) && !resourceStripPrefix.isEmpty()) {
            var relativePackagePath = getBazelPackage().getWorkspaceRelativePath();
            if (!relativePackagePath.isPrefixOf(resourceStripPrefix)) {
                throw new IllegalStateException(
                        format(
                            "Found a resource_strip_prefix which is outside the expected package location '{}': {}",
                            relativePackagePath,
                            resourceStripPrefix));
            }

            rootPath = resourceStripPrefix.removeFirstSegments(relativePackagePath.segmentCount());
        } else {
            // search for src/*/resources
            rootPath = searchForBazelSupportedStructure(fileEntry);
        }

        // remember in cache
        detectedRootPathsByFileEntryPathParent.put(fileEntryParent, rootPath);

        return rootPath;
    }

    public BazelPackage getBazelPackage() {
        return bazelPackage;
    }

    public IPath getBazelPackageLocation() {
        return getBazelPackage().getLocation();
    }

    /**
     * @param resourceDirectory
     *            the resource directory (must be contained in {@link #getResourceDirectories()})
     * @return the configured excludes (if the resource directory is based on a <code>glob</code>, <code>null</code> if
     *         nothing should be excluded <code>glob</code>)
     */
    public IPath[] getExclutionPatternsForSourceDirectory(IPath resourceDirectory) {
        var fileOrGlob = requireNonNull(
            requireNonNull(resourceDirectoriesWithFilesOrGlobs, "no resource directories discovered")
                    .get(resourceDirectory),
            () -> format("resource directory '%s' unknown", resourceDirectory));
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
     * @param resourceDirectory
     *            the resource directory (must be contained in {@link #getResourceDirectories()})
     * @return the configured includes (if the resource directory is based on a <code>glob</code>, <code>null</code> if
     *         everything should be included)
     */
    public IPath[] getInclusionPatternsForSourceDirectory(IPath resourceDirectory) {
        var fileOrGlob = requireNonNull(
            requireNonNull(resourceDirectoriesWithFilesOrGlobs, "no source directories discovered")
                    .get(resourceDirectory),
            () -> format("source directory '%s' unknown", resourceDirectory));
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
     * {@return the list of detected resource directories (relative to #getBazelPackageLocation())}
     */
    public Collection<IPath> getResourceDirectories() {
        return requireNonNull(resourceDirectoriesWithFilesOrGlobs, "no resource directories discovered").keySet();
    }

    public boolean hasResourceDirectories() {
        return (resourceDirectoriesWithFilesOrGlobs != null) && !resourceDirectoriesWithFilesOrGlobs.isEmpty();
    }

    public boolean hasResourceFilesWithoutCommonRoot() {
        return (resourceFilesWithoutCommonRoot != null) && !resourceFilesWithoutCommonRoot.isEmpty();
    }

    /**
     * Implements heuristic from Bazel looking for <code>src&#47;*&#47;resources</code>.
     *
     * @param fileEntry
     * @return the detected path (maybe empty if not found)
     */
    private IPath searchForBazelSupportedStructure(ResourceEntry fileEntry) {
        IPath path = Path.EMPTY;
        var segments = fileEntry.getRelativePath().segments();
        for (var i = 0; i < segments.length; i++) {
            var segment = segments[i];

            // check for 'src' and look ahead
            if ("src".equals(segment) && ((i + 2) < segments.length) && "resources".equals(segments[i + 2])) {
                // we have a root path
                return path.append(segment).append(segments[i + 1]).append(segments[i + 2]);
            }

            // we cannnot search outside the package for "java" or "javatests" but we try here
            if ("java".equals(segment) || "javatests".equals(segment)) {
                // use this and abort
                return path.append(segment);
            }

            // continue search
            path = path.append(segment);
        }

        // nothing found means empty
        return Path.EMPTY;
    }

}
