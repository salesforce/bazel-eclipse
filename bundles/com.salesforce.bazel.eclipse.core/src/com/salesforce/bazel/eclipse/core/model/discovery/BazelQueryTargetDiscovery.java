package com.salesforce.bazel.eclipse.core.model.discovery;

import static com.salesforce.bazel.eclipse.core.model.BazelWorkspace.findWorkspaceFile;
import static java.lang.String.format;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.sdk.command.BazelQueryForLabelsCommand;
import com.salesforce.bazel.sdk.command.BazelQueryForPackagesCommand;

/**
 * Default implementation of {@link TargetDiscoveryStrategy} using <code>bazel query</code> to discovery targets.
 * <p>
 * Discovery is a two step process. In the first step, the workspace is queried for <strong>all</strong> available
 * packages. In the second step, a list of packages is queried for available targets. The list may be filtered (eg.,
 * based on list of directories in the project view).
 * </p>
 */
public class BazelQueryTargetDiscovery implements TargetDiscoveryStrategy {

    public static final String STRATEGY_NAME = "bazel-query";

    private static Logger LOG = LoggerFactory.getLogger(BazelQueryTargetDiscovery.class);

    @Override
    public Collection<WorkspacePath> discoverPackages(BazelWorkspace bazelWorkspace, IProgressMonitor progress)
            throws CoreException {
        var monitor = SubMonitor.convert(progress, 100);

        // bazel query 'buildfiles(//...)' --output package
        Collection<String> labels = bazelWorkspace.getCommandExecutor()
                .runQueryWithoutLock(
                    new BazelQueryForPackagesCommand(
                            bazelWorkspace.getLocation().toPath(),
                            "buildfiles(//...)",
                            true,
                            "Querying for all available packages in workspace"));

        monitor.worked(1);
        monitor.setWorkRemaining(labels.size());

        var result = new ArrayList<WorkspacePath>();
        for (String label : labels) {
            monitor.worked(1);

            // ignore external packages
            if (label.startsWith("@")) {
                LOG.debug("Ignored external package during discovery: {}", label);
                continue;
            }

            // ignore workspaces within the workspace (eg., test projects)
            // (if there is no entry in .bazelignore then query will return those folders)
            var packagePath = new Path(label);
            var packageWorkspaceLocation = findWorkspaceLocation(bazelWorkspace, packagePath);
            if (!bazelWorkspace.getLocation().equals(packageWorkspaceLocation)) {
                LOG.debug(
                    "Ignored package within nested workspace during discover: {} ({})",
                    label,
                    packageWorkspaceLocation);
                continue;
            }

            result.add(new WorkspacePath(packagePath.toString()));
        }
        return result;
    }

    @Override
    public Collection<TargetExpression> discoverTargets(BazelWorkspace bazelWorkspace,
            Collection<WorkspacePath> bazelPackages, IProgressMonitor progress) throws CoreException {
        try {
            var monitor = SubMonitor.convert(progress, "Discovering targets", 1 + bazelPackages.size());

            monitor.subTask("Querying for targets");
            Collection<String> labels = bazelWorkspace.getCommandExecutor()
                    .runQueryWithoutLock(
                        new BazelQueryForLabelsCommand(
                                bazelWorkspace.getLocation().toPath(),
                                format(
                                    "let all_target = kind(.*rule, %s) in $all_target - attr(tags, 'no-ide', $all_target)",
                                    bazelPackages.stream()
                                            .map(p -> "//" + p.relativePath().toString() + ":all")
                                            .collect(joining(" + "))),
                                true,
                                "Querying for targets to synchronize"));

            return labels.parallelStream().map(Label::fromStringSafe).filter(not(Objects::isNull)).toList();
        } finally {
            if (progress != null) {
                progress.done();
            }
        }
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

        // the root package never is nested in another workspace
        if (packagePath.isEmpty()) {
            return bazelWorkspace.getLocation();
        }

        // there should be no other WORKSPACE file in either the package nor any of its parents
        var possibleNestedWorkspacePath = bazelWorkspace.getLocation().append(packagePath);
        var workspaceFile = findWorkspaceFile(possibleNestedWorkspacePath.toPath());
        if (workspaceFile != null) {
            return possibleNestedWorkspacePath;
        }

        // continue checking the parent package for a nested WORKSPACE
        return findWorkspaceLocation(bazelWorkspace, packagePath.removeLastSegments(1));
    }
}
