package com.salesforce.bazel.eclipse.core.model.discovery.classpath.libs;

import static java.lang.String.format;
import static java.nio.file.Files.isRegularFile;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Rule;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.java.sync.importer.ExecutionPathHelper;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.discovery.JavaClasspathJarLocationResolver;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;

public class LibrariesDiscoveryUtil {

    private static Logger LOG = LoggerFactory.getLogger(LibrariesDiscoveryUtil.class);

    protected final BazelWorkspace bazelWorkspace;
    protected final WorkspaceRoot workspaceRoot;
    protected final JavaClasspathJarLocationResolver locationResolver;

    private boolean foundMissingJars;

    public LibrariesDiscoveryUtil(BazelWorkspace bazelWorkspace) throws CoreException {
        this.bazelWorkspace = bazelWorkspace;
        this.workspaceRoot = new WorkspaceRoot(bazelWorkspace.getLocation().toPath());
        this.locationResolver = new JavaClasspathJarLocationResolver(bazelWorkspace);
    }

    /**
     * Processes each jar and collects them into the result.
     * <p>
     * IO will be done to check whether a jar exists on the file system.
     * </p>
     *
     * @param jars
     *            the list of jars
     * @param srcJar
     *            a source jar to be used for all jars
     * @param testOnly
     *            whether this goes onto the test or regular classpath
     * @param origin
     *            origin label to be stored with the {@link ClasspathEntry} as attribute
     * @param result
     *            the collection to collect classpath entries into
     */
    protected void collectJarsAsClasspathEntries(List<ArtifactLocation> jars, ArtifactLocation srcJar,
            Optional<Boolean> testOnly, Label origin, Collection<ClasspathEntry> result) {
        for (ArtifactLocation artifactLocation : jars) {
            var library = LibraryArtifact.builder().setClassJar(artifactLocation);
            if (srcJar != null) {
                library.addSourceJar(srcJar);
            }
            var classpath = locationResolver.resolveJar(library.build());
            if (classpath != null) {
                if (isRegularFile(classpath.getPath().toPath())) {
                    if (testOnly.isPresent() && testOnly.get()) {
                        classpath.getExtraAttributes().put(IClasspathAttribute.TEST, Boolean.TRUE.toString());
                    }
                    classpath.setBazelTargetOrigin(origin);
                    result.add(classpath);
                } else {
                    foundMissingJars = true;
                }
            }
        }
    }

    protected Optional<Boolean> findBooleanAttribute(Rule rule, String attributeName) {
        return rule.getAttributeList()
                .stream()
                .filter(a -> a.getName().equals(attributeName))
                .map(Build.Attribute::getBooleanValue)
                .findAny();
    }

    protected List<ArtifactLocation> findJars(Rule rule, String attributeName, boolean generated) {
        List<ArtifactLocation> jars = new ArrayList<>();
        rule.getAttributeList()
                .stream()
                .filter(a -> a.getName().equals(attributeName))
                .map(Build.Attribute::getStringListValueList)
                .collect(toList())
                .forEach(list -> list.forEach(jar -> {
                    var jarArtifact = jarLabelToArtifactLocation(jar, generated);
                    if (jarArtifact != null) {
                        jars.add(jarArtifact);
                    }
                }));
        return jars;
    }

    protected ArtifactLocation findSingleJar(Rule rule, String attributeName, boolean isGenerated) {
        var attribute = rule.getAttributeList().stream().filter(a -> a.getName().equals(attributeName)).findAny();
        if (attribute.isEmpty()) {
            return null;
        }

        return jarLabelToArtifactLocation(attribute.get().getStringValue(), isGenerated);
    }

    public BazelWorkspace getBazelWorkspace() {
        return bazelWorkspace;
    }

    /**
     * Attempts to resolve a file label into either <code>external/</code> or some other path to be found either in
     * bazel-bin or the exec-root.
     *
     * @param fileLabel
     *            the file labe
     * @return the hypothetical relative path of the consumable artifact
     */
    private String getHypotheticalRelativePathOfConsumableArtifact(Label fileLabel) {
        var execRootRelativPath = new StringBuilder();
        var externalWorkspaceName = fileLabel.externalWorkspaceName();
        if ((externalWorkspaceName != null) && !externalWorkspaceName.isBlank()) {
            execRootRelativPath.append("external/").append(externalWorkspaceName).append('/');
        }
        var blazePackage = fileLabel.blazePackage();
        if (!blazePackage.isWorkspaceRoot()) {
            execRootRelativPath.append(blazePackage.relativePath()).append('/');
        }
        execRootRelativPath.append(fileLabel.targetName());
        return blazePackage.toString();
    }

    /**
     * @return <code>true</code> if there jars were omitted from the result because they cannot be found locally
     *         (<code>false</code> otherwise)
     */
    public boolean isFoundMissingJars() {
        return foundMissingJars;
    }

    protected ArtifactLocation jarLabelToArtifactLocation(String jar, boolean isGenerated) {
        if ((jar == null) || jar.isBlank()) {
            return null;
        }
        if (Label.validate(jar) != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Ignoring invalid label: {}", jar);
            }
            return null;
        }
        var jarLabel = Label.create(jar);

        String executionRootRelativePath;
        if (!isGenerated) {
            // if the artifact is not generated then there is an expectation that the relative path is relative to the execroot
            // we can consume it directly from  <execroot>/...
            executionRootRelativePath = getHypotheticalRelativePathOfConsumableArtifact(jarLabel);
        } else {
            /*
             * otherwise jar file cannot be consume it directly from <execroot>/...
             * instead we have to consume it from bazel-out/mnemonic/bin/...
             */
            var blazeInfo = locationResolver.getBlazeInfo();
            var bazelOutPrefix =
                    blazeInfo.getExecutionRoot().relativize(blazeInfo.getBlazeBin().getAbsoluteOrRelativePath());
            executionRootRelativePath =
                    format("%s/%s", bazelOutPrefix, getHypotheticalRelativePathOfConsumableArtifact(jarLabel));
        }

        return ExecutionPathHelper.parse(workspaceRoot, BazelBuildSystemProvider.BAZEL, executionRootRelativePath);
    }

}