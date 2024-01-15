/*-
 * Copyright (c) 2024 Salesforce and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Salesforce - adapted from M2E, JDT or other Eclipse project
 */
package com.salesforce.bazel.eclipse.core.model;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.extensions.EclipseExtensionRegistryLookup;

/**
 * A simple lookup strategy for {@link SynchronizationParticipant} implementations using the Eclipse extension registry.
 */
public final class SynchronizationParticipantExtensionLookup extends EclipseExtensionRegistryLookup {

    private static Logger LOG = LoggerFactory.getLogger(SynchronizationParticipantExtensionLookup.class);

    private static final String EXTENSION_POINT_SYNCHRONIZATION_PARTICIPANT =
            "com.salesforce.bazel.eclipse.core.model.sync.participant";

    private static final String ELEMENT_SYNCHRONIZATION_PARTICIPANT = "synchronizationParticipant";

    protected SynchronizationParticipantExtensionLookup() {
        super(EXTENSION_POINT_SYNCHRONIZATION_PARTICIPANT);
    }

    /**
     * Searches the Eclipse extension registry for all {@link SynchronizationParticipant}.
     *
     * @return list of found participants in random order (never <code>null</code>)
     * @throws CoreException
     *             if there was an error creating analyzers
     */
    public List<SynchronizationParticipant> createSynchronizationParticipants() throws CoreException {
        var objects = findAndCreateAllObjectsByElementName(ELEMENT_SYNCHRONIZATION_PARTICIPANT);
        var result = new ArrayList<SynchronizationParticipant>();
        for (Object object : objects) {
            try {
                result.add((SynchronizationParticipant) object);
            } catch (ClassCastException e) {
                LOG.error(
                    "Invalid extension found for extension point '{}'. Expected a SynchronizationParticipant but got '{}'",
                    extensionPointId,
                    object.getClass().getName());
            }
        }
        return result;
    }

}
