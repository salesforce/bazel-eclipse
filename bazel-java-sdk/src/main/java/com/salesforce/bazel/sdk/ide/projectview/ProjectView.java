package com.salesforce.bazel.sdk.ide.projectview;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.salesforce.bazel.sdk.model.BazelPackageLocation;

/**
 * The <a href="http://ij.bazel.build/docs/project-views.html">project view</a> file. 
 * </p>
 * This implementations currently only supports a subset of the functionality provided by the IntelliJ Bazel Plugin, namely these sections:
 * </p>
 * <ul>
 * <li>directories</li>
 * </ul>
 * 
 * Example project view file:
 * <pre>
 * directories:
 *   path/to/bazel/package/1
 *   path/to/bazel/package/2
 * </pre>
 * 
 * @author stoens
 * @since March 2020
 */
public class ProjectView {
    
    static String DIRECTORIES_COMMENT = "# Add the directories you want added as source here";
    static String INDENT = "  ";
    
    private final File rootWorkspaceDirectory;
    private final Map<BazelPackageLocation, Integer> bazelPackageToLineNumber;
    
    /**
     * Create a new ProjectView instance with the specified bazel packages added to the "directories" section.
     */
    public ProjectView(File rootWorkspaceDirectory, List<BazelPackageLocation> packages) {
        this.rootWorkspaceDirectory = rootWorkspaceDirectory;
        this.bazelPackageToLineNumber = init(packages);
    }

    /**
     * Creates a new ProjectView instance with the specified raw content.
     */
    public ProjectView(File rootWorkspaceDirectory, String content) {
        this.rootWorkspaceDirectory = rootWorkspaceDirectory;
        this.bazelPackageToLineNumber = parse(content, rootWorkspaceDirectory);
    }

    /**
     * Returns the raw project view file content.
     */
    public String getContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("directories:").append(System.lineSeparator());
        sb.append(INDENT).append(DIRECTORIES_COMMENT).append(System.lineSeparator());
        for (BazelPackageLocation pack : this.bazelPackageToLineNumber.keySet()) {
            sb.append(INDENT)
                .append(pack.getBazelPackageFSRelativePath())
                .append(System.lineSeparator());
        } 
        return sb.toString();        
    }

    /**
     * Returns the line number, in the raw project view file content, of the specified bazel package, in the "directories" section.
     */
    public int getLineNumber(BazelPackageLocation pack) {
        Integer lineNumber = bazelPackageToLineNumber.get(pack);
        if (lineNumber == null) {
            throw new IllegalArgumentException("Unknown " + pack);
        }
        return lineNumber;
    } 

    /**
     * Returns all invalid bazel packages from the "directories" section.
     */
    public List<BazelPackageLocation> getInvalidPackages() {
        List<BazelPackageLocation> invalidPackages = new ArrayList<>();
        for (BazelPackageLocation pack : bazelPackageToLineNumber.keySet()) {
            boolean packageIsValid = false;
            for (String buildFileName : new String[]{"BUILD", "BUILD.bazel"}) {
                // this should go through bazel (aspect machinery?) instead of directly checking for BUILD file presence on the fs
                File b = new File(rootWorkspaceDirectory, Paths.get(pack.getBazelPackageFSRelativePath(), buildFileName).toString());
                if (b.exists() && b.isFile()) {
                    packageIsValid = true;
                    break;
                }
            }
            if (!packageIsValid) {
                invalidPackages.add(pack);
            }
        }
        return invalidPackages;
    }

    /**
     * Returns all bazel packages from the "directories" section.
     */
    public List<BazelPackageLocation> getPackages() {
        return new ArrayList<>(this.bazelPackageToLineNumber.keySet());
    }

    private static Map<BazelPackageLocation, Integer> init(List<BazelPackageLocation> packages) {
        // directories:
        //   # comment
        // therefore:
        int lineNumber = 3;
        Map<BazelPackageLocation, Integer> bazelPackageToLineNumber = new LinkedHashMap<>(packages.size());
        for (BazelPackageLocation pack : packages) {
            bazelPackageToLineNumber.put(pack, lineNumber);
            
        }
        return Collections.unmodifiableMap(bazelPackageToLineNumber);
    }

    private static Map<BazelPackageLocation, Integer> parse(String content, File rootWorkspaceDirectory) {       
        Map<BazelPackageLocation, Integer> bazelPackageToLineNumber = new LinkedHashMap<>(); // preserve insertion order
        boolean withinDirectoriesSection = false;
        int lineNumber = 0;
        for (String line : content.split(System.lineSeparator())) {
            lineNumber += 1;
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.endsWith(":")) {
                if (withinDirectoriesSection) {
                    break;
                } else if (line.equals("directories:")) {
                    withinDirectoriesSection = true;
                    continue;
                }
            }
            if (line.startsWith("#")) {
                continue;
            }
            if (withinDirectoriesSection) {
                bazelPackageToLineNumber.put(new ProjectViewPackageLocation(rootWorkspaceDirectory, line), lineNumber);
            }
        }
        return Collections.unmodifiableMap(bazelPackageToLineNumber);
    }
}
