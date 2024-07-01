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
package com.salesforce.bazel.eclipse.core.classpath;

import java.util.Collection;

import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;

/**
 * Represents a targets classpath as two parts, loaded being the classpath entries that are loaded into the project
 * model, and unloaded being classpaths that are part of the target, but are not to be loaded in the project model
 */
public record ClasspathHolder(Collection<ClasspathEntry> loaded, Collection<ClasspathEntry> unloaded) {

}
