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
package com.salesforce.bazel.eclipse.core.osgi;

import static java.util.Objects.requireNonNull;

import org.osgi.framework.BundleContext;
import org.osgi.service.event.EventAdmin;
import org.osgi.util.tracker.ServiceTracker;

/**
 * A tracker for various OSGi services we consume/need.
 */
public class OsgiServiceTracker {

    private final ServiceTracker<EventAdmin, EventAdmin> eventAdminTracker;

    public OsgiServiceTracker(BundleContext bundleContext) {
        eventAdminTracker = new ServiceTracker<>(bundleContext, EventAdmin.class, null);
    }

    public void close() {
        if (eventAdminTracker != null) {
            eventAdminTracker.close();
        }
    }

    public EventAdmin getEventAdmin() {
        ServiceTracker<EventAdmin, EventAdmin> tracker =
                requireNonNull(eventAdminTracker, "the service tracker was not initialized properly");
        return requireNonNull(tracker.getService(), "no event admin; possible deployment bug");
    }
}
