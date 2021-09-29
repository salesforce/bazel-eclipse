package com.salesforce.bazel.eclipse.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.NoSuchElementException;

import org.eclipse.core.runtime.Platform;

import com.salesforce.bazel.eclipse.activator.Activator;
import com.salesforce.bazel.eclipse.component.EclipseBazelComponentFacade;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

public class BazelCompilerUtils {
    // Command to find bazel path on windows
    public static final String WIN_BAZEL_FINDE_COMMAND = "where bazel";

    // Command to find bazel path on linux or mac
    public static final String LINUX_BAZEL_FINDE_COMMAND = "which bazel";

    public static final String BAZEL_EXECUTABLE_ENV_VAR = "BAZEL_EXECUTABLE";

    public static final String BAZEL_EXECUTABLE_DEFAULT_PATH = "/usr/local/bin/bazel";

    public static String getBazelPath() {
        String path = getEnvBazelPath();

        if (path == null) {
            path = getOSBazelPath();
        }

        if (path == null) {
            path = BAZEL_EXECUTABLE_DEFAULT_PATH;
            Activator.getDefault().logWarning("Bazel path has not been found, was used standart path " + path);
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
        return EclipseBazelComponentFacade.getInstance().getOsDetectionStrategy();
    }
    
    public static String getOSBazelPath() {
        String path = null;
        String command = null;
        if (Platform.getOS().equals(Platform.OS_WIN32)) {
            command = WIN_BAZEL_FINDE_COMMAND;
        }
        if (Platform.getOS().equals(Platform.OS_LINUX) || Platform.getOS().equals(Platform.OS_MACOSX)) {
            command = LINUX_BAZEL_FINDE_COMMAND;
        }

        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec(command).getInputStream()))) {
            path = reader.lines().findFirst().get();
        } catch (IOException | NoSuchElementException e) {
            path = null;
        }

        return path;
    }


}
