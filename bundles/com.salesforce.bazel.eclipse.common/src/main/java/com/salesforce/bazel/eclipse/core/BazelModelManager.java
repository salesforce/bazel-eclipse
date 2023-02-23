package com.salesforce.bazel.eclipse.core;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.atomic.AtomicReference;

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

import com.salesforce.bazel.eclipse.BazelCommonContstants;
import com.salesforce.bazel.eclipse.activator.BazelPlugin;
import com.salesforce.bazel.eclipse.classpath.BazelClasspathManager;

/**
 * The Bazel model manager is responsible for managing the mapping state of the Bazel model into the IDE.
 * <p>
 * It's intended to be used as a singleton. There must only be one instance during the entire lifetime of the IDE. This
 * instance is managed by {@link BazelPlugin}.
 * </p>
 */
public class BazelModelManager implements BazelCommonContstants {

    private static Logger LOG = LoggerFactory.getLogger(BazelModelManager.class);

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
            if (savedProject != null) {
                //                if (!BazelProject.hasJavaNature(savedProject)) {
                //                    return; // ignore
                //                }
                // sever project specific info
                return;
            }

            // TODO: save all existing Bazel mappings
        }
    };

    private final IPath stateLocation;
    private BazelClasspathManager classpathManager;

    /**
     * Creates a new model manager instance
     *
     * @param stateLocation
     *            the absolute location on the file system to save model state
     */
    public BazelModelManager(IPath stateLocation) {
        this.stateLocation = stateLocation;
        this.resourceChangeProcessor = new ResourceChangeProcessor();
    }

    /**
     * @return the classpath manager used by this model manager
     */
    public BazelClasspathManager getClasspathManager() {
        return requireNonNull(classpathManager, "not initialized");
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
     * This method can only be called once. It's intended to be called by {@link BazelPlugin} and not someone else.
     * </p>
     *
     * @param workspace
     *            the workspace
     */
    public void initialize(IWorkspace workspace) {
        if (!workspaceReference.compareAndSet(null, workspace)) {
            throw new IllegalStateException(
                    "Attempt to initialize a model more than once. Please verify the code flow!");
        }

        // TODO: load all existing Bazel mappings

        // initialize the classpath
        classpathManager = new BazelClasspathManager(stateLocation.toFile());
        var refreshClasspath = new Job("Computing build path of Bazel projects") {
            @Override
            public boolean belongsTo(Object family) {
                return PLUGIN_ID.equals(family);
            }

            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    classpathManager.initializeMissingClasspaths(getWorkspace(), monitor);
                } catch (CoreException e) {
                    return e.getStatus();
                }
                return Status.OK_STATUS;
            }
        };
        refreshClasspath.setPriority(Job.BUILD); // process after others
        refreshClasspath.schedule();

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
                    JavaCore.addPreProcessingResourceChangedListener(resourceChangeProcessor,
                        IResourceChangeEvent.POST_CHANGE);

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
     * It's intended to be called by {@link BazelPlugin} and not someone else.
     * </p>
     */
    public void shutdown() {
        var workspace = workspaceReference.getAndSet(null);
        if (workspace == null) {
            // ignore repeated attempt
            return;
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

        // wait for the initialization job to finish
        try {
            Job.getJobManager().join(PLUGIN_ID, null);
        } catch (InterruptedException e) {
            // ignore
        }

    }
}
