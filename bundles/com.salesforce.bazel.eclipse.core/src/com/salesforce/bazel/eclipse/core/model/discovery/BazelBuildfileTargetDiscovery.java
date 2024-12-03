package com.salesforce.bazel.eclipse.core.model.discovery;

import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;

/**
 * A simplification of {@link BazelQueryTargetDiscovery} which ignores targets and assumes all from selected packages.
 * <p>
 * This is recommended with the {@link BuildfileDrivenProvisioningStrategy}.
 * </p>
 */
public class BazelBuildfileTargetDiscovery extends BazelQueryTargetDiscovery implements TargetDiscoveryStrategy {

    public static final String STRATEGY_NAME = "buildfiles";

    @Override
    public Collection<TargetExpression> discoverTargets(BazelWorkspace bazelWorkspace,
            Collection<WorkspacePath> bazelPackages, IProgressMonitor progress) throws CoreException {
        return bazelPackages.stream().map(TargetExpression::allFromPackageNonRecursive).toList();
    }

}
