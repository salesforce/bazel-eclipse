package com.salesforce.bazel.sdk.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.salesforce.bazel.sdk.logging.LogHelper;

/**
 * This class contains only static methods, that interact with the file system to
 * provide information about bazel artifacts.
 */
public final class BazelDirectoryStructureUtil {

    private static final LogHelper LOG = LogHelper.log(BazelDirectoryStructureUtil.class);

    public static boolean isBazelPackage(File repositoryRoot, String possiblePackagePath) {
        Path rootPath = repositoryRoot.toPath();
        Path packagePath = rootPath.resolve(possiblePackagePath);
        for (String buildFileName : BazelConstants.BUILD_FILE_NAMES) {
            Path buildFilePath = packagePath.resolve(buildFileName);
            if (Files.isRegularFile(buildFilePath)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> findBazelPackages(File repositoryRoot, String relativeRepositoryPath) {
        Path rootPath = repositoryRoot.toPath();
        try {
            return
                Files.walk(rootPath.resolve(relativeRepositoryPath))
                    .filter(Files::isRegularFile)
                    .filter(p -> BazelConstants.BUILD_FILE_NAMES.contains(p.getFileName().toString()))
                    .map(Path::getParent)
                    .map(p -> rootPath.relativize(p).toString())
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            LOG.error("Failed to look for BUILD files at " + relativeRepositoryPath + ": " + ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    private BazelDirectoryStructureUtil() {
        throw new IllegalStateException("Not meant to be instantiated");
    }
}
