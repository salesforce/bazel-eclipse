/*-
 *
 */
package com.salesforce.bazel.eclipse.core.resources;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BAZEL_NATURE_ID;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.RESOURCE_FILTER_BAZEL_OUTPUT_SYMLINKS_ID;
import static java.lang.String.format;

import java.io.IOException;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.filtermatchers.AbstractFileInfoMatcher;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;

/**
 * A special filter to ensure the Bazel output sym-links do not appear for the {@link BazelWorkspace workspace} project
 * in Eclipse.
 * <p>
 * This filter is important to prevent excessive refresh times in Eclipse for large projects
 * </p>
 */
public class BazelOutputFilterMatcher extends AbstractFileInfoMatcher {

    public static final String ID = RESOURCE_FILTER_BAZEL_OUTPUT_SYMLINKS_ID;

    private static Logger LOG = LoggerFactory.getLogger(BazelOutputFilterMatcher.class);

    private BazelProject bazelProject;

    @Override
    public void initialize(IProject project, Object arguments) throws CoreException {
        if (!project.hasNature(BAZEL_NATURE_ID)) {
            throw new CoreException(
                    Status.error(
                        format(
                            "Project '%s' is not a Bazel project. Please remove the Bazel output filter from the project configuration!",
                            project)));
        }

        bazelProject = BazelCore.create(project);
    }

    @Override
    public boolean matches(IContainer parent, IFileInfo fileInfo) throws CoreException {
        if (parent.getType() != IResource.PROJECT) {
            return false;
        }

        // only check symlinks
        if (!fileInfo.getAttribute(EFS.ATTRIBUTE_SYMLINK)) {
            return false;
        }

        // shortcut: if symlink name starts with "bazel-" we ignore it
        if (fileInfo.getName().startsWith("bazel-")) {
            return true;
        }

        // implementation node: the code below requires a proper Bazel setup, thus, lets check here if this is a workspace project
        // the code above can run without this check
        if (!bazelProject.isWorkspaceProject()) {
            return false;
        }

        var symlinkTarget = fileInfo.getStringAttribute(EFS.ATTRIBUTE_LINK_TARGET);
        if (symlinkTarget == null) {
            return false;
        }

        try {
            var symlinkTarketPath = IPath.fromOSString(symlinkTarget);
            var workspaceOutputBase = bazelProject.getBazelWorkspace().getOutputBaseLocation();
            return workspaceOutputBase.isPrefixOf(symlinkTarketPath)
                    || workspaceOutputBase.isPrefixOf(IPath.fromPath(symlinkTarketPath.toPath().toRealPath()));
        } catch (IOException e) {
            // ignore
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                    "Error checking symlink '{}' -> '{}' in project '{}'",
                    fileInfo.getName(),
                    symlinkTarget,
                    parent,
                    e);
            }
        }

        return false;
    }

}
