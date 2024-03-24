package com.salesforce.bazel.eclipse.core.model.discovery.projects;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.LinkedHashSet;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.MultiStatus;

import com.salesforce.bazel.eclipse.core.model.BazelPackage;

/**
 * Similar to {@link JavaArchiveInfo} this class provides annotation processor information to {@link JavaProjectInfo}.
 */
public class JavaPluginInfo {

    private final LinkedHashSet<LabelEntry> pluginDeps;
    private final BazelPackage bazelPackage;

    private LinkedHashSet<IPath> annotationProcessorJars;

    /**
     * @param jarsAndOptionalSrcJar
     *            the list of jars to analyze
     * @param bazelPackage
     *            the {@link BazelPackage} which defines the scope of any analysis
     */
    public JavaPluginInfo(LinkedHashSet<LabelEntry> pluginDeps, BazelPackage bazelPackage) {
        this.pluginDeps = pluginDeps;
        this.bazelPackage = bazelPackage;
    }

    /**
     * Analyzes the annotation processor plug-ins
     *
     * @param result
     *            a multi status for collecting problems
     * @throws CoreException
     */
    public void analyzeJars(MultiStatus result) throws CoreException {
        var jars = new LinkedHashSet<IPath>();

        for (LabelEntry entry : pluginDeps) {
            // TODO: collect jars from outputs from each entry
            // may also need deps
            // may need to make this a query?

            /*
             * this is actually not trivial, we need to resolve potentially external targets here
             * we may want to reuse some of ExternalLibrariesDiscovery, cache at the model
             * or maybe the model needs a way to query and cache for jar output of targets?
             */
        }

        if (!jars.isEmpty()) {
            annotationProcessorJars = jars;
        }
    }

    public BazelPackage getBazelPackage() {
        return bazelPackage;
    }

    public IPath getBazelPackageLocation() {
        return getBazelPackage().getLocation();
    }

    /**
     * {@return the list of detected jars}
     */
    public Collection<IPath> getJars() {
        return requireNonNull(annotationProcessorJars, "no jars discovered");
    }

    public boolean hasJars() {
        return (annotationProcessorJars != null) && !annotationProcessorJars.isEmpty();
    }

}
