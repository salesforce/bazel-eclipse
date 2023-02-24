package com.salesforce.bazel.eclipse.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.eclipse.core.runtime.Platform;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

public class BazelCompilerUtils {
    // Command to find bazel path on windows
    public static final String WIN_BAZEL_FINDE_COMMAND = "where bazel";

    // Command to find bazel path on linux or mac
    public static final String LINUX_BAZEL_FINDE_COMMAND = "which bazel";

    public static final String BAZEL_EXECUTABLE_ENV_VAR = "BAZEL_EXECUTABLE";

    public static final String BAZEL_EXECUTABLE_DEFAULT_PATH = "/usr/local/bin/bazel";

    private static final LogHelper LOG = LogHelper.log(BazelCompilerUtils.class);

    public static String getBazelPath() {
        var path = getEnvBazelPath();

        if (path == null) {
            path = getOSBazelPath();
        }

        if (path == null) {
            path = BAZEL_EXECUTABLE_DEFAULT_PATH;
            LOG.warn("Bazel path has not been found, was used standart path {}", path);
        }

        if (Objects.isNull(path)) {
            LOG.error("Bazel executable path has not been found");
        } else {
            LOG.info("Bazel executable path is {}", path);
        }
        return path;
    }

    public static String getEnvBazelPath() {
        return System.getenv(BAZEL_EXECUTABLE_ENV_VAR);
    }

    /**
     * Provides details of the operating environment (OS, real vs. tests, etc)
     */
    public static OperatingEnvironmentDetectionStrategy getOperatingEnvironmentDetectionStrategy() {
        return ComponentContext.getInstance().getOsStrategy();
    }

    public static String getOSBazelPath() {
        String path = null;
        String command = null;
        if (Platform.OS_WIN32.equals(Platform.getOS())) {
            command = WIN_BAZEL_FINDE_COMMAND;
        }
        if (Platform.OS_LINUX.equals(Platform.getOS()) || Platform.OS_MACOSX.equals(Platform.getOS())) {
            command = LINUX_BAZEL_FINDE_COMMAND;
        }

        try (var reader =
                new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec(command).getInputStream()))) {
            path = reader.lines().findFirst().get();
        } catch (IOException | NoSuchElementException e) {
            path = null;
        }

        return path;
    }

}
