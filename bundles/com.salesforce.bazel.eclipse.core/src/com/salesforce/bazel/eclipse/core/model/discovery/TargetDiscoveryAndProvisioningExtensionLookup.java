package com.salesforce.bazel.eclipse.core.model.discovery;

import static java.lang.String.format;
import static java.util.stream.Collectors.toCollection;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

import com.salesforce.bazel.eclipse.core.extensions.PriorityAttributeComparator;
import com.salesforce.bazel.eclipse.core.projectview.BazelProjectView;

/**
 * A simple lookup strategy for {@link TargetDiscoveryStrategy} and {@link TargetProvisioningStrategy} implementations
 * using the Eclipse extension registry.
 */
public final class TargetDiscoveryAndProvisioningExtensionLookup {

    private static final String EXTENSION_POINT_TARGET_DISCOVERY_STRATEGY =
            "com.salesforce.bazel.eclipse.core.model.target.discovery";
    private static final String ELLEMENT_TARGET_DISCOVERY_STRATEGY = "targetDiscoveryStrategy";
    private static final String ELLEMENT_TARGET_PROVISIONING_STRATEGY = "targetProvisioningStrategy";
    private static final String ELLEMENT_MACRO_CALL_ANALYZER = "macroCallAnalyzer";
    private static final String ATTR_CLASS = "class";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_FUNCTION_NAME = "functionName";

    /**
     * Searches the Eclipse extension registry for all {@link MacroCallAnalyzer} registered for a specific function
     * name.
     *
     * @param functionName
     *            the name of the function called by the macro (must not be <code>null</code>)
     * @return list of found analyzers in priority order (never <code>null</code>)
     * @throws CoreException
     *             if there was an error creating analyzers
     */
    public List<MacroCallAnalyzer> createMacroCallAnalyzers(String functionName) throws CoreException {
        var elements = findExtensionsWithAttributeValue(ELLEMENT_MACRO_CALL_ANALYZER, ATTR_FUNCTION_NAME, functionName);

        // sort based on priority
        elements.sort(new PriorityAttributeComparator());

        var analyzers = new ArrayList<MacroCallAnalyzer>(elements.size());
        for (IConfigurationElement element : elements) {
            var analyzer = (MacroCallAnalyzer) element.createExecutableExtension(ATTR_CLASS);
            if (analyzer == null) {
                throw new CoreException(
                        Status.error(format("No object returned from extension factory for element '%s'", element)));
            }
            analyzers.add(analyzer);
        }

        return analyzers;
    }

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
        var elements = findExtensionsWithAttributeValue(elementName, ATTR_NAME, strategyName);
        if (elements.isEmpty()) {
            throw new CoreException(
                    Status.error(
                        format("No extensions available providing a '%s' with name '%s'!", elementName, strategyName)));
        }

        // use the first one in the list
        var element = elements.iterator().next();
        var strategy = element.createExecutableExtension(ATTR_CLASS);
        if (strategy == null) {
            throw new CoreException(
                    Status.error(format("No object returned from extension factory for name '%s'", strategyName)));
        }
        return strategy;
    }

    private ArrayList<IConfigurationElement> findExtensionsWithAttributeValue(String elementName, String attributeName,
            String attributeValue) {
        var elements =
                Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_TARGET_DISCOVERY_STRATEGY);
        return List.of(elements)
                .stream()
                .filter(p -> elementName.equals(p.getName()) && attributeValue.equals(p.getAttribute(attributeName)))
                .collect(toCollection(ArrayList::new)); // by contract - return a modifiable ArrayList which may be sorted later
    }

}