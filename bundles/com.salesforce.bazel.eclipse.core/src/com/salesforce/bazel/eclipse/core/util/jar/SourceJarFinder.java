package com.salesforce.bazel.eclipse.core.util.jar;

import static java.nio.file.Files.isRegularFile;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import org.eclipse.core.runtime.IPath;

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation.Builder;

/**
 * A utility for finding source jars.
 * <p>
 * The utility supports some conventions from Bazel and rules_jvm_external to lookup source jars for a classpath jar.
 * </p>
 */
public class SourceJarFinder {

    private final static List<String> KNOWN_SUFFIXES = List.of("-sources.jar", "-src.jar");

    private final static List<String> KNOWN_PREFIXES =
            List.of("processed_" /* rules_jvm_external */, "" /* always try with empty prefix */);

    public static ArtifactLocation findSourceJar(ArtifactLocation jar) {
        var jarPath = Path.of(jar.getExecutionRootRelativePath());
        var directory = jarPath.getParent();
        var srcJarNames = getPotentialSourceJarNames(jarPath.getFileName().toString());

        for (String srcJarName : srcJarNames) {
            var srcJarPath = directory.resolve(srcJarName);
            if (isRegularFile(srcJarPath)) {
                ArtifactLocation.builder();
                return Builder.copy(jar)
                        .setRelativePath(Path.of(jar.getRelativePath()).resolveSibling(srcJarName).toString())
                        .build();
            }
        }

        return null;
    }

    public static IPath findSourceJar(Path jarPath) {
        var directory = jarPath.getParent();
        var srcJarNames = getPotentialSourceJarNames(jarPath.getFileName().toString());

        for (String srcJarName : srcJarNames) {
            var srcJar = directory.resolve(srcJarName);
            if (isRegularFile(srcJar)) {
                return IPath.fromPath(srcJar);
            }
        }

        return null;
    }

    public static String getPotentialNonSourceJarNames(String maybeSourceJarName) {
        // try removing known prefixes
        for (String prefix : KNOWN_PREFIXES) {
            if ((prefix.length() > 0) && maybeSourceJarName.startsWith(prefix)) {
                maybeSourceJarName = maybeSourceJarName.substring(prefix.length());
            }
        }

        // try removing known suffixes
        for (String suffix : KNOWN_SUFFIXES) {
            maybeSourceJarName = maybeSourceJarName.replace(suffix, ".jar");
        }

        return maybeSourceJarName;
    }

    public static Collection<String> getPotentialSourceJarNames(String jarName) {
        var potentialJarNames = new LinkedHashSet<String>(); // use deterministic order

        // try removing known prefixes
        for (String prefix : KNOWN_PREFIXES) {
            if ((prefix.length() > 0) && jarName.startsWith(prefix)) {
                jarName = jarName.substring(prefix.length());
            }
            // try removing known suffixes
            for (String suffix : KNOWN_SUFFIXES) {
                var srcJar = jarName.replace(".jar", suffix);
                potentialJarNames.add(srcJar);
            }
        }

        return potentialJarNames;
    }

    public static boolean isPotentialSourceJar(String jarNameOrPath) {
        // try removing known suffixes
        for (String suffix : KNOWN_SUFFIXES) {
            if (jarNameOrPath.endsWith(suffix)) {
                return true;
            }
        }

        return false;
    }

    private SourceJarFinder() {
        // no need to instantiate (may want to in the future if the lookup should be more extensible)
    }

}
