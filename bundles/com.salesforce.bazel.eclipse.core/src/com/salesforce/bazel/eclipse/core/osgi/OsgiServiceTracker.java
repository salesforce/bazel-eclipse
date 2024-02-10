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

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.EventAdmin;

import com.salesforce.bazel.eclipse.core.BazelCorePlugin;

/**
 * A tracker for various OSGi services we consume/need.
 */
@Component
public class OsgiServiceTracker {

    @Reference
    EventAdmin eventAdmin;

    @Activate
    public void activate() {
        BazelCorePlugin.getInstance().setServiceTracker(this);
    }

    @Deactivate
    public void deactivate() {
        BazelCorePlugin.getInstance().setServiceTracker(null);
    }

    public EventAdmin getEventAdmin() {
        return requireNonNull(eventAdmin, "no event admin; possible bug");
    }
}
