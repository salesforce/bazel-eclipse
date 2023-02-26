package com.salesforce.bazel.eclipse.core.model.discovery;

import static java.lang.String.format;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

/**
 * A simple lookup strategy for {@link TargetDiscoveryStrategy} and {@link TargetProvisioningStrategy} implementations
 * using the Eclipse extension registry.
 */
public final class TargetDiscoveryAndProvisioningExtensionLookup {

    private static final String EXTENSION_POINT_TARGET_DISCOVERY_STRATEGY =
            "com.salesforce.bazel.core.model.target.discovery";
    private static final String ELLEMENT_TARGET_DISCOVERY_STRATEGY = "targetDiscoveryStrategy";
    private static final String ELLEMENT_TARGET_PROVISIONING_STRATEGY = "targetProvisioningStrategy";
    private static final String ATTR_CLASS = "class";
    private static final String ATTR_NAME = "name";

    /**
     * Searches the Eclipse extension registry for a strategy with the given name.
     *
     * @param name
     *            the name of the strategy (must not be <code>null</code>)
     * @return the strategy (never <code>null</code>)
     * @throws CoreException
     *             if no strategy was found
     */
    public TargetDiscoveryStrategy createTargetDiscoveryStrategy(String name) throws CoreException {
        return (TargetDiscoveryStrategy) findAndCreateStrategy(name, ELLEMENT_TARGET_DISCOVERY_STRATEGY);
    }

    /**
     * Searches the Eclipse extension registry for a strategy with the given name.
     *
     * @param name
     *            the name of the strategy (must not be <code>null</code>)
     * @return the strategy (never <code>null</code>)
     * @throws CoreException
     *             if no strategy was found
     */
    public TargetProvisioningStrategy createTargetProvisioningStrategy(String name) throws CoreException {
        return (TargetProvisioningStrategy) findAndCreateStrategy(name, ELLEMENT_TARGET_PROVISIONING_STRATEGY);
    }

    Object findAndCreateStrategy(String strategyName, String elementName) throws CoreException {
        var elements =
                Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_TARGET_DISCOVERY_STRATEGY);
        // TODO: do we need .map(IConfigurationElement::getChildren).map(Arrays::asList).flatMap(Collection::stream) here?
        var element = List.of(elements).stream()
                .filter(p -> elementName.equals(p.getName()) && strategyName.equals(p.getAttribute(ATTR_NAME)))
                .findAny();
        if (element.isEmpty()) {
            throw new CoreException(Status.error(
                format("No extensions available providing a '%s' with name '%s'!", elementName, strategyName)));
        }

        var strategy = (TargetDiscoveryStrategy) element.get().createExecutableExtension(ATTR_CLASS);
        if (strategy == null) {
            throw new CoreException(
                    Status.error(format("No object returned from extension factory for name '%s'", strategyName)));
        }
        return strategy;
    }

}