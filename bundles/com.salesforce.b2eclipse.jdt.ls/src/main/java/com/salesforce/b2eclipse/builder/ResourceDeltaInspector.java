package com.salesforce.b2eclipse.builder;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.runtime.CoreException;

import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.util.BazelConstants;

/**
 * Convenience methods for looking into a IResourceDelta.
 */
class ResourceDeltaInspector {

    private static final LogHelper LOG = LogHelper.log(ResourceDeltaInspector.class);

    static boolean deltaHasChangedBuildFiles(IResourceDelta delta) {
        return hasChangedFiles(delta, BazelConstants.BUILD_FILE_NAMES);
    }

    private static boolean hasChangedFiles(IResourceDelta delta, Collection<String> filenameNeedles) {
        if (delta == null) {
            throw new IllegalArgumentException("Field delta cannot be null.");
        }
        try {
            Collection<IResource> matchingResources = new ArrayList<>();
            delta.accept(new ChangedResourceVisitor(filenameNeedles, matchingResources));
            return !matchingResources.isEmpty();
        } catch (CoreException ex) {
            LOG.error("Error while inspecting IResourceDelta", ex);
            return false;
        }
    }

    private static class ChangedResourceVisitor implements IResourceDeltaVisitor {

        private final Collection<String> filenameNeedles;
        private Collection<IResource> matchingResources;

        private ChangedResourceVisitor(Collection<String> filenameNeedles, Collection<IResource> matchingResources) {
            this.filenameNeedles = filenameNeedles;
            this.matchingResources = matchingResources;
        }

        @Override
        public boolean visit(IResourceDelta delta) throws CoreException {
            if (delta.getKind() == IResourceDelta.CHANGED) {
                if ((delta.getFlags() & IResourceDelta.CONTENT) != 0) {
                    IResource resource = delta.getResource();
                    if (resource.getType() == IResource.FILE) {
                        if (filenameNeedles.contains(resource.getName())) {
                            matchingResources.add(resource);
                            return false; // stop visiting
                        }
                    }
                }
            }
            return true;
        }
    }
}
