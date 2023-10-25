/*-
 *
 */
package com.salesforce.bazel.eclipse.core.util.jar;

import static java.lang.String.format;

import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarFile;

import com.google.idea.blaze.base.model.primitives.Label;

/**
 * Extension to {@link JarFile} providing access to the <code>Target-Label</code>.
 */
public class BazelJarFile extends JarFile {

    public static final String JAR_MANIFEST_ATTRIBUTE_TARGET_LABEL = "Target-Label";

    public BazelJarFile(Path path) throws IOException {
        super(path.toFile());
    }

    /**
     * Inspects the jar manifest for an attribute {@value #JAR_MANIFEST_ATTRIBUTE_TARGET_LABEL} and returns its value.
     *
     * @return the value of manifest attribute {@value #JAR_MANIFEST_ATTRIBUTE_TARGET_LABEL} (maybe <code>null</code>)
     * @throws IOException
     */
    public Label getTargetLabel() throws IOException {
        var manifest = getManifest();
        if (manifest == null) {
            return null;
        }

        var mainAttributes = manifest.getMainAttributes();
        if (mainAttributes == null) {
            return null;
        }

        var targetLabel = mainAttributes.getValue(JAR_MANIFEST_ATTRIBUTE_TARGET_LABEL);
        if (targetLabel == null) {
            return null;
        }

        var error = Label.validate(targetLabel);
        if (error != null) {
            throw new IOException(format("Invalid Target-Label in jar file: %s", error));
        }

        return Label.create(targetLabel);
    }
}
