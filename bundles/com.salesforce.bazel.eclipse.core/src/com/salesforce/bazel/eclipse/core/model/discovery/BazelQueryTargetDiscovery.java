package com.salesforce.bazel.eclipse.core.model.discovery;

import static com.salesforce.bazel.eclipse.core.model.BazelWorkspace.findWorkspaceFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.sdk.command.BazelQueryForPackagesCommand;

/**
 * Default implementation of {@link TargetDiscoveryStrategy} using <code>bazel query</code> to discovery targets.
 */
public class BazelQueryTargetDiscovery implements TargetDiscoveryStrategy {

    public static final String STRATEGY_NAME = "bazel-query";

    private static Logger LOG = LoggerFactory.getLogger(BazelQueryTargetDiscovery.class);

    @Override
    public Collection<BazelPackage> discoverPackages(BazelWorkspace bazelWorkspace, IProgressMonitor progress)
            throws CoreException {
        var monitor = SubMonitor.convert(progress, 100);

        // bazel query 'buildfiles(//...)' --output package
        Collection<String> labels =
                bazelWorkspace.getCommandExecutor().runQueryWithoutLock(new BazelQueryForPackagesCommand(
                        bazelWorkspace.getLocation().toFile().toPath(), "buildfiles(//...)", true));

        monitor.worked(1);
        monitor.setWorkRemaining(labels.size());

        var result = new ArrayList<BazelPackage>();
        for (String label : labels) {
            monitor.worked(1);

            // ignore external packages
            if (label.startsWith("@")) {
                LOG.debug("Ignored external package during discover: {}", label);
                continue;
            }

            // ignore workspaces within the workspace (eg., test projects)
            // (if there is no entry in .bazelignore the query will return those folders)
            var packagePath = new Path(label);
            var packageWorkspaceLocation = findWorkspaceLocation(bazelWorkspace, packagePath);
            if (!bazelWorkspace.getLocation().equals(packageWorkspaceLocation)) {
                LOG.debug("Ignored package within nested workspace during discover: {} ({})", label,
                    packageWorkspaceLocation);
                continue;
            }

            var bazelPackage = bazelWorkspace.getBazelPackage(packagePath);
            if (bazelPackage.exists()) {
                result.add(bazelPackage);
            }

        }
        return result;
    }

    @Override
    public Collection<BazelTarget> discoverTargets(BazelWorkspace bazelWorkspace,
            Collection<BazelPackage> bazelPackages, IProgressMonitor progress) throws CoreException {
        // open all packages at once
        bazelWorkspace.open(bazelPackages);

        List<BazelTarget> targets = new ArrayList<>();
        for (BazelPackage bazelPackage : bazelPackages) {
            targets.addAll(bazelPackage.getBazelTargets());
        }
        return targets;

    }

    /**
     * Finds and returns the workspace location for a given package.
     * <p>
     * This method should be used for detected workspaces nested within the outer workspace.
     * </p>
     * <p>
     * Returns the location of the given workspace if the package path is empty.
     * </p>
     *
     * @param bazelWorkspace
     *            the workspace to check against
     * @param packagePath
     *            the package path
     * @return
     */
    protected IPath findWorkspaceLocation(BazelWorkspace bazelWorkspace, IPath packagePath) {
        if (packagePath.isAbsolute()) {
            throw new IllegalArgumentException("absolute path not allowed!");
        }

        // the root package never is
        if (packagePath.isEmpty()) {
            return bazelWorkspace.getLocation();
        }

        // there should be no other WORKSPACE file in either the package nor any of its parents
        var locationToCheck = bazelWorkspace.getLocation().append(packagePath);
        var workspaceFile = findWorkspaceFile(locationToCheck.toFile().toPath());
        if (workspaceFile != null) {
            return locationToCheck;
        }
        return findWorkspaceLocation(bazelWorkspace, packagePath.removeLastSegments(1));
    }
}
