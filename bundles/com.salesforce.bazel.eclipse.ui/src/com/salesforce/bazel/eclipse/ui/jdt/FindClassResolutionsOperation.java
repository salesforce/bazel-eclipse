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
 *      Salesforce - copied and adapted from PDE FindClassResolutionsOperation
*/
package com.salesforce.bazel.eclipse.ui.jdt;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jface.operation.IRunnableWithProgress;

import com.google.idea.blaze.base.model.primitives.Label;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.util.TypeLocator;
import com.salesforce.bazel.eclipse.ui.jdt.JavaResolutionFactory.ProposalType;
import com.salesforce.bazel.eclipse.ui.utils.JavaSearchUtil;

/**
 * This Operation is used to find possible resolutions to an unresolved class reference in a Bazel project.
 * <p>
 * When it is run, it will pass any ExportPackageDescriptions which provide the package to the
 * AbstractClassResolutionCollector. The AbstractClassResolutionCollector is responsible for creating the appropriate
 * resolutions.
 * </p>
 */
public class FindClassResolutionsOperation implements IRunnableWithProgress {

    /**
     * This class is meant to be sub-classed or instantiated for use with FindClassResolutionsOperation. The subclass is
     * responsible for creating corresponding proposals with the help of JavaResolutionFactory.
     *
     * @see JavaResolutionFactory
     */
    public static class ClassResolutionCollector {

        protected final Collection<Object> proposals;
        protected final ProposalType proposalType;
        private final int proposalRelevance;

        public ClassResolutionCollector(Collection<Object> proposals, ProposalType proposalType,
                int proposalRelevance) {
            this.proposals = proposals;
            this.proposalType = proposalType;
            this.proposalRelevance = proposalRelevance;
        }

        public void addAddDependencyModification(BazelProject bazelProject, Label label,
                ClasspathEntry classpathEntry) {
            try {
                collectProposal(
                    JavaResolutionFactory.createAddDependencyProposal(
                        bazelProject.getBazelBuildFile(),
                        bazelProject,
                        label,
                        classpathEntry,
                        proposalType,
                        proposalRelevance));
            } catch (CoreException e) {
                // ignore
            }
        }

        protected void collectProposal(Object proposal) {
            if (proposal != null) {
                proposals.add(proposal);
            }
        }
    }

    final String className;
    final BazelProject bazelProject;
    final ClassResolutionCollector collector;
    final TypeLocator typeLocator;

    /**
     * This class is used to try to find resolutions to unresolved java classes.
     * <p>
     * When a missing <code>deps</code> might resolve a class, the Label which contains the package will be passed to
     * the AbstractClassResoltuionCollector. The collector is then responsible for creating an corresponding resolutions
     * with the help of JavaResolutionFactory.
     * </p>
     *
     * @param project
     *            the project which contains the unresolved class
     * @param className
     *            the name of the class which is unresolved
     * @param collector
     *            a subclass of AbstractClassResolutionCollector to collect/handle possible resolutions
     * @throws CoreException
     */
    public FindClassResolutionsOperation(final BazelProject bazelProject, final String className,
            final ClassResolutionCollector collector) throws CoreException {
        this.bazelProject = bazelProject;
        this.className = className;
        this.collector = collector;
        typeLocator = new TypeLocator(bazelProject.getBazelWorkspace());
    }

    /**
     * Finds all types in Bazel projects.
     * <p>
     * The types will be filtered based on Bazel targets already on the classpath. TODO: and system packages
     * </p>
     *
     * @param fullyQualifiedTypeName
     *            the fully qualified type to search for
     * @param monitor
     * @return the map of types to import
     */
    private Map<Label, ClasspathEntry> findValidLabels(String packageName, String typeName, IProgressMonitor monitor) {
        var subMonitor = SubMonitor.convert(monitor);

        try {
            var currentBazelProject = bazelProject;
            var searchScope = JavaSearchUtil.createScopeIncludingAllWorkspaceProjectsButSelected(currentBazelProject);

            final var currentJavaProject = JavaCore.create(bazelProject.getProject());
            final Map<Label, ClasspathEntry> bazelInfos = new HashMap<>();
            SearchRequestor requestor = new SearchRequestor() {

                @Override
                public void acceptSearchMatch(SearchMatch aMatch) throws CoreException {
                    var element = aMatch.getElement();
                    // Only try to import types we can access (Bug 406232)
                    if ((element instanceof IType type)
                            && (Flags.isPublic(type.getFlags()) && !currentJavaProject.equals(type.getJavaProject()))) {
                        var packageFragment = type.getPackageFragment();
                        if (packageFragment.exists()) {
                            var bazelInfo = typeLocator.findBazelInfo(type);
                            if (bazelInfo != null) {
                                bazelInfos.put(bazelInfo.originLabel(), bazelInfo.classpathEntry());
                            }
                        }
                    } else if (((element instanceof IPackageFragment packageFragment)
                            && !currentJavaProject.equals(packageFragment.getJavaProject()))
                            && packageFragment.exists()) {
                        var bazelInfo = typeLocator.findBazelInfo(packageFragment);
                        if (bazelInfo != null) {
                            bazelInfos.put(bazelInfo.originLabel(), bazelInfo.classpathEntry());
                        }
                    }
                }
            };

            var typeOrPackagePattern = SearchPattern.createPattern(
                typeName != null ? packageName + "." + typeName : packageName,
                typeName != null ? IJavaSearchConstants.TYPE : IJavaSearchConstants.PACKAGE,
                IJavaSearchConstants.DECLARATIONS,
                SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE);
            if (typeOrPackagePattern == null) {
                return Collections.emptyMap();
            }
            new SearchEngine().search(
                typeOrPackagePattern,
                new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
                searchScope,
                requestor,
                subMonitor.split(1));

            if (!bazelInfos.isEmpty()) {
                // remove packages that are already imported
                if (bazelProject.isTargetProject()) {
                    removeAllTargetDepsFromMap(bazelInfos, bazelProject.getBazelTarget());
                } else if (bazelProject.isPackageProject()) {
                    for (BazelTarget bazelTarget : bazelProject.getBazelTargets()) {
                        removeAllTargetDepsFromMap(bazelInfos, bazelTarget);
                    }
                }
                return bazelInfos;
            }

            return Collections.emptyMap();
        } catch (CoreException ex) {
            // ignore, return an empty set
            return Collections.emptyMap();
        } finally {
            if (monitor != null) {
                monitor.done();
            }
        }
    }

    private void removeAllTargetDepsFromMap(final Map<Label, ClasspathEntry> bazelInfos, BazelTarget bazelTarget)
            throws CoreException {
        var deps = bazelTarget.getRuleAttributes().getStringList("deps");
        if (deps != null) {
            for (String dep : deps) {
                bazelInfos.remove(Label.create(dep));
            }
        }
    }

    @Override
    public void run(final IProgressMonitor monitor) {
        var idx = className.lastIndexOf('.');
        var packageName = idx != -1 ? className.substring(0, idx) : null;
        var typeName = className.substring(idx + 1);
        if ((typeName.length() == 1) && (typeName.charAt(0) == '*')) {
            typeName = null;
        }

        var validLabels = findValidLabels(packageName, typeName, monitor);
        for (Entry<Label, ClasspathEntry> labelAndEntry : validLabels.entrySet()) {
            collector.addAddDependencyModification(bazelProject, labelAndEntry.getKey(), labelAndEntry.getValue());
        }
    }
}