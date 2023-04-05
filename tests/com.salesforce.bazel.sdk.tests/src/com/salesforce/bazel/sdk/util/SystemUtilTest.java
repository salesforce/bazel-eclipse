package com.salesforce.bazel.sdk.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

public class SystemUtilTest {

    @Test
    @DisabledOnOs(OS.MAC)
    void isMac_returns_false_on_none_Mac() throws Exception {
        assertFalse(new SystemUtil().isMac());
    }

    @Test
    @EnabledOnOs(OS.MAC)
    void isMac_returns_true_on_Mac() throws Exception {
        assertTrue(new SystemUtil().isMac());
    }

    @Test
    @DisabledOnOs(OS.LINUX)
    void isUnix_returns_false_on_none_Linux() throws Exception {
        assertFalse(new SystemUtil().isUnix());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void isUnix_returns_true_on_Linux() throws Exception {
        assertTrue(new SystemUtil().isUnix());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void isWindows_returns_false_on_none_Windows() throws Exception {
        assertFalse(new SystemUtil().isWindows());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void isWindows_returns_true_on_Windows() throws Exception {
        assertTrue(new SystemUtil().isWindows());
    }
}
