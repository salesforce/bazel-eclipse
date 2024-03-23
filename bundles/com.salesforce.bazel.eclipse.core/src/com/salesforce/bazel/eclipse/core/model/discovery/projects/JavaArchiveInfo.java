package com.salesforce.bazel.eclipse.core.model.discovery.projects;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;

import com.salesforce.bazel.eclipse.core.model.BazelPackage;

/**
 * Similar to {@link JavaSourceInfo} this class provides jar information to {@link JavaProjectInfo} (eg. from
 * <code>java_import</code>).
 */
public class JavaArchiveInfo {

    private final LinkedHashMap<Entry, Entry> jarsAndOptionalSrcJar;
    private final BazelPackage bazelPackage;

    /**
     * a map of all discovered jars and their srcjar.
     */
    private LinkedHashMap<IPath, IPath> localJarsAndSrcJars;

    /**
     * @param jarsAndOptionalSrcJar
     *            the list of jars to analyze
     * @param bazelPackage
     *            the {@link BazelPackage} which defines the scope of any analysis
     */
    public JavaArchiveInfo(LinkedHashMap<Entry, Entry> jarsAndOptionalSrcJar, BazelPackage bazelPackage) {
        this.jarsAndOptionalSrcJar = jarsAndOptionalSrcJar;
        this.bazelPackage = bazelPackage;
    }

    /**
     * Analyzes a map of jars whether they are available in the package
     *
     * @param result
     *            a multi status for collecting problems
     * @throws CoreException
     */
    public void analyzeJars(MultiStatus result) throws CoreException {
        var jarsInPackage = new LinkedHashMap<IPath, IPath>();

        for (Entry entry : jarsAndOptionalSrcJar.keySet()) {
            if (entry instanceof ResourceEntry jarResource) {
                var relativePath = jarResource.getRelativePath();
                var srcJarEntry = jarsAndOptionalSrcJar.get(entry);
                if (srcJarEntry instanceof ResourceEntry srcJarResource) {
                    jarsInPackage.put(relativePath, jarResource.getRelativePath());
                } else {
                    jarsInPackage.put(relativePath, null);
                }
            } else {
                result.add(
                    Status.error(
                        format(
                            "Unsupported jar import '%s'. Consider moving import target into its own package so the jar file becomes a local file in the package for IDE support.",
                            entry)));
            }
        }

        if (!jarsInPackage.isEmpty()) {
            localJarsAndSrcJars = jarsInPackage;
        }
    }

    public BazelPackage getBazelPackage() {
        return bazelPackage;
    }

    public IPath getBazelPackageLocation() {
        return getBazelPackage().getLocation();
    }

    /**
     * {@return the list of detected jars (relative to #getBazelPackageLocation())}
     */
    public Map<IPath, IPath> getJars() {
        return requireNonNull(localJarsAndSrcJars, "no jars discovered");
    }

    public boolean hasJars() {
        return (localJarsAndSrcJars != null) && !localJarsAndSrcJars.isEmpty();
    }

}
