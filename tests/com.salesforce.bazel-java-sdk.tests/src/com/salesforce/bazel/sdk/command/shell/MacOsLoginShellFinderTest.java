package com.salesforce.bazel.sdk.command.shell;

import static java.lang.String.format;
import static java.nio.file.Files.isExecutable;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.salesforce.bazel.sdk.util.SystemUtil;

public class MacOsLoginShellFinderTest {

    @Test
    void detectLoginShell() throws Exception {
        assumeTrue(new SystemUtil().isMac());

        Path detectLoginShell = new MacOsLoginShellFinder().detectLoginShell();
        assertNotNull(detectLoginShell);
        assertTrue(isExecutable(detectLoginShell), () -> format("not executable: '%s'", detectLoginShell));
    }

    @Test
    void detectLoginShell_unknonw_users() throws Exception {
        assumeTrue(new SystemUtil().isMac());

        assertThrows(IOException.class, () -> {
            new MacOsLoginShellFinder().detectLoginShell("foo-bar-" + System.nanoTime());
        });
    }
}
