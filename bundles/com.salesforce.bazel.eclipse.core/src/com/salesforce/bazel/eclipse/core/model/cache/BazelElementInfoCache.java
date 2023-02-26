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

import static java.util.Objects.requireNonNull;

import java.util.concurrent.atomic.AtomicReference;

import com.salesforce.bazel.eclipse.core.model.BazelElement;
import com.salesforce.bazel.eclipse.core.model.BazelElementInfo;

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

    private static final AtomicReference<BazelElementInfoCache> cacheRef = new AtomicReference<>();

    /**
     * Returns the singleton cache instance
     *
     * @return the singleton cache instance (never <code>null</code>)
     * @throws IllegalStateException
     *             if the cache has not been initalized yet
     */
    public static final BazelElementInfoCache getInstance() throws IllegalStateException {
        var cache = cacheRef.get();
        if (cache == null) {
            throw new IllegalStateException("BazelElementInfoCache not initialized.");
        }
        return cache;
    }

    /**
     * Initializes the singleton instance.
     *
     * @param cache
     *            the singleton instance
     * @throws IllegalStateException
     *             if the singleton cache instance was already initialized
     */
    public static final void setInstance(BazelElementInfoCache cache) throws IllegalStateException {
        if (!cacheRef.compareAndSet(null, requireNonNull(cache, "Cannot initialize NULL instance"))) {
            throw new IllegalStateException("The cache was already initialized. Cannot initialize multiple times!");
        }
    }

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
