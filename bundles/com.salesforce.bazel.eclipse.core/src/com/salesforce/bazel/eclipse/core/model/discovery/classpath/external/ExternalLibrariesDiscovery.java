/*-
 * Copyright (c) 2023 Salesforce and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Salesforce - Partially adapted and heavily inspired from Eclipse JDT, M2E and PDE
 */
package com.salesforce.bazel.eclipse.core.model.discovery.classpath.external;

import static java.lang.String.format;
import static java.nio.file.Files.isRegularFile;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
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
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.discovery.JavaClasspathJarLocationResolver;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.sdk.command.BazelQueryForLabelsCommand;
import com.salesforce.bazel.sdk.command.BazelQueryForTargetProtoCommand;

/**
 * A tool for discovering external libraries in a Bazel workspace
 */
public class ExternalLibrariesDiscovery {

    private static Logger LOG = LoggerFactory.getLogger(ExternalLibrariesDiscovery.class);

    private static final String PREFIX_EXTERNAL = "//external:";

    private final BazelWorkspace bazelWorkspace;
    private final WorkspaceRoot workspaceRoot;
    private final JavaClasspathJarLocationResolver locationResolver;

    private boolean foundMissingJars;

    public ExternalLibrariesDiscovery(BazelWorkspace bazelWorkspace) throws CoreException {
        this.bazelWorkspace = bazelWorkspace;
        workspaceRoot = new WorkspaceRoot(bazelWorkspace.getLocation().toPath());
        locationResolver = new JavaClasspathJarLocationResolver(bazelWorkspace);
    }

    protected ArtifactLocation externalJarToArtifactLocation(String jar, boolean isGenerated) {
        if ((jar == null) || jar.isBlank()) {
            return null;
        }
        if (Label.validate(jar) != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Ignoring invalid label: {}", jar);
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
                : format(
                    "external/%s/%s/%s",
                    jarLabel.externalWorkspaceName(),
                    jarLabel.blazePackage(),
                    jarLabel.targetName());

        if (isGenerated) {
            /*
             * The jar file may be generated, in which case we cannot consume it directly from <execroot>/external/...
             * Instead we have to consume it from bazel-out/mnemonic/bin/external/...
             */
            var blazeInfo = locationResolver.getBlazeInfo();
            var bazelOutPrefix =
                    blazeInfo.getExecutionRoot().relativize(blazeInfo.getBlazeBin().getAbsoluteOrRelativePath());
            executionRootRelativePath = format("%s/%s", bazelOutPrefix, executionRootRelativePath);
        }

        return ExecutionPathHelper.parse(workspaceRoot, BazelBuildSystemProvider.BAZEL, executionRootRelativePath);
    }

    private ArtifactLocation findSingleJar(Rule rule, String attributeName, boolean isGenerated) {
        var attribute = rule.getAttributeList().stream().filter(a -> a.getName().equals(attributeName)).findAny();
        if (attribute.isEmpty()) {
            return null;
        }

        return externalJarToArtifactLocation(attribute.get().getStringValue(), isGenerated);
    }

    public BazelWorkspace getBazelWorkspace() {
        return bazelWorkspace;
    }

    /**
     * @return <code>true</code> if there jars were omitted from the result because they cannot be found locally
     *         (<code>false</code> otherwise)
     */
    public boolean isFoundMissingJars() {
        return foundMissingJars;
    }

    public Set<ClasspathEntry> query(IProgressMonitor progress) throws CoreException {

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

        var monitor = SubMonitor.convert(progress, "Quering Bazel..", 2);
        try {
            Set<ClasspathEntry> result = new LinkedHashSet<>();

            monitor.subTask("java_import");
            queryForJavaImports(result);
            monitor.checkCanceled();

            monitor.subTask("rules_jvm_external");
            queryForRulesJvmExternalJars(result);
            monitor.checkCanceled();

            return result;
        } finally {
            progress.done();
        }

    }

    private void queryForJavaImports(Set<ClasspathEntry> result) throws CoreException {
        // get list of all interesting external repo rules
        // note, some rules may do crazy stuff, just expand the regex if you think we should be searching more for java_import
        var allExternalQuery = new BazelQueryForLabelsCommand(
                workspaceRoot.directory(),
                "kind(\"jvm_import_external|compat_repository\", //external:*)",
                false);
        Collection<String> externals = bazelWorkspace.getCommandExecutor().runQueryWithoutLock(allExternalQuery);

        // get java_import details from each external
        var setOfExternalsToQuery = externals.stream()
                .filter(s -> s.startsWith(PREFIX_EXTERNAL))
                .map(s -> s.substring(PREFIX_EXTERNAL.length()))
                .map(s -> format("@%s//...", s))
                .collect(joining(" "));
        var javaImportQuery = new BazelQueryForTargetProtoCommand(
                workspaceRoot.directory(),
                format("kind('java_import rule', set( %s ))", setOfExternalsToQuery),
                false);
        Collection<Target> javaImportTargets = bazelWorkspace.getCommandExecutor().runQueryWithoutLock(javaImportQuery);

        // parse info from each found target
        for (Target target : javaImportTargets) {
            var srcJar = findSingleJar(target.getRule(), "srcjar", false /* not generated */);

            List<ArtifactLocation> jars = new ArrayList<>();
            target.getRule()
                    .getAttributeList()
                    .stream()
                    .filter(a -> a.getName().equals("jars"))
                    .map(Build.Attribute::getStringListValueList)
                    .collect(toList())
                    .forEach(list -> list.forEach(jar -> {
                        var jarArtifact = externalJarToArtifactLocation(jar, false /* not generated */);
                        if (jarArtifact != null) {
                            jars.add(jarArtifact);
                        }
                    }));

            var testOnly = target.getRule()
                    .getAttributeList()
                    .stream()
                    .filter(a -> a.getName().equals("testonly"))
                    .map(Build.Attribute::getBooleanValue)
                    .findAny();

            for (ArtifactLocation artifactLocation : jars) {
                var library = LibraryArtifact.builder().setClassJar(artifactLocation);
                if (srcJar != null) {
                    library.addSourceJar(srcJar);
                }
                var classpath = locationResolver.resolveJar(library.build());
                if (classpath != null) {
                    if (isRegularFile(classpath.getPath().toPath())) {
                        if (testOnly.isPresent() && testOnly.get()) {
                            classpath.getExtraAttributes().put(IClasspathAttribute.TEST, Boolean.TRUE.toString());
                        }
                        classpath.getExtraAttributes().put("bazel-target-name", target.getRule().getName());
                        result.add(classpath);
                    } else {
                        foundMissingJars = true;
                    }
                }
            }
        }
    }

    private boolean queryForRulesJvmExternalJars(Set<ClasspathEntry> result) throws CoreException {

        // detect missing jars
        var needsFetch = false;

        // get list of all external repos
        var allExternalQuery = new BazelQueryForLabelsCommand(
                workspaceRoot.directory(),
                "kind('.*coursier_fetch', //external:*)",
                false);
        Collection<String> externals = bazelWorkspace.getCommandExecutor().runQueryWithoutLock(allExternalQuery);

        // ignore "unpinned" repositories
        var setOfExternalsToQuery = externals.stream()
                .filter(s -> s.startsWith(PREFIX_EXTERNAL) && !s.contains(":unpinned_"))
                .map(s -> s.substring(PREFIX_EXTERNAL.length()))
                .map(s -> format("@%s//...", s))
                .collect(joining(" "));

        // get jvm_import details from each external
        var javaImportQuery = new BazelQueryForTargetProtoCommand(
                workspaceRoot.directory(),
                format("kind('jvm_import rule', set( %s ))", setOfExternalsToQuery),
                false);
        Collection<Target> javaImportTargets = bazelWorkspace.getCommandExecutor().runQueryWithoutLock(javaImportQuery);

        /*
         * RJE (maven_install) consumes the jar from bazel-out/mnemonic/bin directory because it's generated
         *
         * We therefore prefix all locations from the rule to point to the bazel-bin/externals/... directory.
         * The depends on internal implementation design of maven_install, which is ok at this time.
         */

        // parse info from each found target
        for (Target target : javaImportTargets) {
            var srcJar = findSingleJar(target.getRule(), "srcjar", true /* generated */);

            List<ArtifactLocation> jars = new ArrayList<>();
            target.getRule()
                    .getAttributeList()
                    .stream()
                    .filter(a -> a.getName().equals("jars"))
                    .map(Build.Attribute::getStringListValueList)
                    .collect(toList())
                    .forEach(list -> list.forEach(jar -> {
                        var jarArtifact = externalJarToArtifactLocation(jar, true /* generated */);
                        if (jarArtifact != null) {
                            jars.add(jarArtifact);
                        }
                    }));

            var testOnly = target.getRule()
                    .getAttributeList()
                    .stream()
                    .filter(a -> a.getName().equals("testonly"))
                    .map(Build.Attribute::getBooleanValue)
                    .findAny();

            for (ArtifactLocation artifactLocation : jars) {
                var library = LibraryArtifact.builder().setClassJar(artifactLocation);
                if (srcJar != null) {
                    library.addSourceJar(srcJar);
                }
                var classpath = locationResolver.resolveJar(library.build());
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
        return needsFetch;
    }
}
