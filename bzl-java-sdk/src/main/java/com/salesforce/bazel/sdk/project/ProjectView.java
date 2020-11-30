package com.salesforce.bazel.sdk.project;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.util.BazelConstants;

/**
 * The <a href="http://ij.bazel.build/docs/project-views.html">project view</a> file.
 * </p>
 * This implementations currently only supports a subset of the functionality provided by the IntelliJ Bazel Plugin,
 * namely these sections:
 * </p>
 * <ul>
 * <li>directories</li>
 * </ul>
 *
 * Example project view file:
 *
 * <pre>
 * directories:
 *   path/to/bazel/package1
 *   path/to/bazel/package2
 *
 * targets:
 *   # the targets section is optional
 *   //path/to/bazel/package1:t1
 * </pre>
 *
 * Exclusions are not supported yet.
 *
 * @author stoens
 * @since March 2020
 */
public class ProjectView {

    static String DIRECTORIES_SECTION = "directories:";
    static String TARGETS_SECTION = "targets:";
    static String DIRECTORIES_COMMENT = "# Add the directories you want added as source here";
    static String INDENT = "  ";

    private final File rootWorkspaceDirectory;
    private final Map<BazelPackageLocation, Integer> packageToLineNumber;
    private final Map<BazelLabel, Integer> targetToLineNumber;

    /**
     * Create a new ProjectView instance with the specified directories and targets.
     */
    public ProjectView(File rootWorkspaceDirectory, List<BazelPackageLocation> directories, List<BazelLabel> targets) {
        this.rootWorkspaceDirectory = rootWorkspaceDirectory;
        Map<BazelPackageLocation, Integer> pl = new LinkedHashMap<>();
        Map<BazelLabel, Integer> tl = new LinkedHashMap<>();
        initSections(directories, targets, pl, tl);
        this.packageToLineNumber = Collections.unmodifiableMap(pl);
        // this may get modified, so the map has to be mutable
        this.targetToLineNumber = tl;
    }

    /**
     * Creates a new ProjectView instance with the specified raw content.
     */
    public ProjectView(File rootWorkspaceDirectory, String content) {
        this.rootWorkspaceDirectory = rootWorkspaceDirectory;
        Map<BazelPackageLocation, Integer> pl = new LinkedHashMap<>();
        Map<BazelLabel, Integer> tl = new LinkedHashMap<>();
        parseSections(content, rootWorkspaceDirectory, pl, tl);
        this.packageToLineNumber = Collections.unmodifiableMap(pl);
        // this may get modified, so the map has to be mutable
        this.targetToLineNumber = tl;
    }

    /**
     * Returns the raw project view file content.
     */
    public String getContent() {
        StringBuilder sb = new StringBuilder();
        sb.append(DIRECTORIES_SECTION).append(System.lineSeparator());
        sb.append(INDENT).append(DIRECTORIES_COMMENT).append(System.lineSeparator());
        for (BazelPackageLocation pack : packageToLineNumber.keySet()) {
            sb.append(INDENT).append(pack.getBazelPackageFSRelativePath()).append(System.lineSeparator());
        }
        if (!targetToLineNumber.isEmpty()) {
            sb.append(System.lineSeparator());
            sb.append(TARGETS_SECTION).append(System.lineSeparator());
            for (BazelLabel target : targetToLineNumber.keySet()) {
                sb.append(INDENT).append(target.getLabel()).append(System.lineSeparator());
            }
        }
        return sb.toString();
    }

    /**
     * Returns the line number, in the raw project view file content, of the specified bazel package, in the
     * "directories" section.
     */
    public int getLineNumber(BazelPackageLocation pack) {
        Integer lineNumber = packageToLineNumber.get(pack);
        if (lineNumber == null) {
            throw new IllegalArgumentException("Unknown " + pack);
        }
        return lineNumber;
    }

    /**
     * Returns the directories with their targets.
     */
    public List<BazelPackageLocation> getDirectories() {
        List<BazelPackageLocation> updatedPackageLocations = new ArrayList<>(packageToLineNumber.size());
        for (BazelPackageLocation packageInfo : packageToLineNumber.keySet()) {
            String directory = packageInfo.getBazelPackageFSRelativePath();
            List<BazelLabel> targets = getTargetsForDirectory(directory);
            updatedPackageLocations.add(new ProjectViewPackageLocation(rootWorkspaceDirectory, directory, targets));
        }
        return Collections.unmodifiableList(updatedPackageLocations);
    }

    /**
     * Returns only the targets from the targets: section.
     */
    public List<BazelLabel> getTargets() {
        return Collections.unmodifiableList(new ArrayList<>(targetToLineNumber.keySet()));
    }

    /**
     * Adds the default targets for each directory that does not have one (or more) entries
     * in the "targets:" section.
     */
    public void addDefaultTargets() {
        List<BazelLabel> defaultLabels = new ArrayList<>();
        for (BazelPackageLocation directory : packageToLineNumber.keySet()) {
            boolean foundLabel = false;
            BazelLabel bazelPackage = new BazelLabel(directory.getBazelPackageFSRelativePath());
            for (BazelLabel label : targetToLineNumber.keySet()) {
                if (label.getPackagePath().equals(bazelPackage.getPackagePath())) {
                    foundLabel = true;
                    break;
                }
            }
            if (!foundLabel) {
                for (String target : BazelConstants.DEFAULT_PACKAGE_TARGETS) {
                    defaultLabels.add(new BazelLabel(bazelPackage.getPackagePath(), target));
                }
            }
        }
        for (BazelLabel dflt : defaultLabels) {
            // since this method is used to adjust internal state, it is ok for the
            // line number to not be correct.
            targetToLineNumber.put(dflt, 0);
        }
    }

    public File getWorkspaceRootDirectory() {
        return rootWorkspaceDirectory;
    }

    @Override
    public int hashCode() {
        return packageToLineNumber.keySet().hashCode() ^ targetToLineNumber.keySet().hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProjectView)) {
            return false;
        }
        ProjectView o = (ProjectView)other;
        return packageToLineNumber.keySet().equals(o.packageToLineNumber.keySet()) &&
                targetToLineNumber.keySet().equals(o.targetToLineNumber.keySet());
    }

    private List<BazelLabel> getTargetsForDirectory(String directory) {
        List<BazelLabel> targets = null;
        BazelLabel bazelPackage = new BazelLabel(directory);
        for (BazelLabel target : targetToLineNumber.keySet()) {
            if (target.getPackagePath().equals(bazelPackage.getPackagePath())) {
                if (targets == null) {
                    targets = new ArrayList<>();
                }
                targets.add(target);
            }
        }
        return targets;
    }

    private static void initSections(List<BazelPackageLocation> packages, List<BazelLabel> targets,
            Map<BazelPackageLocation, Integer> packageToLineNumber,
            Map<BazelLabel, Integer> targetToLineNumber)
    {
        // directories:
        //   # comment
        // therefore:
        int lineNumber = 3;
        for (BazelPackageLocation pack : packages) {
            packageToLineNumber.put(pack, lineNumber);
            lineNumber += 1;
        }
        // newline
        lineNumber += 1;
        for (BazelLabel target : targets) {
            targetToLineNumber.put(target, lineNumber);
            lineNumber += 1;
        }
    }

    private static void parseSections(String content, File rootWorkspaceDirectory,
            Map<BazelPackageLocation, Integer> packageToLineNumber,
            Map<BazelLabel, Integer> targetToLineNumber)
    {
        boolean withinDirectoriesSection = false;
        boolean withinTargetsSection = false;
        int lineNumber = 0;
        for (String line : content.split(System.lineSeparator())) {
            lineNumber += 1;
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("#")) {
                continue;
            }
            if (line.equals(DIRECTORIES_SECTION)) {
                withinDirectoriesSection = true;
                withinTargetsSection = false;
                continue;
            } else if (line.equals(TARGETS_SECTION)) {
                withinDirectoriesSection = false;
                withinTargetsSection = true;
                continue;
            } else if (line.endsWith(":")) {
                // some other yet unknown section
                withinDirectoriesSection = false;
                withinTargetsSection = false;
                continue;
            }
            if (withinDirectoriesSection) {
                packageToLineNumber.put(new ProjectViewPackageLocation(rootWorkspaceDirectory, line), lineNumber);
            } else if (withinTargetsSection) {
                targetToLineNumber.put(new BazelLabel(line), lineNumber);
            }
        }
    }


}
