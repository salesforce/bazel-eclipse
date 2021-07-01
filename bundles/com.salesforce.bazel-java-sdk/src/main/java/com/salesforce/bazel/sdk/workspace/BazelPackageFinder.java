package com.salesforce.bazel.sdk.workspace;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.util.BazelConstants;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;

/**
 * Scanner for a Bazel workspace to locate BUILD files that contain rules that are supported by the SDK.
 */
public class BazelPackageFinder {
    LogHelper logger = LogHelper.log(this.getClass());

    public BazelPackageFinder() {}

    public void findBuildFileLocations(File dir, WorkProgressMonitor monitor, Set<File> buildFileLocations, int depth) {
        if (!dir.isDirectory()) {
            return;
        }

        try {

            // collect all BUILD files
            List<Path> buildFiles = new ArrayList<>(1000);

            Path start = dir.toPath();
            Files.walkFileTree(start, new FileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (start.relativize(dir).toString().startsWith("bazel-")) {
                        // this is a Bazel internal directory at the root of the project dir, ignore
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    if (dir.getFileName().toString().equals("target")) {
                        // skip Maven target directories
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    if (dir.getFileName().toString().equals(".bazel")) {
                        // skip Core .bazel directory
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (isBuildFile(file)) {
                        buildFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

            });

            // scan all build files
            // normally in the SDK we do not use Java streams, to make the code more accessible, but the parallel
            // streaming here really speeds up the file system scan
            Set<File> syncSet = Collections.synchronizedSet(buildFileLocations);
            buildFiles.parallelStream().forEach(file -> {
                // great, this dir is a Bazel package (but this may be a non-Java package)
                // scan the BUILD file looking for java rules, only add if this is a java project
                if (BuildFileSupport.hasRegisteredRules(file.toFile())) {
                    syncSet.add(FSPathHelper.getCanonicalFileSafely(file.getParent().toFile()));
                }
            });

        } catch (Exception anyE) {
            logger.error("ERROR scanning for Bazel packages: {}", anyE.getMessage());
        }
    }

    private static boolean isBuildFile(Path candidate) {
        return BazelConstants.BUILD_FILE_NAMES.contains(candidate.getFileName().toString());
    }

    public Set<File> findBuildFileLocations(File rootDirectoryFile) throws IOException {
        Set<File> files = ConcurrentHashMap.newKeySet();
        findBuildFileLocations(rootDirectoryFile, null, files, 0);
        return files;
    }
}
