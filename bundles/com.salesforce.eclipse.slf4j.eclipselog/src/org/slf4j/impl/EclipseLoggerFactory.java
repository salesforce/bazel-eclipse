/*-
 * Copyright (c) 2017 Salesforce and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Salesforce - initial API and implementation
 */
package org.slf4j.impl;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

public class EclipseLoggerFactory implements ILoggerFactory {

    private final ConcurrentMap<String, Logger> loggerByName = new ConcurrentHashMap<>();
    private volatile Bundle myself;

    private Bundle getBundle() {
        var myself = this.myself;
        if (myself != null) {
            return myself;
        }

        myself = requireNonNull(FrameworkUtil.getBundle(EclipseLoggerFactory.class),
            "Unable to determin my bundle. The Eclipse Log binding must be running in an OSGi environment.");
        requireNonNull(myself.getBundleContext(),
            "Unable to get BundleContext. Please ensure the binding resolves correctly.");
        return this.myself = myself;
    }

    @Override
    public Logger getLogger(final String name) {
        Logger logger = null;
        do {
            logger = loggerByName.get(name);
            if (logger == null) {
                logger = loggerByName.putIfAbsent(name, new EclipseLogger(name, getBundle()));
            }
        } while (logger == null);

        return logger;
    }
}
