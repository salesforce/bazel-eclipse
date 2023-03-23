package com.salesforce.bazel.eclipse.core.model.discovery;

import static java.lang.String.format;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

import com.salesforce.bazel.eclipse.core.projectview.BazelProjectView;

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
     * Convenience method reading the configured strategy for a project view and falling back to a global default if no
     * target provision strategy is configured for the project view.
     *
     * @param projectView
     *            project view for obtaining {@link BazelProjectView#targetDiscoveryStrategy()}
     * @return the strategy (never <code>null</code>)
     * @throws CoreException
     *             if no strategy was found
     */
    public TargetDiscoveryStrategy createTargetDiscoveryStrategy(BazelProjectView projectView) throws CoreException {
        var discoveryStrategy = projectView.targetDiscoveryStrategy();
        if ((discoveryStrategy == null) || discoveryStrategy.equals("default")) {
            discoveryStrategy = BazelQueryTargetDiscovery.STRATEGY_NAME;
        }

        return createTargetDiscoveryStrategy(discoveryStrategy);
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
    public TargetDiscoveryStrategy createTargetDiscoveryStrategy(String name) throws CoreException {
        return (TargetDiscoveryStrategy) findAndCreateStrategy(name, ELLEMENT_TARGET_DISCOVERY_STRATEGY);
    }

    /**
     * Convenience method reading the configured strategy for a project view and falling back to a global default if no
     * target provision strategy is configured for the project view.
     *
     * @param projectView
     *            project view for obtaining {@link BazelProjectView#targetProvisioningStrategy()}
     * @return the strategy (never <code>null</code>)
     * @throws CoreException
     *             if no strategy was found
     */
    public TargetProvisioningStrategy createTargetProvisioningStrategy(BazelProjectView projectView)
            throws CoreException {
        var provisioningStrategy = projectView.targetProvisioningStrategy();
        if ((provisioningStrategy == null) || provisioningStrategy.equals("default")) {
            provisioningStrategy = ProjectPerTargetProvisioningStrategy.STRATEGY_NAME;
        }

        return createTargetProvisioningStrategy(provisioningStrategy);
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