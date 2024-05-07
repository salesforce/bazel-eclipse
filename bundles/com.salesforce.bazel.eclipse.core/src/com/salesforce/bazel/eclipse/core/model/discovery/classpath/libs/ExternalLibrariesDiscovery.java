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
package com.salesforce.bazel.eclipse.core.model.discovery.classpath.libs;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.TAG_NO_IDE;
import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

import com.google.idea.blaze.base.model.primitives.Label;
import com.salesforce.bazel.eclipse.core.model.BazelRuleAttributes;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.eclipse.core.projectview.GlobSetMatcher;
import com.salesforce.bazel.sdk.command.BazelQueryForTargetProtoCommand;
import com.salesforce.bazel.sdk.command.querylight.Target;

/**
 * A tool for discovering external libraries in a Bazel workspace
 */
public class ExternalLibrariesDiscovery extends LibrariesDiscoveryUtil {

    private final GlobSetMatcher externalJarsDiscoveryFilter;

    public ExternalLibrariesDiscovery(BazelWorkspace bazelWorkspace) throws CoreException {
        super(bazelWorkspace);
        externalJarsDiscoveryFilter = bazelWorkspace.getBazelProjectView().externalJarsDiscoveryFilter();
    }

    private Stream<BazelRuleAttributes> getExternalRepositoriesByRuleClassAndFiltered(Set<String> wantedRuleKinds)
            throws CoreException {
        return bazelWorkspace.getExternalRepositoriesByRuleClass(k -> wantedRuleKinds.contains(k))
                .filter(a -> !a.hasTag(TAG_NO_IDE))
                .filter(a -> (a.getName() != null) && externalJarsDiscoveryFilter.matches(Path.of(a.getName())));
    }

    public Collection<ClasspathEntry> query(IProgressMonitor progress) throws CoreException {

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

        var monitor = SubMonitor.convert(progress, "Querying Bazel..", 2);
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
        // note, some rules may do crazy stuff, just expand the set if you think we should be searching more for java_import
        Set<String> wantedRuleKinds = Set.of("jvm_import_external", "compat_repository");
        var externals = getExternalRepositoriesByRuleClassAndFiltered(wantedRuleKinds);

        // get java_import details from each external
        var setOfExternalsToQuery =
                externals.map(BazelRuleAttributes::getName).map(s -> format("@%s//...", s)).collect(joining(" "));
        if (setOfExternalsToQuery.isBlank()) {
            return;
        }

        var javaImportQuery = new BazelQueryForTargetProtoCommand(
                workspaceRoot.directory(),
                format("kind('java_import rule', set( %s ))", setOfExternalsToQuery),
                false,
                List.of(
                    "--proto:output_rule_attrs='srcjar,jars,testonly'",
                    "--noproto:rule_inputs_and_outputs",
                    "--noproto:locations",
                    "--noproto:default_values"),
                "Querying for external java_import library information");
        Collection<Target> javaImportTargets =
                bazelWorkspace.getCommandExecutor().runQueryWithoutLock(javaImportQuery);

        // parse info from each found target
        for (Target target : javaImportTargets) {
            var srcJar = findSingleJar(target.rule(), "srcjar", false /* not generated */);
            var jars = findJars(target.rule(), "jars", false /* not generated */);
            var testOnly = findBooleanAttribute(target.rule(), "testonly");
            var origin = Label.create(target.rule().name());

            collectJarsAsClasspathEntries(jars, srcJar, testOnly, origin, result);
        }
    }

    private void queryForRulesJvmExternalJars(Set<ClasspathEntry> result) throws CoreException {
        // get list of all external repos (
        Set<String> wantedRuleKinds = Set.of("coursier_fetch", "pinned_coursier_fetch"); // pinned is not always available
        var externals = getExternalRepositoriesByRuleClassAndFiltered(wantedRuleKinds);

        // filter out "unpinned" repositories
        var setOfExternalsToQuery = externals.map(BazelRuleAttributes::getName)
                .filter(s -> !s.startsWith("unpinned_"))
                .map(s -> format("@%s//...", s))
                .collect(joining(" "));
        if (setOfExternalsToQuery.isBlank()) {
            return;
        }

        // get jvm_import details from each external
        var javaImportQuery = new BazelQueryForTargetProtoCommand(
                workspaceRoot.directory(),
                format("kind('jvm_import rule', set( %s ))", setOfExternalsToQuery),
                false,
                List.of(
                    "--proto:output_rule_attrs='srcjar,jars,testonly'",
                    "--noproto:rule_inputs_and_outputs",
                    "--noproto:locations",
                    "--noproto:default_values"),
                "Querying for rules_jvm_external library information");
        Collection<Target> javaImportTargets =
                bazelWorkspace.getCommandExecutor().runQueryWithoutLock(javaImportQuery);

        /*
         * RJE (maven_install) consumes the jar from bazel-out/mnemonic/bin directory because it's generated
         *
         * We therefore prefix all locations from the rule to point to the bazel-bin/externals/... directory.
         * This depends on internal implementation design of maven_install, which is ok at this time.
         */

        // parse info from each found target
        for (Target target : javaImportTargets) {
            var srcJar = findSingleJar(target.rule(), "srcjar", true /* generated */);
            var jars = findJars(target.rule(), "jars", true /* generated */);
            var testOnly = findBooleanAttribute(target.rule(), "testonly");
            var origin = Label.create(target.rule().name());

            collectJarsAsClasspathEntries(jars, srcJar, testOnly, origin, result);
        }
    }
}
