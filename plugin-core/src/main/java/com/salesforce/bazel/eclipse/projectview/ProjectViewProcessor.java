package com.salesforce.bazel.eclipse.projectview;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.project.ProjectView;
import com.salesforce.bazel.sdk.project.ProjectViewPackageLocation;
import com.salesforce.bazel.sdk.util.BazelDirectoryStructureUtil;

final class ProjectViewProcessor {

    private static final LogHelper LOG = LogHelper.log(ProjectViewProcessor.class);

    static List<BazelPackageLocation> getInvalidDirectories(ProjectView projectView) {
        List<BazelPackageLocation> invalidDirectories = new ArrayList<>();
        File rootDir = projectView.getWorkspaceRootDirectory();
        for (BazelPackageLocation packageLocation : projectView.getDirectories()) {
            String packageDir = packageLocation.getBazelPackageFSRelativePath();
            if (!BazelDirectoryStructureUtil.isBazelPackage(rootDir, packageDir)) {
                invalidDirectories.add(packageLocation);
            }
        }
        return invalidDirectories;
    }

    static ProjectView resolvePackages(ProjectView projectView) {
        File rootDir = projectView.getWorkspaceRootDirectory();
        List<BazelPackageLocation> updatedDirectories = new ArrayList<>();
        for (BazelPackageLocation packageLocation : projectView.getDirectories()) {

            String directory = packageLocation.getBazelPackageFSRelativePath();
            if (BazelDirectoryStructureUtil.isBazelPackage(rootDir, directory)) {
                // no change, just add the same package
                updatedDirectories.add(packageLocation);
            } else {
                // look for BUILD files below this location
                List<String> additionalPackages = BazelDirectoryStructureUtil.findBazelPackages(rootDir, directory);
                LOG.info("Found " + additionalPackages.size() + " packages under " + directory);
                if (additionalPackages.isEmpty()) {
                    // add the original directory back, it will get flagged as invalid
                    updatedDirectories.add(packageLocation);
                } else {
                    updatedDirectories.addAll(
                        additionalPackages.stream()
                            .map(p -> new ProjectViewPackageLocation(rootDir, p))
                            .collect(Collectors.toList()));
                }
            }
        }
        return new ProjectView(rootDir, updatedDirectories, projectView.getTargets());
    }

}
