package com.salesforce.bazel.eclipse.core.util.jar;

import static java.nio.file.Files.isRegularFile;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.core.runtime.IPath;

/**
 * A utility for finding source jars.
 * <p>
 * The utility supports some conventions from Bazel and rules_jvm_external to lookup source jars for a classpath jar.
 * </p>
 */
public class SourceJarFinder {

    public static IPath findSourceJar(Path jarPath) {
        var directory = jarPath.getParent();
        var jarName = jarPath.getFileName().toString();

        // try removing known prefixes
        List<String> knownPrefixes =
                List.of("processed_" /* rules_jvm_external */, "" /* always try with empty prefix */);
        for (String prefix : knownPrefixes) {
            if ((prefix.length() > 0) && jarName.startsWith(prefix)) {
                jarName = jarName.substring(prefix.length());
            }
            // try removing known suffixes
            List<String> knownSuffixes = List.of("-sources.jar", "-src.jar");
            for (String suffix : knownSuffixes) {
                var srcJar = directory.resolve(jarName.replace(".jar", suffix));
                if (isRegularFile(srcJar)) {
                    return IPath.fromPath(srcJar);
                }
            }
        }

        return null;
    }

    private SourceJarFinder() {
        // no need to instantiate (may want to in the future if the lookup should be more extensible)
    }

}
