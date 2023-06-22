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

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.salesforce.bazel.eclipse.core.model.BazelElement;
import com.salesforce.bazel.eclipse.core.model.BazelElementInfo;
import com.salesforce.bazel.eclipse.core.model.BazelModel;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;

/**
 * Implementation of {@link BazelElementInfoCache} which uses an LRU
 */
public final class CaffeineBasedBazelElementInfoCache extends BazelElementInfoCache {

    private static final String EMPTY_STRING = "";
    private final Cache<String, BazelElementInfo> cache;

    /**
     * Creates a cache using a maximum size.
     *
     * @param maximumSize
     *            the maximum cache size
     * @see Caffeine#maximumSize(long)
     */
    public CaffeineBasedBazelElementInfoCache(int maximumSize) {
        cache = Caffeine.newBuilder().maximumSize(maximumSize).build();
    }

    /**
     * Creates a cache using a maximum size and expiring entries when they haven't been accesses for a given duration.
     *
     * @param maximumSize
     *            the maximum cache size
     * @param expireAfterAccessDuration
     *            the length of time after an entry is last accessed that it should be automatically removed
     * @see Caffeine#maximumSize(long)
     * @see Caffeine#expireAfterAccess(Duration)
     */
    public CaffeineBasedBazelElementInfoCache(int maximumSize, Duration expireAfterAccessDuration) {
        cache = Caffeine.newBuilder().maximumSize(maximumSize).expireAfterAccess(expireAfterAccessDuration).build();
    }

    /**
     * Exposes the underlying Caffeine {@link Cache}.
     * <p>
     * This method should not be used in a quality deployment. It's only exposed for testing reasons.
     * </p>
     *
     * @return the underlying cache
     */
    public Cache<String, ? extends BazelElementInfo> getCache() {
        return cache;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I extends BazelElementInfo> I getIfPresent(BazelElement<I, ?> bazelElement) {
        return (I) cache.getIfPresent(getStableCacheKey(bazelElement));
    }

    private String getStableCacheKey(BazelElement<?, ?> bazelElement) {

        if (bazelElement instanceof BazelModel) {
            // this can only happen for the BazelModel
            return EMPTY_STRING;
        }

        // use a combination of workspace name and label to ensure we support multiple workspaces
        var workspace = requireNonNull(bazelElement.getBazelWorkspace(),
            "every element is required to have a workspace at this point");
        var workspaceLocationHash = workspace.getLocation().toString().hashCode();

        var label = bazelElement.getLabel();
        if (label == null) {
            // sanity check
            if (!(bazelElement instanceof BazelWorkspace)) {
                throw new IllegalStateException("Unable to compute cache key. Every BazelElement must have a label");
            }

            return String.valueOf(workspaceLocationHash);
        }

        return String.valueOf(workspaceLocationHash) + label.toString();
    }

    @Override
    public void invalidate(BazelElement<?, ?> bazelElement) {
        cache.invalidate(getStableCacheKey(bazelElement));
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I extends BazelElementInfo> I putOrGetCached(BazelElement<I, ?> bazelElement, I info) {
        return (I) cache.get(getStableCacheKey(bazelElement), k -> info);
    }
}