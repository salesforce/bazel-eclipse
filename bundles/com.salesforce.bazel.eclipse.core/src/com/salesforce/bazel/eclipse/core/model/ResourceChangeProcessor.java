/**
 * Copyright (c) 2021, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.bazel.eclipse.core.model;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BAZEL_NATURE_ID;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_BUILD;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_BUILD_BAZEL;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_WORKSPACE;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_WORKSPACE_BAZEL;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_WORKSPACE_BZLMOD;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;

import com.salesforce.bazel.eclipse.core.classpath.InitializeOrRefreshClasspathJob;

/**
 * Global change listener for Eclipse Workspaces used by the Bazel model.
 */
class ResourceChangeProcessor implements IResourceChangeListener {

    private final BazelModelManager modelManager;

    public ResourceChangeProcessor(BazelModelManager modelManager) {
        this.modelManager = modelManager;
    }

    private void checkProjectsAndClasspathChanges(IResourceDelta delta) {
        // check for classpath changes
        Set<IProject> affectedProjects = new HashSet<>();
        Set<IProject> projectViewProjects = new HashSet<>();
        collectProjectsAffectedByPossibleClasspathChange(delta, affectedProjects, projectViewProjects);

        // flush the caches for the affected project
        affectedProjects.stream().forEach(this::invalidateCache);
        projectViewProjects.stream().forEach(this::invalidateBazelWorkspaceCache);

        // if we have some, we need to refresh classpaths
        // but we do this asynchronously and *only* when the workspace is in auto-build mode
        if ((affectedProjects.size() > 0) && isAutoBuilding()) {
            var classpathJob = new InitializeOrRefreshClasspathJob(
                    affectedProjects.toArray(new IProject[affectedProjects.size()]),
                    modelManager.getClasspathManager(),
                    true /* force refresh */);
            classpathJob.schedule();
        }
    }

    private void collectProjectsAffectedByPossibleClasspathChange(IResourceDelta delta,
            Set<IProject> affectedProjectsWithClasspathChange, Set<IProject> affectedProjectsWithProjectViewChange) {
        var resource = delta.getResource();
        var processChildren = false;
        switch (resource.getType()) {
            case IResource.ROOT:
                if (delta.getKind() == IResourceDelta.CHANGED) {
                    processChildren = true;
                }
                break;
            case IResource.PROJECT:
                var project = (IProject) resource;
                var kind = delta.getKind();
                boolean isBazelProject;
                try {
                    isBazelProject = project.hasNature(BAZEL_NATURE_ID);
                } catch (CoreException e) {
                    isBazelProject = false; // project does not exist or is not open
                }
                switch (kind) {
                    case IResourceDelta.ADDED:
                        processChildren = isBazelProject;
                        affectedProjectsWithClasspathChange.add(project);
                        break;
                    case IResourceDelta.CHANGED:
                        processChildren = isBazelProject;
                        if ((delta.getFlags() & IResourceDelta.OPEN) != 0) {
                            // project opened or closed
                            affectedProjectsWithClasspathChange.add(project);
                        } else if ((delta.getFlags() & IResourceDelta.DESCRIPTION) != 0) {
                            // TODO: search if there was a mapping
                            //                            boolean wasBazelProject = ...
                            //                            if (wasBazelProject  != isBazelProject) {
                            //                                // project gained or lost Bazel nature
                            //                                JavaProject javaProject = (JavaProject)JavaCore.create(project);
                            //                                this.state.addClasspathValidation(javaProject); // add/remove classpath markers
                            //                                affectedProjects.add(project.getFullPath());
                            //                            }
                        }
                        break;
                    case IResourceDelta.REMOVED:
                        affectedProjectsWithClasspathChange.add(project);
                        break;
                }
                break;
            case IResource.FILE:
                /* check BUILD files change */
                var file = (IFile) resource;
                var fileName = file.getName();
                if (fileName.equals(FILE_NAME_BUILD_BAZEL) || fileName.equals(FILE_NAME_BUILD)
                        || fileName.equals(FILE_NAME_WORKSPACE_BAZEL) || fileName.equals(FILE_NAME_WORKSPACE)
                        || fileName.equals(FILE_NAME_WORKSPACE_BZLMOD)) {
                    affectedProjectsWithClasspathChange.add(file.getProject());
                }
                var fileExtension = file.getFileExtension();
                if ("bazelproject".equals(fileExtension)) {
                    affectedProjectsWithProjectViewChange.add(file.getProject());
                }
                break;
            case IResource.FOLDER:
                // the current assumption is that BUILD files must be direct children of a project; we don't traverse down into folders
                processChildren = false;
                break;
        }
        if (processChildren) {
            var children = delta.getAffectedChildren();
            for (IResourceDelta child : children) {
                collectProjectsAffectedByPossibleClasspathChange(
                    child,
                    affectedProjectsWithClasspathChange,
                    affectedProjectsWithProjectViewChange);
            }
        }
    }

    private void deleting(IProject project) throws CoreException {
        if (!project.hasNature(BAZEL_NATURE_ID)) {
            return;
        }

        // FIXME: optimize to be less aggressive
        // we "only" need to invalidate classpath of projects depending on the to be deleted one
        // however, ideally a sync is required now
        modelManager.getModel().getInfoCache().invalidateAll();
    }

    private void invalidateBazelWorkspaceCache(IProject project) {
        var bazelProject = modelManager.getBazelProject(project);
        try {
            bazelProject.getBazelWorkspace().invalidateInfo();
        } catch (CoreException e) {
            // ignore
        }
    }

    private void invalidateCache(IProject project) {
        var bazelProject = modelManager.getBazelProject(project);
        try {
            if (bazelProject.isWorkspaceProject()) {
                bazelProject.getBazelWorkspace().invalidateInfo();
            } else if (bazelProject.isPackageProject()) {
                bazelProject.getBazelPackage().invalidateInfo();
            } else if (bazelProject.isTargetProject()) {
                // validate the whole package
                bazelProject.getBazelPackage().invalidateInfo();
            }
        } catch (CoreException e) {
            // ignore
        }
    }

    /**
     * Returns whether a given delta contains some information relevant to the Bazel model, in particular it will not
     * consider SYNC or MARKER only deltas.
     */
    private boolean isAffectedBy(IResourceDelta rootDelta) {
        if (rootDelta == null) {
            return false;
        }

        // use local exception to quickly escape from delta traversal
        class FoundRelevantDeltaException extends RuntimeException {
            private static final long serialVersionUID = 7137113252936111022L; // backward compatible
            // only the class name is used (to differentiate from other RuntimeExceptions)
        }
        try {
            rootDelta.accept((IResourceDeltaVisitor) delta -> /* throws CoreException */ {
                switch (delta.getKind()) {
                    case IResourceDelta.ADDED:
                    case IResourceDelta.REMOVED:
                        throw new FoundRelevantDeltaException();
                    case IResourceDelta.CHANGED:
                        // if any flag is set but SYNC or MARKER, this delta should be considered
                        if ((delta.getAffectedChildren().length == 0 // only check leaf delta nodes
                        ) && ((delta.getFlags() & ~(IResourceDelta.SYNC | IResourceDelta.MARKERS)) != 0)) {
                            throw new FoundRelevantDeltaException();
                        }
                }
                return true;
            }, IContainer.INCLUDE_HIDDEN);
        } catch (FoundRelevantDeltaException e) {
            //System.out.println("RELEVANT DELTA detected in: "+ (System.currentTimeMillis() - start));
            return true;
        } catch (CoreException e) { // ignore delta if not able to traverse
        }

        //System.out.println("IGNORE SYNC DELTA took: "+ (System.currentTimeMillis() - start));
        return false;
    }

    boolean isAutoBuilding() {
        return ResourcesPlugin.getWorkspace().isAutoBuilding();
    }

    @Override
    public void resourceChanged(IResourceChangeEvent event) {
        var resource = event.getResource();
        var delta = event.getDelta();

        switch (event.getType()) {
            case IResourceChangeEvent.PRE_DELETE:
                try {
                    if ((resource.getType() == IResource.PROJECT) && ((IProject) resource).hasNature(BAZEL_NATURE_ID)) {
                        deleting((IProject) resource);
                    }
                } catch (CoreException e) {
                    // project doesn't exist or is not open: ignore
                }
                return;

            case IResourceChangeEvent.POST_CHANGE:
                if (isAffectedBy(delta)) {
                    checkProjectsAndClasspathChanges(delta);
                }

        }
    }

}
