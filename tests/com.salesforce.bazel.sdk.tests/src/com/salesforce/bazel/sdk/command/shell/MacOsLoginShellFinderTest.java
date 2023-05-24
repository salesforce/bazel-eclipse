package com.salesforce.bazel.sdk.command.shell;

import static java.lang.String.format;
import static java.nio.file.Files.isExecutable;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.MAC)
public class MacOsLoginShellFinderTest {

    @Test
    void detectLoginShell() throws Exception {
        var detectLoginShell = new MacOsLoginShellFinder().detectLoginShell();
        assertNotNull(detectLoginShell);
        assertTrue(isExecutable(detectLoginShell), () -> format("not executable: '%s'", detectLoginShell));
    }

    @Test
    void detectLoginShell_unknonw_users() throws Exception {
        assertThrows(IOException.class, () -> {
            new MacOsLoginShellFinder().detectLoginShell("foo-bar-" + System.nanoTime());
        });
    }
}
