package com.salesforce.bazel.eclipse.core.model.discovery.classpath.libs;

import static java.lang.String.format;
import static java.nio.file.Files.isRegularFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.java.sync.importer.ExecutionPathHelper;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.discovery.JavaClasspathJarLocationResolver;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.sdk.model.RuleInternal;

public class LibrariesDiscoveryUtil {

    private static Logger LOG = LoggerFactory.getLogger(LibrariesDiscoveryUtil.class);

    protected final BazelWorkspace bazelWorkspace;
    protected final WorkspaceRoot workspaceRoot;
    protected final JavaClasspathJarLocationResolver locationResolver;
    protected Path outputBase;

    private boolean foundMissingJars;
    private boolean foundMissingSrcJars;

    public LibrariesDiscoveryUtil(BazelWorkspace bazelWorkspace) throws CoreException {
        this.bazelWorkspace = bazelWorkspace;
        workspaceRoot = new WorkspaceRoot(bazelWorkspace.getLocation().toPath());
        locationResolver = new JavaClasspathJarLocationResolver(bazelWorkspace);
        outputBase = bazelWorkspace.getOutputBaseLocation().toPath();
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
                    if ((classpath.getSourceAttachmentPath() != null)
                            && !isRegularFile(classpath.getSourceAttachmentPath().toPath())) {
                        foundMissingSrcJars = true;
                    }
                    result.add(classpath);
                } else {
                    foundMissingJars = true;
                }
            }
        }
    }

    protected Optional<Boolean> findBooleanAttribute(RuleInternal rule, String attributeName) {
        var attribute = rule.getAttribute(attributeName);
        if (attribute != null) {
            return Optional.of(attribute.booleanValue());
        }
        return Optional.empty();
    }

    protected List<ArtifactLocation> findJars(RuleInternal rule, String attributeName, boolean generated) {
        List<ArtifactLocation> jars = new ArrayList<>();
        rule.getAttribute(attributeName).stringListValue().forEach(jar -> {
            var jarArtifact = jarLabelToArtifactLocation(jar, generated);
            if (jarArtifact != null) {
                jars.add(jarArtifact);
            }
        });
        return jars;
    }

    protected ArtifactLocation findSingleJar(RuleInternal rule, String attributeName, boolean isGenerated) {
        var attribute = rule.getAttribute(attributeName);
        if (attribute == null) {
            return null;
        }

        return jarLabelToArtifactLocation(attribute.stringValue(), isGenerated);
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
        return execRootRelativPath.toString();
    }

    /**
     * This is the inverse of {@link #getHypotheticalRelativePathOfConsumableArtifact(Label)}. It highly depends on
     * Bazel implementation details and will break if the change it.
     *
     * @param jarPath
     *            the jar location (typically absolute)
     * @return the Label (maybe <code>null</code>)
     */
    protected Label guessJarLabelFromLocation(Path jarPath) {
        if (jarPath.isAbsolute()) {
            // make relative
            var blazeInfo = locationResolver.getBlazeInfo();
            var executionRoot = blazeInfo.getExecutionRoot();
            var bazelOutPrefix = blazeInfo.getBlazeBin().getPathRootedAt(executionRoot);

            if (jarPath.startsWith(bazelOutPrefix)) {
                jarPath = bazelOutPrefix.relativize(jarPath);
            } else if (jarPath.startsWith(executionRoot)) {
                jarPath = blazeInfo.getExecutionRoot().relativize(jarPath);
            } else if (jarPath.startsWith(outputBase)) {
                // the 'external' folder is actually a sibling of execroot, let's try this
                jarPath = outputBase.relativize(jarPath);
            } else {
                // not in this workspace
                LOG.warn(
                    "Path '{}' outside of workspace '{}' execution root. Please check setup and/or report bug!",
                    jarPath,
                    executionRoot);
                return null;
            }
        }

        var segments = jarPath.getNameCount();
        if (segments == 0) {
            LOG.warn("Path '{}' is empty. Please check setup and/or report bug!", jarPath);
            return null;
        }
        if (segments == 1) {
            return Label.create(new WorkspacePath(""), TargetName.create(jarPath.getName(0).toString()));
        }

        // at least two
        var firstSegment = jarPath.getName(0).toString();
        var secondSegment = jarPath.getName(1).toString();

        String externalWorkspaceName = null;
        if ("external".equals(firstSegment)) {
            externalWorkspaceName = secondSegment;
            // adjust remaining path
            jarPath = jarPath.subpath(2, jarPath.getNameCount());
            segments = jarPath.getNameCount();
        }

        if (segments == 0) {
            LOG.warn("Path '{}' is empty. Please check setup and/or report bug!", jarPath);
            return null;
        }
        if (segments == 1) {
            return Label.create(
                externalWorkspaceName,
                new WorkspacePath(""),
                TargetName.create(jarPath.getName(0).toString()));
        }

        // everything else is a lottery - we need to lookup from the workspace classpath to guess correctly
        // hence we make the following assumption - last segment is the target name, everything else is package path

        var packagePath = jarPath.subpath(0, segments - 1);
        var jarName = jarPath.getName(segments - 1);

        return Label.create(
            externalWorkspaceName,
            new WorkspacePath(IPath.fromPath(packagePath).toString() /* Bazel expects '/' as separator */),
            TargetName.create(jarName.toString()));
    }

    /**
     * @return <code>true</code> if jars were omitted from the result because they cannot be found locally
     *         (<code>false</code> otherwise)
     */
    public boolean isFoundMissingJars() {
        return foundMissingJars;
    }

    /**
     * @return <code>true</code> if source jars were discovered which cannot be found locally (<code>false</code>
     *         otherwise)
     */
    public boolean isFoundMissingSrcJars() {
        return foundMissingSrcJars;
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