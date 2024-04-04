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
package com.salesforce.bazel.eclipse.core.model.discovery.projects;

/**
 * Additional information for an {@link Entry}
 */
public record EntrySettings(boolean nowarn) {
    /**
     * default settings
     */
    public static final EntrySettings DEFAULT_SETTINGS = new EntrySettings(false);

}
