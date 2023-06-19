package com.salesforce.bazel.eclipse.core.model.discovery;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.idea.blaze.base.command.buildresult.BlazeArtifact.LocalFileArtifact;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoderImpl;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspaceBlazeInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;

/**
 * A utility for resolving jar information from Bazel for Eclipse classpath computation.
 */
public class JavaClasspathJarLocationResolver {

    private static Logger LOG = LoggerFactory.getLogger(JavaClasspathJarLocationResolver.class);

    protected final BazelWorkspace bazelWorkspace;
    protected WorkspaceRoot workspaceRoot;
    protected ArtifactLocationDecoder locationDecoder;

    public JavaClasspathJarLocationResolver(BazelWorkspace bazelWorkspace) throws CoreException {
        this.bazelWorkspace = bazelWorkspace;

        workspaceRoot = new WorkspaceRoot(bazelWorkspace.getLocation().toPath());
        locationDecoder = new ArtifactLocationDecoderImpl(new BazelWorkspaceBlazeInfo(bazelWorkspace),
                new WorkspacePathResolverImpl(workspaceRoot));
    }

    public BazelWorkspace getBazelWorkspace() {
        return bazelWorkspace;
    }

    public ArtifactLocationDecoder getLocationDecoder() {
        return locationDecoder;
    }

    public WorkspaceRoot getWorkspaceRoot() {
        return workspaceRoot;
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
        var jarArtifactForIde = jar.jarForIntellijLibrary();
        if (jarArtifactForIde.isMainWorkspaceSourceArtifact()) {
            IPath jarPath = new Path(locationDecoder.resolveSource(jarArtifactForIde).toString());
            var sourceJar = jar.getSourceJars().stream().findFirst();
            if (!sourceJar.isPresent()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found jar for '{}': {} without source",
                        new Path(jarArtifactForIde.getExecutionRootRelativePath()).lastSegment(), jarPath);
                }
                return ClasspathEntry.newLibraryEntry(jarPath, null, null, false /* test only */);
            }

            IPath srcJarPath = new Path(locationDecoder.resolveSource(sourceJar.get()).toString());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found jar for '{}': {} (source {})",
                    new Path(jarArtifactForIde.getExecutionRootRelativePath()).lastSegment(), jarPath, srcJarPath);
            }
            return ClasspathEntry.newLibraryEntry(jarPath, srcJarPath, null, false /* test only */);
        }
        var jarArtifact = locationDecoder.resolveOutput(jarArtifactForIde);
        if (jarArtifact instanceof LocalFileArtifact localJar) {
            IPath jarPath = new Path(localJar.getPath().toString());
            var sourceJar = jar.getSourceJars().stream().findFirst();
            if (!sourceJar.isPresent()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found jar for '{}': {} without source", localJar.getPath().getFileName(), jarPath);
                }
                return ClasspathEntry.newLibraryEntry(jarPath, null, null, false /* test only */);
            }
            var srcJarArtifact = locationDecoder.resolveOutput(sourceJar.get());
            if (srcJarArtifact instanceof LocalFileArtifact localSrcJar) {
                IPath srcJarPath = new Path(localSrcJar.getPath().toString());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found jar for '{}': {} (source {})", localJar.getPath().getFileName(), jarPath,
                        srcJarPath);
                }
                return ClasspathEntry.newLibraryEntry(jarPath, srcJarPath, null, false /* test only */);
            }
        }
        return null;
    }
}
