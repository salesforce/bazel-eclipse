package com.salesforce.bazel.eclipse.core.model.discovery;

import static java.lang.String.format;
import static org.eclipse.core.runtime.IPath.forPosix;

import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact.LocalFileArtifact;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoderImpl;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.java.sync.importer.ExecutionPathHelper;
import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspaceBlazeInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.eclipse.core.util.jar.BazelJarFile;

/**
 * A utility for resolving jar information from Bazel for Eclipse classpath computation.
 */
public class JavaClasspathJarLocationResolver {

    private static Logger LOG = LoggerFactory.getLogger(JavaClasspathJarLocationResolver.class);

    protected final BazelWorkspace bazelWorkspace;
    protected final BlazeInfo blazeInfo;
    protected final WorkspaceRoot workspaceRoot;
    protected final ArtifactLocationDecoder locationDecoder;

    public JavaClasspathJarLocationResolver(BazelWorkspace bazelWorkspace) throws CoreException {
        this.bazelWorkspace = bazelWorkspace;

        blazeInfo = new BazelWorkspaceBlazeInfo(bazelWorkspace);
        workspaceRoot = new WorkspaceRoot(bazelWorkspace.getLocation().toPath());
        locationDecoder = new ArtifactLocationDecoderImpl(blazeInfo, new WorkspacePathResolverImpl(workspaceRoot));
    }

    /**
     * Convenience method to decode an {@link ArtifactLocation} using the {@link #getLocationDecoder()}.
     *
     * @param artifactLocation
     *            the {@link ArtifactLocation} (must not be <code>null</code>)
     * @return the absolute location on the file system (never <code>null</code>)
     * @throws IllegalStateException
     *             if the artifact location cannot be decoded
     */
    protected IPath decodeArtifactLocation(ArtifactLocation artifactLocation) {
        if (artifactLocation.isMainWorkspaceSourceArtifact()) {
            return forPosix(locationDecoder.resolveSource(artifactLocation).toString());
        }

        var blazeArtifact = locationDecoder.resolveOutput(artifactLocation);
        if (blazeArtifact instanceof LocalFileArtifact localArtifact) {
            return forPosix(localArtifact.getPath().toString());
        }

        throw new IllegalStateException(
                format(
                    "Unable to decode artifact location '%s'. It's is neither a source nor a local output artifact.",
                    artifactLocation));
    }

    public ArtifactLocation generatedJarLocation(BazelPackage bazelPackage, IPath jar) {
        // the jar is expected to be generated inside the given package
        var executionRootRelativePath = bazelPackage.getWorkspaceRelativePath().append(jar).toString();

        /*
         * The jar file is generated, we have to consume it from bazel-out/mnemonic/bin/...
         */
        var blazeInfo = getBlazeInfo();
        var bazelOutPrefix =
                blazeInfo.getExecutionRoot().relativize(blazeInfo.getBlazeBin().getAbsoluteOrRelativePath());
        executionRootRelativePath = format("%s/%s", bazelOutPrefix, executionRootRelativePath);

        return ExecutionPathHelper.parse(workspaceRoot, BazelBuildSystemProvider.BAZEL, executionRootRelativePath);
    }

    public BazelWorkspace getBazelWorkspace() {
        return bazelWorkspace;
    }

    public BlazeInfo getBlazeInfo() {
        return blazeInfo;
    }

    public ArtifactLocationDecoder getLocationDecoder() {
        return locationDecoder;
    }

    public WorkspaceRoot getWorkspaceRoot() {
        return workspaceRoot;
    }

    protected Label readTargetLabel(Path jarPath) {
        try (var jarFile = new BazelJarFile(jarPath)) {
            return jarFile.getTargetLabel();
        } catch (IOException e) {
            LOG.warn("Error inspecting manifest of jar '{}': {}", jarPath, e.getMessage(), e);
        }
        return null;
    }

    /**
     * Resolves a {@link LibraryArtifact jar} into a {@link ClasspathEntry}.
     * <p>
     * This method may be used whenever an external jar shall be converted into a {@link IClasspathEntry#CPE_LIBRARY
     * library} classpath entry. The workspace is used to locate the jar file.
     * </p>
     *
     * @param jar
     *            the jar
     * @return the classpath entry (maybe <code>null</code> in case resolution is not possible)
     */
    public ClasspathEntry resolveJar(LibraryArtifact jar) {
        // prefer the class jar because this is much better in Eclipse when debugging/stepping through code/code navigation/etc.
        var jarArtifactForIde = jar.getClassJar() != null ? jar.getClassJar() : jar.jarForIntellijLibrary();
        if (jarArtifactForIde.isMainWorkspaceSourceArtifact()) {
            var jarPath = forPosix(locationDecoder.resolveSource(jarArtifactForIde).toString());
            var sourceJar = jar.getSourceJars().stream().findFirst();
            if (!sourceJar.isPresent()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                        "Found jar for '{}': {} without source",
                        forPosix(jarArtifactForIde.getExecutionRootRelativePath()).lastSegment(),
                        jarPath);
                }
                return ClasspathEntry.newLibraryEntry(jarPath, null, null, false /* test only */);
            }

            var srcJarPath = forPosix(locationDecoder.resolveSource(sourceJar.get()).toString());
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                    "Found jar for '{}': {} (source {})",
                    forPosix(jarArtifactForIde.getExecutionRootRelativePath()).lastSegment(),
                    jarPath,
                    srcJarPath);
            }
            return ClasspathEntry.newLibraryEntry(jarPath, srcJarPath, null, false /* test only */);
        }
        var jarArtifact = locationDecoder.resolveOutput(jarArtifactForIde);
        if (jarArtifact instanceof LocalFileArtifact localJar) {
            var jarPath = forPosix(localJar.getPath().toString());
            var sourceJar = jar.getSourceJars().stream().findFirst();
            if (!sourceJar.isPresent()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found jar for '{}': {} without source", localJar.getPath().getFileName(), jarPath);
                }
                return ClasspathEntry.newLibraryEntry(jarPath, null, null, false /* test only */);
            }
            var srcJarArtifact = locationDecoder.resolveOutput(sourceJar.get());
            if (srcJarArtifact instanceof LocalFileArtifact localSrcJar) {
                var srcJarPath = forPosix(localSrcJar.getPath().toString());
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                        "Found jar for '{}': {} (source {})",
                        localJar.getPath().getFileName(),
                        jarPath,
                        srcJarPath);
                }
                return ClasspathEntry.newLibraryEntry(jarPath, srcJarPath, null, false /* test only */);
            }
        }
        return null;
    }

}
