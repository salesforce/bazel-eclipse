package com.salesforce.bazel.eclipse.core.projectview;

import java.util.Collection;
import java.util.Collections;

/**
 * A holder of all information of a <a href="https://ij.bazel.build/docs/project-views.html">Bazel Project View</a>.
 * <p>
 * This is an immutable record. The collections cannot be modified.
 * </p>
 * <p>
 * This provides the following extensions:
 * <ul>
 * <li><code>targetDiscoveryStrategy</code> - optional (default is <code>null</code>), configures IDE to use a non
 * standard target discovery strategy</li>
 * <li><code>targetProvisioningStrategy</code> - optional (default is <code>null</code>), configures IDE to use a non
 * standard target provisioning strategy</li>
 * </ul>
 * </p>
 */
public record BazelProjectView(
        Collection<String> directoriesToInclude,
        Collection<String> directoriesToExclude,
        Collection<String> targetsToInclude,
        Collection<String> targetsToExclude,
        boolean deriveTargetsFromDirectories,
        String workspaceType,
        Collection<String> additionalLanguages,
        String javaLanguageLevel,
        Collection<String> tsConfigRules,
        String targetDiscoveryStrategy,
        String targetProvisioningStrategy) {

    public BazelProjectView {
        directoriesToInclude = Collections.unmodifiableCollection(directoriesToInclude);
        directoriesToExclude = Collections.unmodifiableCollection(directoriesToExclude);
        targetsToInclude = Collections.unmodifiableCollection(targetsToInclude);
        targetsToExclude = Collections.unmodifiableCollection(targetsToExclude);
        additionalLanguages = Collections.unmodifiableCollection(additionalLanguages);
        tsConfigRules = Collections.unmodifiableCollection(tsConfigRules);
    }

}
