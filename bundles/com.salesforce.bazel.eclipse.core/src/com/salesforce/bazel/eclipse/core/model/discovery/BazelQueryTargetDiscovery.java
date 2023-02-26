package com.salesforce.bazel.eclipse.core.model.discovery;

import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;

/**
 * Default implementation of {@link TargetDiscoveryStrategy} using <code>bazel query</code> to discovery targets.
 */
public class BazelQueryTargetDiscovery implements TargetDiscoveryStrategy {

    public static final String STRATEGY_NAME = "bazel-query";

    @Override
    public Collection<BazelTarget> discoverTargets(BazelPackage bazelPackage, IProgressMonitor progress)
            throws CoreException {
        // use the BazelPackageInfo as it may be cached already
        return bazelPackage.getBazelTargets();
    }
}
