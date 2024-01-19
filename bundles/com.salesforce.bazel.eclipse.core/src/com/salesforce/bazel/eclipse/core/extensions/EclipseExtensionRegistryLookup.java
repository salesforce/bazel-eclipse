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
package com.salesforce.bazel.eclipse.core.extensions;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

/**
 * A simple base class for discovering objects from the Eclipse Extension registry.
 */
public abstract class EclipseExtensionRegistryLookup {

    /**
     * Attribute name 'class' for loading objects
     */
    protected static final String ATTR_CLASS = "class";

    protected final String extensionPointId;

    protected EclipseExtensionRegistryLookup(String extensionPointId) {
        this.extensionPointId = requireNonNull(extensionPointId, "An extension point id is required!");
    }

    protected Stream<IConfigurationElement> allConfigurationElements() {
        return Stream.of(Platform.getExtensionRegistry().getConfigurationElementsFor(extensionPointId));
    }

    protected List<Object> findAndCreateAllObjectsByElementName(String elementName) throws CoreException {
        var elements = findExtensionsByElementName(elementName);
        if (elements.isEmpty()) {
            return Collections.emptyList();
        }

        // sort by priority
        sortByPriorityAttribute(elements);

        List<Object> result = new ArrayList<>(elements.size());
        for (IConfigurationElement element : elements) {
            var object = element.createExecutableExtension(ATTR_CLASS);
            if (object == null) {
                throw new CoreException(
                        Status.error(format("No object returned from extension factory for %s", element)));
            }
            result.add(object);
        }
        return result;
    }

    protected Object findAndCreateSingleObjectByElementNameAndAttributeValue(String elementName, String attributeName,
            String attributeValue) throws CoreException {
        var elements = findExtensionsByElementNameAndAttributeValue(elementName, attributeName, attributeValue);
        if (elements.isEmpty()) {
            throw new CoreException(
                    Status.error(
                        format(
                            "No extensions available providing a '%s' with %s '%s'!",
                            elementName,
                            attributeName,
                            attributeValue)));
        }

        // use the first one in the list
        var element = elements.iterator().next();
        var strategy = element.createExecutableExtension(ATTR_CLASS);
        if (strategy == null) {
            throw new CoreException(
                    Status.error(
                        format(
                            "No object returned from extension factory for %s '%s'",
                            attributeName,
                            attributeValue)));
        }
        return strategy;
    }

    protected ArrayList<IConfigurationElement> findExtensionsByElementName(String elementName) {
        return allConfigurationElements().filter(p -> elementName.equals(p.getName()))
                .collect(toCollection(ArrayList::new)); // by contract - return a modifiable ArrayList which may be sorted later
    }

    protected ArrayList<IConfigurationElement> findExtensionsByElementNameAndAttributeValue(String elementName,
            String attributeName, String expectedAttributeValue) {
        return allConfigurationElements().filter(
            p -> elementName.equals(p.getName()) && expectedAttributeValue.equals(p.getAttribute(attributeName)))
                .collect(toCollection(ArrayList::new)); // by contract - return a modifiable ArrayList which may be sorted later
    }

    protected void sortByPriorityAttribute(ArrayList<IConfigurationElement> elements) {
        // sort based on priority
        elements.sort(new PriorityAttributeComparator());
    }
}
