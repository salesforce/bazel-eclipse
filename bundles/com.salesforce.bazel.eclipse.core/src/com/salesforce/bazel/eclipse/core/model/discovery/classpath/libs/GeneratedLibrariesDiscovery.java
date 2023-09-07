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

import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.devtools.build.lib.query2.proto.proto2api.Build.GeneratedFile;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Target;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelRuleAttributes;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.eclipse.core.util.jar.SourceJarFinder;
import com.salesforce.bazel.sdk.command.BazelQueryForTargetProtoCommand;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * A tool for discovering external libraries in a Bazel workspace
 */
public class GeneratedLibrariesDiscovery extends LibrariesDiscoveryUtil {

    private static Logger LOG = LoggerFactory.getLogger(GeneratedLibrariesDiscovery.class);

    public GeneratedLibrariesDiscovery(BazelWorkspace bazelWorkspace) throws CoreException {
        super(bazelWorkspace);
    }

    private ArtifactLocation findGeneratedJar(BazelPackage bazelPackage, String jarName) {
        return jarLabelToArtifactLocation(
            format("//%s:%s", bazelPackage.getWorkspaceRelativePath().toString(), jarName),
            true);
    }

    public Collection<ClasspathEntry> query(IProgressMonitor progress) throws CoreException {

        var monitor = SubMonitor.convert(progress, "Quering Bazel..", 2);
        try {
            Set<ClasspathEntry> result = new LinkedHashSet<>();

            monitor.subTask("generated jars");
            queryForGeneratedJars(result);
            monitor.checkCanceled();

            monitor.subTask("rules_jvm_external");
            queryForRulesJvmExternalJars(result);
            monitor.checkCanceled();

            return result;
        } finally {
            progress.done();
        }

    }

    private void queryForGeneratedJars(Set<ClasspathEntry> result) throws CoreException {
        var generatedJarQuery = new BazelQueryForTargetProtoCommand(
                workspaceRoot.directory(),
                "filter(\".*\\.jar$\", kind(\"generated file\", //...:*))",
                false,
                "Querying for generated jar files");
        Collection<Target> generatedJarTargets =
                bazelWorkspace.getCommandExecutor().runQueryWithoutLock(generatedJarQuery);

        // group by generating targets
        Map<String, List<String>> jarsByGeneratingRuleLabel = generatedJarTargets.stream()
                .map(Target::getGeneratedFile)
                .collect(groupingBy(GeneratedFile::getGeneratingRule, mapping(GeneratedFile::getName, toList())));

        // filter out java_binary targets and others developers cannot use typically as dependencies
        Set<String> rulesToIgnore = Set.of("java_binary");

        for (String generatingRule : jarsByGeneratingRuleLabel.keySet()) {
            var bazelTarget = bazelWorkspace.getBazelTarget(new BazelLabel(generatingRule));
            if (!bazelTarget.exists()) {
                continue;
            }

            if (rulesToIgnore.contains(bazelTarget.getRuleClass())) {
                continue;
            }

            Collection<String> jars = jarsByGeneratingRuleLabel.get(generatingRule)
                    .stream()
                    .map(Label::create)
                    .map(Label::targetName)
                    .map(TargetName::toString)
                    .collect(toCollection(HashSet::new));

            // extract src jars
            List<String> srcJars = jars.stream().filter(SourceJarFinder::isPotentialSourceJar).collect(toList());
            jars.removeIf(SourceJarFinder::isPotentialSourceJar);
            if (srcJars.size() > 1) {
                LOG.warn(
                    "Found target '{}' with more than one source jar. Only the first one is supported. Dropping remaining from '{}'",
                    generatingRule,
                    srcJars);
            }

            var srcJar = srcJars.size() > 0 ? findGeneratedJar(bazelTarget.getBazelPackage(), srcJars.get(0)) : null;
            var classpathJars = jars.stream()
                    .map(jar -> findGeneratedJar(bazelTarget.getBazelPackage(), jar))
                    .filter(Objects::nonNull)
                    .collect(toList());
            if (classpathJars.isEmpty()) {
                LOG.debug("No jars left in target '{}", bazelTarget);
                continue;
            }
            var testOnly = Optional.ofNullable(bazelTarget.getRuleAttributes().getBoolean("testonly"));
            var origin = bazelTarget.getLabel().toPrimitive();

            collectJarsAsClasspathEntries(classpathJars, srcJar, testOnly, origin, result);
        }
    }

    private void queryForRulesJvmExternalJars(Set<ClasspathEntry> result) throws CoreException {
        // get list of all external repos (
        Set<String> wantedRuleKinds = Set.of("coursier_fetch", "pinned_coursier_fetch"); // pinned is not always available
        var externals = bazelWorkspace.getExternalRepositoriesByRuleClass(k -> wantedRuleKinds.contains(k));

        // filter out "unpinned" repositories
        var setOfExternalsToQuery = externals.map(BazelRuleAttributes::getName)
                .filter(s -> s.startsWith("unpinned_"))
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
                "Querying for rules_jvm_external library information");
        Collection<Target> javaImportTargets = bazelWorkspace.getCommandExecutor().runQueryWithoutLock(javaImportQuery);

        /*
         * RJE (maven_install) consumes the jar from bazel-out/mnemonic/bin directory because it's generated
         *
         * We therefore prefix all locations from the rule to point to the bazel-bin/externals/... directory.
         * This depends on internal implementation design of maven_install, which is ok at this time.
         */

        // parse info from each found target
        for (Target target : javaImportTargets) {
            var srcJar = findSingleJar(target.getRule(), "srcjar", true /* generated */);
            var jars = findJars(target.getRule(), "jars", true /* generated */);
            var testOnly = findBooleanAttribute(target.getRule(), "testonly");
            var origin = Label.create(target.getRule().getName());

            collectJarsAsClasspathEntries(jars, srcJar, testOnly, origin, result);
        }
    }
}
