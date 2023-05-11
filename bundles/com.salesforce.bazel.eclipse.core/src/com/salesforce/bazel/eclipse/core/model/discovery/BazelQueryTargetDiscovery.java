package com.salesforce.bazel.eclipse.core.model.discovery;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.sdk.command.BazelQueryForPackagesCommand;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * Default implementation of {@link TargetDiscoveryStrategy} using <code>bazel query</code> to discovery targets.
 */
public class BazelQueryTargetDiscovery implements TargetDiscoveryStrategy {

    public static final String STRATEGY_NAME = "bazel-query";

    @Override
    public Collection<BazelPackage> discoverPackages(BazelWorkspace bazelWorkspace, IProgressMonitor progress)
            throws CoreException {
        // bazel query 'buildfiles(//...)' --output package
        Collection<BazelLabel> labels =
                bazelWorkspace.getCommandExecutor().runQueryWithoutLock(new BazelQueryForPackagesCommand(
                        bazelWorkspace.getLocation().toFile().toPath(), "buildfiles(//...)", true));
        var result = new ArrayList<BazelPackage>();
        for (BazelLabel bazelLabel : labels) {
            if (bazelLabel.isExternalRepoLabel()) {
                continue;
            }

            var bazelPackage = bazelWorkspace.getBazelPackage(bazelLabel);
            if (bazelPackage.exists()) {
                result.add(bazelPackage);
            }
        }
        return result;
    }

    @Override
    public Collection<BazelTarget> discoverTargets(BazelPackage bazelPackage, IProgressMonitor progress)
            throws CoreException {
        // use the BazelPackageInfo as it may be cached already
        return bazelPackage.getBazelTargets();
    }
}
