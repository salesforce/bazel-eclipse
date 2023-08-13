package com.salesforce.bazel.eclipse.core.projectview;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.eclipse.core.runtime.IPath;

import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;

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
 * <li><code>projectMappings</code> - optional (default is empty), configures IDE to map targets to projects (useful in
 * combination with <code>--override_repository</code> to allow direct classpath resolution within the IDE)</li>
 * </ul>
 * </p>
 */
public record BazelProjectView(
        Collection<WorkspacePath> directoriesToImport,
        Collection<WorkspacePath> directoriesToExclude,
        Collection<TargetExpression> targets,
        boolean deriveTargetsFromDirectories,
        String workspaceType,
        Collection<String> additionalLanguages,
        String javaLanguageLevel,
        Collection<String> tsConfigRules,
        IPath bazelBinary,
        String targetDiscoveryStrategy,
        String targetProvisioningStrategy,
        Map<String, String> projectMappings) {

    public BazelProjectView {
        directoriesToImport = Collections.unmodifiableCollection(directoriesToImport);
        directoriesToExclude = Collections.unmodifiableCollection(directoriesToExclude);
        targets = Collections.unmodifiableCollection(targets);
        additionalLanguages = Collections.unmodifiableCollection(additionalLanguages);
        tsConfigRules = Collections.unmodifiableCollection(tsConfigRules);
    }

}
