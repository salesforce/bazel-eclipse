/*-
 * Copyright (c) 2023 Salesforce and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Salesforce - Partially adapted and heavily inspired from Eclipse JDT, M2E and PDE
 */
package com.salesforce.bazel.eclipse.core.model;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.ISaveContext;
import org.eclipse.core.resources.ISaveParticipant;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.JavaCore;
import org.osgi.service.prefs.BackingStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.BazelCorePlugin;
import com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants;
import com.salesforce.bazel.eclipse.core.classpath.BazelClasspathManager;
import com.salesforce.bazel.eclipse.core.extensions.ExtensibleCommandExecutor;
import com.salesforce.bazel.eclipse.core.model.cache.BazelElementInfoCache;
import com.salesforce.bazel.eclipse.core.model.cache.CaffeineBasedBazelElementInfoCache;
import com.salesforce.bazel.eclipse.core.model.execution.BazelModelCommandExecutionService;
import com.salesforce.bazel.eclipse.core.model.execution.JobsBasedExecutionService;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects;

/**
 * The Bazel model manager is responsible for managing the mapping state of the Bazel model into the IDE.
 * <p>
 * It's intended to be used as a singleton. There must only be one instance during the entire lifetime of the IDE. This
 * instance is managed by {@link BazelCorePlugin}.
 * </p>
 * <p>
 * In general the model manager is considered an internal manager to the model. It should only be interacted with from
 * within the model. Please reach out if you have a different use case.
 * </p>
 */
public class BazelModelManager implements BazelCoreSharedContstants {

    private static Logger LOG = LoggerFactory.getLogger(BazelModelManager.class);

    /**
     * @return the duration after which cache entries should expire (this should be large enough to allow maximum cache
     *         hits during a full Sync)
     */
    private static Duration getCacheExpireAfterAccessDuration() {
        var seconds = Integer.getInteger("eclipse.bazel.model.cache.expireAfterAccessSeconds", 1800); // assuming Sync max is 30 minutes
        if (seconds <= 0) {
            return Duration.ofHours(72); // up to 72 hours
        }
        return Duration.ofSeconds(seconds);
    }

    /**
     * @return the maximum cache size (this should be large enough to hold all packages, targets and build files needed
     *         during a full Sync)
     */
    private static int getCacheMaximumSize() {
        return Integer.getInteger("eclipse.bazel.model.cache.maximumSize", 100000000 /* is hundred million enough?*/);
    }

    private final ResourceChangeProcessor resourceChangeProcessor;
    private final AtomicReference<IWorkspace> workspaceReference = new AtomicReference<>();

    private final ISaveParticipant saveParticipant = new ISaveParticipant() {

        @Override
        public void doneSaving(ISaveContext context) {
            // nothing to do
        }

        @Override
        public void prepareToSave(ISaveContext context) throws CoreException {
            // nothing to do
        }

        @Override
        public void rollback(ISaveContext context) {
            // nothing to do
        }

        @Override
        public void saving(ISaveContext context) throws CoreException {
            switch (context.getKind()) {
                case ISaveContext.FULL_SAVE: {

                    // enable deltas since this save
                    context.needDelta();

                    // opportunity for cleanups on full save
                    break;
                }
                case ISaveContext.SNAPSHOT: {
                    // opportunity for cleanups on snaphot save
                    break;
                }
            }

            // this is where we have an opportunity to persist internal state when Eclipse shuts down (or performs a snapshot during normal operations)

            var savedProject = context.getProject();
            if (savedProject != null) {}

            // TODO: save all existing Bazel mappings
        }
    };

    private final IPath stateLocation;
    private final BazelModel model;

    private final JobsBasedExecutionService executionService =
            new JobsBasedExecutionService(new ExtensibleCommandExecutor());

    private volatile BazelClasspathManager classpathManager;
    private volatile BazelElementInfoCache cache;
    private volatile IntellijAspects aspects;

    /**
     * Creates a new model manager instance
     *
     * @param stateLocation
     *            the absolute location on the file system to save model state
     */
    public BazelModelManager(IPath stateLocation) {
        this.stateLocation = stateLocation;
        model = new BazelModel(this);
        resourceChangeProcessor = new ResourceChangeProcessor(this);
    }

    public BazelProject getBazelProject(IProject project) {
        return new BazelProject(requireNonNull(project, "missing project"), getModel());
    }

    /**
     * @return the classpath manager used by this model manager
     */
    public BazelClasspathManager getClasspathManager() {
        return requireNonNull(classpathManager, "The model manager is not initialized!");
    }

    /**
     * @return the execution service used by the model
     */
    BazelModelCommandExecutionService getExecutionService() {
        return executionService;
    }

    /**
     * {@return the aspects used by the model}
     */
    public IntellijAspects getIntellijAspects() {
        return requireNonNull(aspects, "The model manager is not initialized!");
    }

    public BazelModel getModel() {
        return model;
    }

    /**
     * {@return the singleton instance of the {@link BazelElementInfoCache} used by the model}
     */
    public BazelElementInfoCache getModelInfoCache() {
        return requireNonNull(cache, "The model manager is not initialized!");
    }

    /**
     * {@return the resource change processor}
     */
    ResourceChangeProcessor getResourceChangeProcessor() {
        return resourceChangeProcessor;
    }

    /**
     * @return the Eclipse {@link IWorkspace}.
     */
    IWorkspace getWorkspace() {
        return requireNonNull(workspaceReference.get(), "Not initialized!");
    }

    /**
     * Initializes the model manager with a given Eclipse workspace
     * <p>
     * This method can only be called once. It's intended to be called by {@link BazelCorePlugin} and not someone else.
     * </p>
     *
     * @param workspace
     *            the workspace
     * @throws Exception
     *             in case issues initializing the model
     */
    public void initialize(IWorkspace workspace) throws Exception {
        if (!workspaceReference.compareAndSet(null, workspace)) {
            throw new IllegalStateException(
                    "Attempt to initialize a model more than once. Please verify the code flow!");
        }

        // configure cache
        cache = new CaffeineBasedBazelElementInfoCache(getCacheMaximumSize(), getCacheExpireAfterAccessDuration());

        // ensure aspects are usable
        aspects = new IntellijAspects(stateLocation.append("intellij-aspects").toPath());

        // initialize the classpath manager
        classpathManager = new BazelClasspathManager(stateLocation.toFile(), this);

        // insert our global resource listener into the workspace
        // and process deltas since last activated in same thread so that we don't miss anything
        // (see https://bugs.eclipse.org/bugs/show_bug.cgi?id=38658 for background)
        var processSavedState = new Job("Processing Bazel changes since last activation") {
            @Override
            public boolean belongsTo(Object family) {
                return PLUGIN_ID.equals(family);
            }

            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {

                    // we must process the POST_CHANGE events before the Java model
                    // for the container classpath update to proceed smoothly
                    JavaCore.addPreProcessingResourceChangedListener(
                        resourceChangeProcessor,
                        IResourceChangeEvent.PRE_DELETE | IResourceChangeEvent.POST_CHANGE);

                    // add save participant and process delta atomically
                    // see https://bugs.eclipse.org/bugs/show_bug.cgi?id=59937
                    getWorkspace().run((var progress) -> {
                        var savedState = workspace.addSaveParticipant(PLUGIN_ID, saveParticipant);
                        if (savedState != null) {
                            savedState.processResourceChangeEvents(resourceChangeProcessor);
                        }
                    }, monitor);
                } catch (CoreException e) {
                    return e.getStatus();
                }
                return Status.OK_STATUS;
            }
        };
        processSavedState.setPriority(Job.SHORT); // process asap
        processSavedState.schedule();
    }

    /**
     * Shutdown of the model manager.
     * <p>
     * It's intended to be called by {@link BazelCorePlugin} and not someone else.
     * </p>
     */
    public void shutdown() {
        var workspace = workspaceReference.getAndSet(null);
        if (workspace == null) {
            // ignore repeated attempt
            return;
        }

        // wait for the initialization job to finish
        try {
            Job.getJobManager().join(PLUGIN_ID, null);
        } catch (InterruptedException e) {
            // ignore
        }

        // flush preferences
        var preferences = InstanceScope.INSTANCE.getNode(PLUGIN_ID);
        try {
            preferences.flush();
        } catch (BackingStoreException e) {
            LOG.error("Could not save BazelCore preferences", e); //$NON-NLS-1$
        }
        workspace.removeResourceChangeListener(resourceChangeProcessor);
        workspace.removeSaveParticipant(PLUGIN_ID);

        // unset references
        cache = null;
        aspects = null;
        classpathManager = null;
    }
}
