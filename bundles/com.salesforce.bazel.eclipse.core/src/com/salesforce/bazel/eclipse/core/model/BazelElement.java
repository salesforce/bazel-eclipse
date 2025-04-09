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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.OperationCanceledException;

import com.salesforce.bazel.eclipse.core.model.cache.BazelElementInfoCache;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * Base class for a Bazel model element.
 * <p>
 * The Bazel SDK model is designed on the concept of handlers, i.e. each model element by default is a handle. In order
 * to check an element exists, the {@link #exists()} method has to be used.
 * </p>
 * <p>
 * Because Bazel elements are handles only, the real information is captured in {@link BazelElementInfo} type hierarchy.
 * This information is maintained in a {@link BazelElementInfoCache}. The model cannot be used without
 * {@link BazelElementInfoCache#setInstance(BazelElementInfoCache) initializing} the cache first. Most methods on
 * {@link BazelElement} and its subclasses require {@link #createInfo() #getInfo()} the element. Loading an element is
 * potentially expensive because it may trigger Bazel process execution. Thus, unless otherwise state, assume any call
 * to a method, which throws {@link CoreException} may be expensive.
 * </p>
 * <p>
 * All elements in the Bazel model properly implement {@link #hashCode()} and {@link #equals(Object)} to ensure they are
 * equal when the handle matches the same Bazel element.
 * </p>
 *
 * @param <I>
 *            type of element info
 * @param <P>
 *            type of element parent
 */
public sealed abstract class BazelElement<I extends BazelElementInfo, P extends BazelElement<?, ?>>
        permits BazelModel, BazelWorkspace, BazelPackage, BazelTarget, BazelFile {

    private static final String NO_NAME = "";

    private volatile BazelElementCommandExecutor commandExecutor;

    /**
     * Opens the underlying Bazel element and returns the populated {@link BazelElementInfo}.
     * <p>
     * This method is typically called by {@link #getInfo()}. It should not be called directly.
     * </p>
     * <p>
     * This method will <b>always</b> return a new, fresh element info option. It performs IO operations with the
     * underlying file system and may execute Bazel commands using the {@link BazelCommandManager}.
     * </p>
     * <p>
     * Implementors must not modify any state in the model or Eclipse workspace when creating infos.
     * </p>
     * <p>
     * When this method is called, implementors can assume that the parent info has already been created and stored in
     * the {@link BazelElementInfoCache}.
     * </p>
     *
     * @return the loaded {@link BazelElementInfo}
     * @throws CoreException
     *             in case of problems loading the info
     */
    protected abstract I createInfo() throws CoreException;

    @Override
    public abstract boolean equals(Object obj);

    /**
     * Indicates whether a model element exists in the file system.
     * <p>
     * This most certainly involves IO operations. It may require reading the underlying element information but there
     * might be optimized implementations which do not require reading the full element info. Please check the Java doc
     * on a subclass method for details.
     * </p>
     *
     * @return
     * @throws CoreException
     *             in case of problems/errors accessing/reading the file system
     * @see #createInfo()
     */
    public abstract boolean exists() throws CoreException;

    /**
     * Returns the {@link BazelWorkspace workspace} an element is contained in.
     *
     * @return the {@link BazelWorkspace} (maybe <code>null</code> in case of {@link BazelModel})
     */
    public abstract BazelWorkspace getBazelWorkspace();

    /**
     * {@return the executor for this element}
     */
    public BazelElementCommandExecutor getCommandExecutor() {
        var executor = commandExecutor;
        if (executor != null) {
            return executor;
        }
        return commandExecutor = new BazelElementCommandExecutor(this);
    }

    /**
     * Returns the {@link BazelElementInfo}, {@link #createInfo() opening the element if none exists in the cache}.
     *
     * @return the loaded element info (may be cached)
     * @throws CoreException
     *             in case of problems {@link #createInfo() loading} the element info.
     */
    protected final I getInfo() throws CoreException {
        var infoCache = getInfoCache();
        var info = infoCache.getIfPresent(this);
        if (info != null) {
            return info;
        }

        // ensure the parent is loaded
        if (hasParent()) {
            getParent().getInfo();
        }

        // loads can be potentially expensive; we synchronize on the location
        var location = getLocation();
        var openJob = new BazelElementOpenJob<>(location != null ? location : IPath.ROOT, this, infoCache);
        try {
            return openJob.open();
        } catch (InterruptedException e) {
            throw new OperationCanceledException("Interrupted while opening element.");
        }
    }

    BazelElementInfoCache getInfoCache() {
        return getModel().getModelManager().getModelInfoCache();
    }

    /**
     * Returns the full qualified label for this element.
     * <p>
     * Note, not all elements in the model have a label. The label is only available when it allows to uniquely identify
     * the element in Bazel. Therefore, the following elements do not have a label:
     * <ul>
     * <li>{@link BazelModel}</li>
     * <li>{@link BazelWorkspace}</li>
     * </ul>
     * </p>
     *
     * @return the full qualified label for this element, or <code>null</code> in case of {@link BazelModel}, or
     *         {@link BazelWorkspace}
     */
    public abstract BazelLabel getLabel();

    /**
     * Returns the absolute path in the local file system to this element.
     * <p>
     * If this element is a Bazel workspace, this method returns the absolute local file system path of the Bazel
     * workspace root.
     * </p>
     * <p>
     * If this element is a package, this method returns the absolute local file system path to the folder of the Bazel
     * package.
     * </p>
     * <p>
     * If this element is a target, this method returns the absolute local file system path to the BUILD file in that
     * package, which defines the target.
     * </p>
     *
     * @return the absolute path of this element in the local file system, or <code>null</code> if no path can be
     *         determined
     */
    public abstract IPath getLocation();

    /**
     * @return the Bazel model this element belongs to
     */
    BazelModel getModel() {
        BazelElement<?, ?> model = this;
        while ((model != null) && !(model instanceof BazelModel)) {
            model = model.getParent();
        }

        return (BazelModel) model;
    }

    /**
     * Returns a name of the element.
     * <p>
     * Note, this method may trigger loading of the underlying element.
     * </p>
     *
     * @return a name of the element (never <code>null</code>)
     */
    public String getName() {
        var label = getLabel();
        if (label == null) {
            return NO_NAME;
        }

        return label.toString();
    }

    /**
     * Returns the parent of the model element.
     *
     * @return the parent (may be <code>null</code> in case of the {@link BazelModel})
     */
    public abstract P getParent();

    @Override
    public abstract int hashCode();

    public final boolean hasInfo() {
        return getInfoCache().getIfPresent(this) != null;
    }

    public boolean hasParent() {
        return getParent() != null;
    }

    /**
     * Invalidates this elements loaded info from the cache.
     */
    final void invalidateInfo() {
        var infoCache = getInfoCache();
        infoCache.invalidate(this);
    }

    /**
     * Returns the {@link BazelElementInfo}, opening the element with the given info if none exists in the cache.
     *
     * @return the loaded element info (may be cached)
     * @throws CoreException
     *             in case of problems {@link #createInfo() loading} the element info.
     */
    protected final I openIfNecessary(I info) throws CoreException {
        // ensure the parent is loaded
        if (hasParent()) {
            getParent().getInfo();
        }

        var infoCache = getInfoCache();

        // ensure there is at most one info in the cache and this is what we use
        return infoCache.putOrGetCached(this, requireNonNull(info, "null info not allowed"));
    }

    @Override
    public String toString() {
        var label = getLabel();
        if (label == null) {
            return this.getClass().getSimpleName();
        }

        var workspace = getBazelWorkspace();
        if (workspace == this) {
            return "BazelWorkspace (" + getLocation() + ")";
        }

        var info = hasInfo() ? " [OPEN]" : " [CLOSED]";

        return this.getClass().getSimpleName() + " (" + label.toString() + ")" + info;
    }

}
