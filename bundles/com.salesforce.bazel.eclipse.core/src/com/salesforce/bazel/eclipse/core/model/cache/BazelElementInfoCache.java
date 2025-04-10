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
package com.salesforce.bazel.eclipse.core.model.cache;

import java.util.Collection;

import com.salesforce.bazel.eclipse.core.model.BazelElement;
import com.salesforce.bazel.eclipse.core.model.BazelElementInfo;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;

/**
 * A cache for managing the lifecycle of {@link BazelElementInfo} objects.
 * <p>
 * In order to avoid repeated reads or expensive queries from Bazel, the cache will be used to keep information
 * available in memory.
 * </p>
 * <p>
 * During the lifetime of the SDK/JVM instance, only one {@link BazelElementInfoCache} cache instance shall be used.
 * Multiple instances are confusing as its tough to manage and can easily cause memory issues as well as unwanted side
 * effects. It's the responsibility of the SDK user/adopter to ensure this guarantee.
 * </p>
 */
public abstract sealed class BazelElementInfoCache permits CaffeineBasedBazelElementInfoCache {

    /**
     * Collects all elements available in the cache for the given workspace.
     *
     * @param bazelWorkspace
     *            the bazel workspace
     * @return an in-time snapshot collection of all elements of the workspace available in the cache
     */
    public abstract Collection<BazelElement<?, ?>> getAll(BazelWorkspace bazelWorkspace);

    /**
     * Returns an element info from the cache if it's present.
     *
     * @param <I>
     *            the element info type
     * @param bazelElement
     *            the element to obtain the info for
     * @return the element info (may be <code>null</code>)
     */
    public abstract <I extends BazelElementInfo> I getIfPresent(BazelElement<I, ?> bazelElement);

    /**
     * @return a string summarizing the cache statistics for logging purposes
     */
    public abstract CharSequence getStatsSummary();

    /**
     * Discards an entry for the specified element from the cache.
     * <p>
     * The behavior of this operation is undefined for an entry that is being loaded (or reloaded) and is otherwise not
     * present.
     * </p>
     * <p>
     * This method is exposed for <code>com.salesforce.bazel.eclipse.core.model.ResourceChangeProcessor</code>. It
     * should not be called by anyone else because in Eclipse everything is tight to a
     * <code>IResourceChangeEvent</code>. Thus, when you find a situation where a cache flush is needed, please write a
     * test and then fix the implementation by adding support for the missing event into
     * <code>com.salesforce.bazel.eclipse.core.model.ResourceChangeProcessor</code>
     * </p>
     *
     * @param bazelElement
     */
    public abstract void invalidate(BazelElement<?, ?> bazelElement);

    /**
     * Discards all entries in the cache.
     * <p>
     * The behavior of this operation is undefined for an entry that is being loaded (or reloaded) and is otherwise not
     * present.
     * </p>
     * <p>
     * This method is exposed for <code>com.salesforce.bazel.eclipse.core.model.ResourceChangeProcessor</code>. It
     * should not be called by anyone else because in Eclipse everything is tight to a
     * <code>IResourceChangeEvent</code>. Thus, when you find a situation where a cache flush is needed, please write a
     * test and then fix the implementation by adding support for the missing event into
     * <code>com.salesforce.bazel.eclipse.core.model.ResourceChangeProcessor</code>
     * </p>
     *
     * @see com.salesforce.bazel.eclipse.core.model.ResourceChangeProcessor
     */
    public abstract void invalidateAll();

    /**
     * Puts an element info into the cache for a given element. This method provides a simple substitute for the
     * conventional "if cached, return; otherwise cache and return" pattern.
     * </p>
     * <p>
     * Note, this method returns the {@code info} that was or is associated with the {@code BazelElement} in this cache,
     * which may not be the passed in info. The entire method invocation is performed atomically, so the put is applied
     * at most once per bazelElement.
     * </p>
     *
     * @param <I>
     *            the element info type
     * @param bazelElement
     *            the element to obtain the info for
     * @param info
     *            the element info (must not be <code>null</code>)
     */
    public abstract <I extends BazelElementInfo> I putOrGetCached(BazelElement<I, ?> bazelElement, I info);
}
