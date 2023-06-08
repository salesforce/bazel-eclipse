/*-
 *
 */
package com.salesforce.bazel.eclipse.core.model.discovery;

import static java.lang.String.format;
import static java.nio.file.Files.isRegularFile;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Rule;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Target;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.java.sync.importer.ExecutionPathHelper;
import com.salesforce.bazel.eclipse.core.classpath.BazelClasspathScope;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.sdk.command.BazelQueryForLabelsCommand;
import com.salesforce.bazel.sdk.command.BazelQueryForTargetProtoCommand;

/**
 * This strategy implements computation of the {@link BazelWorkspace workspace project's} classpath.
 * <p>
 * In contrast to other projects the workspace project itself does not allow real development. Instead we use it for
 * discovering all imported repositories and making them available to Eclipse for global search and discovery.
 * </p>
 */
public class WorkspaceClasspathStrategy extends BaseProvisioningStrategy {

    private static Logger LOG = LoggerFactory.getLogger(WorkspaceClasspathStrategy.class);

    private static final String PREFIX_EXTERNAL = "//external:";

    public static ArtifactLocation externalJarImportToArtifactLocation(WorkspaceRoot workspaceRoot, String jar) {
        if ((jar == null) || jar.isBlank()) {
            return null;
        }
        if (Label.validate(jar) != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Ignoring invalid lable: {}", jar);
            }
            return null;
        }
        var jarLabel = Label.create(jar);

        if (!jarLabel.isExternal()) {
            LOG.warn("Suspicious result for //external:* query: {}", jarLabel);
            return null;
        }

        var executionRootRelativePath = jarLabel.blazePackage().isWorkspaceRoot()
                ? format("external/%s/%s", jarLabel.externalWorkspaceName(), jarLabel.targetName())
                : format("external/%s/%s/%s", jarLabel.externalWorkspaceName(), jarLabel.blazePackage(),
                    jarLabel.targetName());
        return ExecutionPathHelper.parse(workspaceRoot, BazelBuildSystemProvider.BAZEL, executionRootRelativePath);
    }

    /**
     * Computes the classpath for the workspace project.
     * <p>
     * Calling this with a project other then the workspace project ({@link BazelProject#isWorkspaceProject()} must
     * return <code>true</code>) leads to unpredictable result.
     * </p>
     *
     * @param workspaceProject
     *            the workspace project
     * @param workspace
     *            the workspace
     * @param scope
     *            the requested classpath code
     * @param monitor
     *            monitor for checking progress and cancellation
     * @return the computed classpath
     * @throws CoreException
     */
    public Collection<ClasspathEntry> computeClasspath(BazelProject workspaceProject, BazelWorkspace bazelWorkspace,
            BazelClasspathScope scope, IProgressMonitor monitor) throws CoreException {

        // There are several ways to query. We may need some extensibility here
        //
        // For example:
        //   1. get list of all external repos
        //      > bazel query "//external:*"
        //   2. query for java rules for each external repo
        //      > bazel query "kind('java_.* rule', @exernal_repo_name//...)"
        //
        // or:
        //   1. specific support for jvm_import_external
        //      > bazel query "kind(jvm_import_external, //external:*)"
        //

        // get list of all external repos
        var workspaceRoot = new WorkspaceRoot(bazelWorkspace.getLocation().toPath());
        var allExternalQuery = new BazelQueryForLabelsCommand(workspaceRoot.directory(),
                "kind(jvm_import_external, //external:*)", false);
        Collection<String> externals = bazelWorkspace.getCommandExecutor().runQueryWithoutLock(allExternalQuery);

        var setOfExternalsToQuery = externals.stream().filter(s -> s.startsWith(PREFIX_EXTERNAL))
                .map(s -> s.substring(PREFIX_EXTERNAL.length())).map(s -> format("@%s//...", s)).collect(joining(" "));
        var javaImportQuery = new BazelQueryForTargetProtoCommand(workspaceRoot.directory(),
                format("kind('java_import rule', set( %s ))", setOfExternalsToQuery), false);
        Collection<Target> javaImportTargets = bazelWorkspace.getCommandExecutor().runQueryWithoutLock(javaImportQuery);

        var jarInfo = new JavaClasspathJarInfo(bazelWorkspace);

        var needsFetch = false;
        Set<ClasspathEntry> result = new LinkedHashSet<>();
        for (Target target : javaImportTargets) {
            var srcJar = findSingleJar(target.getRule(), "srcjar", workspaceRoot);

            List<ArtifactLocation> jars = new ArrayList<>();
            target.getRule().getAttributeList().stream().filter(a -> a.getName().equals("jars"))
                    .map(Build.Attribute::getStringListValueList).collect(toList())
                    .forEach(list -> list.forEach(jar -> {
                        var jarArtifact = externalJarImportToArtifactLocation(workspaceRoot, jar);
                        if (jarArtifact != null) {
                            jars.add(jarArtifact);
                        }
                    }));

            var testOnly = target.getRule().getAttributeList().stream().filter(a -> a.getName().equals("testonly"))
                    .map(Build.Attribute::getBooleanValue).findAny();

            for (ArtifactLocation artifactLocation : jars) {
                var library = LibraryArtifact.builder().setClassJar(artifactLocation);
                if (srcJar != null) {
                    library.addSourceJar(srcJar);
                }
                var classpath = jarInfo.resolveJar(library.build());
                if (classpath != null) {
                    if (isRegularFile(classpath.getPath().toPath())) {
                        if (testOnly.isPresent() && testOnly.get()) {
                            classpath.getExtraAttributes().put(IClasspathAttribute.TEST, Boolean.TRUE.toString());
                        }
                        classpath.getExtraAttributes().put("bazel-target-name", target.getRule().getName());
                        result.add(classpath);
                    } else {
                        needsFetch = true;
                    }
                }
            }
        }

        if (needsFetch) {
            createBuildPathProblem(workspaceProject, Status.info(
                "Some external jars were ommitted from the classpath because they don't exist locally. Consider runing 'bazel fetch //...' to download any missing library."));
        }

        return result;
    }

    @Override
    public Map<BazelProject, Collection<ClasspathEntry>> computeClasspaths(Collection<BazelProject> bazelProjects,
            BazelWorkspace workspace, BazelClasspathScope scope, IProgressMonitor monitor) throws CoreException {
        if (bazelProjects.size() != 1) {
            throw new IllegalArgumentException("This strategy must only be used for the BazelWorkspace project!");
        }
        var workspaceProject = bazelProjects.iterator().next();
        return Map.of(workspaceProject, computeClasspath(workspaceProject, workspace, scope, monitor));
    }

    @Override
    protected List<BazelProject> doProvisionProjects(Collection<BazelTarget> targets, IProgressMonitor progress)
            throws CoreException {
        throw new IllegalStateException("this method must not be called");
    }

    private ArtifactLocation findSingleJar(Rule rule, String attributeName, WorkspaceRoot workspaceRoot) {
        var attribute = rule.getAttributeList().stream().filter(a -> a.getName().equals(attributeName)).findAny();
        if (attribute.isEmpty()) {
            return null;
        }

        return externalJarImportToArtifactLocation(workspaceRoot, attribute.get().getStringValue());
    }

}
