// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.salesforce.bazel.sdk.command.shell;

import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.getPosixFilePermissions;
import static java.nio.file.Files.setPosixFilePermissions;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.util.SystemUtil;

/**
 * Adaption of <a href=
 * "https://github.com/JetBrains/intellij-community/blob/2df04061a5aefa7251055cbb12ab9ec8be3b6778/platform/util/src/com/intellij/util/EnvironmentUtil.java">EnvironmentUtil</a>
 * for obtaining environment information.
 */
public class EnvironmentUtil {

    public static class ShellEnvReader {
        public static final String DISABLE_OMZ_AUTO_UPDATE = "DISABLE_AUTO_UPDATE";

        private static final String INTELLIJ_ENVIRONMENT_READER = "INTELLIJ_ENVIRONMENT_READER";

        private static void deleteTempFile(Path file) {
            try {
                if (file != null) {
                    Files.delete(file);
                }
            } catch (NoSuchFileException ignore) {} catch (IOException e) {
                LOG.warn("Cannot delete temporary file", e);
            }
        }

        /**
         * Parses output of printenv binary or `env -0` command
         */
        private static Map<String, String> parseEnv(String text) {
            return parseEnv(text.split("\0"));
        }

        public static Map<String, String> parseEnv(String[] lines) {
            Set<String> toIgnore = new HashSet<>(
                    Arrays.asList("_", "PWD", "SHLVL", DISABLE_OMZ_AUTO_UPDATE, INTELLIJ_ENVIRONMENT_READER));
            var env = System.getenv();
            Map<String, String> newEnv = new HashMap<>();

            for (String line : lines) {
                if (!line.isEmpty()) {
                    var pos = line.indexOf('=');
                    if (pos <= 0) {
                        throw new RuntimeException("malformed:" + line);
                    }
                    var name = line.substring(0, pos);
                    if (!toIgnore.contains(name)) {
                        newEnv.put(name, line.substring(pos + 1));
                    } else if (env.containsKey(name)) {
                        newEnv.put(name, env.get(name));
                    }
                }
            }

            LOG.info("shell environment loaded (" + newEnv.size() + " vars)");
            return newEnv;
        }

        private final long myTimeoutMillis;

        private final SystemUtil systemUtil = SystemUtil.getInstance();

        /**
         * Creates an instance with the default time-out value of {@value #DEFAULT_SHELL_ENV_READING_TIMEOUT_MILLIS}
         * milliseconds.
         *
         * @see #ShellEnvReader(long)
         */
        public ShellEnvReader() {
            this(DEFAULT_SHELL_ENV_READING_TIMEOUT_MILLIS);
        }

        /**
         * @param timeoutMillis
         *            the time-out (in milliseconds) for reading environment variables.
         * @param systemUtil
         * @see #ShellEnvReader()
         */
        public ShellEnvReader(long timeoutMillis) {
            myTimeoutMillis = timeoutMillis;
        }

        protected List<String> getShellProcessCommand() {
            var shellScript = getShell();
            if ((shellScript == null) || shellScript.isEmpty()) {
                throw new RuntimeException("empty $SHELL");
            }
            if (!Files.isExecutable(Paths.get(shellScript))) {
                throw new RuntimeException("$SHELL points to a missing or non-executable file: " + shellScript);
            }
            return buildShellProcessCommand(shellScript, true, true, false);
        }

        public final Map<String, String> readShellEnv(Path file, Map<String, String> additionalEnvironment)
                throws IOException {
            String reader;

            if (systemUtil.isMac()) {
                var printenvDir = Files.createTempDirectory("ij-printenv-tmp-dir");
                var printenvExecutable = printenvDir.resolve("printenv");
                try (var in =
                        getClass().getClassLoader().getResourceAsStream("/printenv/bin/macos/" + MacOS_LOADER_BINARY)) {
                    copy(in, printenvExecutable);
                    var printenvPermissions = new HashSet<>(getPosixFilePermissions(printenvExecutable));
                    printenvPermissions.add(PosixFilePermission.OWNER_EXECUTE);
                    printenvPermissions.add(PosixFilePermission.GROUP_EXECUTE);
                    printenvPermissions.add(PosixFilePermission.OTHERS_EXECUTE);
                    setPosixFilePermissions(printenvExecutable, printenvPermissions);
                }

                reader = printenvExecutable.toString();
            } else {
                reader = SHELL_ENV_COMMAND + "' '" + ENV_ZERO_ARGUMENT;
            }

            // The temporary file is not pre-created, as writing to an already existing file using pipe might not be available
            // if the 'no-clobber' option is set for the shell
            var envDataFileDir = Files.createTempDirectory("ij-env-tmp-dir");
            var envDataFile = envDataFileDir.resolve("ij-shell-env-data.tmp");

            var readerCmd = new StringBuilder();
            if (file != null) {
                if (!Files.exists(file)) {
                    throw new NoSuchFileException(file.toString());
                }
                readerCmd.append(SHELL_SOURCE_COMMAND).append(" \"").append(file).append("\" && ");
            }

            readerCmd.append("'").append(reader).append("' > '").append(envDataFile.toAbsolutePath()).append("'");

            var command = getShellProcessCommand();
            var idx = command.indexOf(SHELL_COMMAND_ARGUMENT);
            if (idx >= 0) {
                // if there is already a command append command to the end
                command.set(idx + 1, command.get(idx + 1) + ';' + readerCmd);
            } else {
                command.add(SHELL_COMMAND_ARGUMENT);
                command.add(readerCmd.toString());
            }

            LOG.info("loading shell env: " + String.join(" ", command));
            try {
                return runProcessAndReadOutputAndEnvs(command, null, additionalEnvironment, envDataFile).getValue();
            } finally {
                deleteTempFile(envDataFile);
                deleteTempFile(envDataFileDir);
            }
        }

        /**
         * @param scriptEnvironmentProcessor
         *            a block which accepts the environment of the new process, allowing to add and remove environment
         *            variables.
         * @return Debugging output of the script, and the map of environment variables.
         * @throws IOException
         *             if the process fails to start, exits with a non-zero code, produces no output, or the file used
         *             to store the output cannot be read.
         * @see #runProcessAndReadOutputAndEnvs(List, Path, Map, Path)
         */
        protected final Map.Entry<String, Map<String, String>> runProcessAndReadOutputAndEnvs(List<String> command,
                Path workingDir, Consumer<? super Map<String, String>> scriptEnvironmentProcessor, Path envDataFile)
                throws IOException {
            final var builder = new ProcessBuilder(command);

            /*
             * Add, remove or change the environment variables.
             */
            scriptEnvironmentProcessor.accept(builder.environment());

            if (workingDir != null) {
                builder.directory(workingDir.toFile());
            }
            builder.environment().put(DISABLE_OMZ_AUTO_UPDATE, "true");
            builder.environment().put(INTELLIJ_ENVIRONMENT_READER, "true");

            var logFile = Files.createTempFile("ij-shell-env-log.", ".tmp");
            try {
                var process = builder.redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()))
                        .start();
                var exitCode = waitAndTerminateAfter(process, myTimeoutMillis);

                var envData = Files.exists(envDataFile)
                        ? new String(Files.readAllBytes(envDataFile), Charset.defaultCharset()) : "";
                var log = Files.exists(logFile) ? new String(Files.readAllBytes(logFile), Charset.defaultCharset())
                        : "(no log file)";
                if ((exitCode != 0) || envData.isEmpty()) {
                    if (!log.isEmpty()) {
                        LOG.info("stdout/stderr: " + log);
                    }
                    throw new IOException("command " + command + ", exit code: " + exitCode + ", log: " + log);
                }
                return new AbstractMap.SimpleImmutableEntry<>(log, parseEnv(envData));
            } finally {
                deleteTempFile(logFile);
            }
        }

        /**
         * @param scriptEnvironment
         *            the extra environment to be added to the environment of the new process. If {@code null}, the
         *            process environment won't be modified.
         * @throws IOException
         *             if the process fails to start, exits with a non-zero code, produces no output, or the file used
         *             to store the output cannot be read.
         * @see #runProcessAndReadOutputAndEnvs(List, Path, Consumer, Path)
         */
        protected final Map.Entry<String, Map<String, String>> runProcessAndReadOutputAndEnvs(List<String> command,
                Path workingDir, Map<String, String> scriptEnvironment, Path envDataFile) throws IOException {
            return runProcessAndReadOutputAndEnvs(command, workingDir, it -> {
                if (scriptEnvironment != null) {
                    // we might need the default environment for a process to launch correctly
                    it.putAll(scriptEnvironment);
                }
            }, envDataFile);
        }
    }

    private static Logger LOG = LoggerFactory.getLogger(EnvironmentUtil.class);

    /**
     * The default time-out to read the environment (in milliseconds).
     */
    private static final long DEFAULT_SHELL_ENV_READING_TIMEOUT_MILLIS = 20_000L;

    private static final String LANG = "LANG";
    private static final String LC_ALL = "LC_ALL";

    private static final String LC_CTYPE = "LC_CTYPE";

    private static final String DESKTOP_STARTUP_ID = "DESKTOP_STARTUP_ID";
    public static final String BASH_EXECUTABLE_NAME = "bash";
    public static final String SHELL_VARIABLE_NAME = "SHELL";
    private static final String SHELL_INTERACTIVE_ARGUMENT = "-i";
    public static final String SHELL_LOGIN_ARGUMENT = "-l";
    public static final String SHELL_COMMAND_ARGUMENT = "-c";
    public static final String SHELL_SOURCE_COMMAND = "source";
    public static final String SHELL_ENV_COMMAND = "/usr/bin/env";

    public static final String ENV_ZERO_ARGUMENT = "-0";

    public static final String MacOS_LOADER_BINARY = "printenv";

    /** singleton instance */
    private static final EnvironmentUtil instance = new EnvironmentUtil();

    /**
     * Holds the number of shell levels the current shell is running on top of. Tested with bash/zsh/fish/tcsh/csh/ksh.
     */
    private static final String SHLVL = "SHLVL";

    /** holds the environment map once loaded (to avoid expensive computation multiple times) */
    private static final AtomicReference<Map<String, String>> environmentMapReference = new AtomicReference<>();

    /**
     * Builds a login shell command list from the {@code shellScript} path.
     *
     * @param shell
     *            path to the shell, usually taken from the {@code SHELL} environment variable
     * @param isLogin
     *            {@code true} if the shell should be started in the login mode, usually with {@code -l} parameter
     * @param isInteractive
     *            {@code true} if the shell should be started in the interactive mode, usually with {@code -i} parameter
     * @param isCommand
     *            {@code true} if the shell should accept a command and not just a script name, usually via {@code -c}
     *            parameter
     * @return list of commands for starting a process, e.g. {@code ["/bin/bash", "-l", "-i", "-c"]}
     */
    public static List<String> buildShellProcessCommand(String shell, boolean isLogin, boolean isInteractive,
            boolean isCommand) {
        List<String> commands = new ArrayList<>();
        commands.add(shell);
        if (isLogin && !(shell.endsWith("/tcsh") || shell.endsWith("/csh"))) {
            // Csh/Tcsh does not allow using `-l` with any other options
            commands.add(SHELL_LOGIN_ARGUMENT);
        }
        if (isInteractive && !shell.endsWith("/fish")) {
            // Fish uses a single config file with conditions
            commands.add(SHELL_INTERACTIVE_ARGUMENT);
        }
        if (isCommand) {
            commands.add(SHELL_COMMAND_ARGUMENT);
        }
        return commands;
    }

    private static boolean checkIfLocaleAvailable(String candidateLanguageTerritory) {
        var available = Locale.getAvailableLocales();
        for (Locale l : available) {
            if (Objects.equals(l.toString(), candidateLanguageTerritory)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the shared, singleton instance
     */
    public static EnvironmentUtil getInstance() {
        return instance;
    }

    public static String getShell() {
        return System.getenv(SHELL_VARIABLE_NAME);
    }

    private static boolean isCharsetVarDefined(Map<String, String> env) {
        return !env.isEmpty() && (env.containsKey(LANG) || env.containsKey(LC_ALL) || env.containsKey(LC_CTYPE));
    }

    private static void setCharsetVar(Map<String, String> env) {
        if (!isCharsetVarDefined(env)) {
            var value = setLocaleEnv(env, Charset.defaultCharset());
            LOG.info("LC_CTYPE=" + value);
        }
    }

    public static String setLocaleEnv(Map<String, String> env, Charset charset) {
        var locale = Locale.getDefault();
        var language = locale.getLanguage();
        var country = locale.getCountry();

        var languageTerritory = "en_US";
        if (!language.isEmpty() && !country.isEmpty()) {
            var languageTerritoryFromLocale = language + '_' + country;
            if (checkIfLocaleAvailable(languageTerritoryFromLocale)) {
                languageTerritory = languageTerritoryFromLocale;
            }
        }

        var result = languageTerritory + '.' + charset.name();
        env.put(LC_CTYPE, result);
        return result;
    }

    /**
     * @param timeoutMillis
     *            the time-out (in milliseconds) for {@code process} to terminate.
     */
    private static int waitAndTerminateAfter(Process process, final long timeoutMillis) {
        var exitCode = waitFor(process, timeoutMillis);
        if (exitCode != null) {
            return exitCode;
        }
        LOG.warn("shell env loader is timed out");

        process.destroyForcibly();
        return -1;
    }

    /**
     * @param timeoutMillis
     *            the time-out (in milliseconds) for {@code process} to terminate.
     * @return the exit code of the process if it has already terminated, or it has terminated within the timeout; or
     *         {@code null} otherwise
     */
    private static Integer waitFor(Process process, final long timeoutMillis) {
        try {
            if (process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                return process.exitValue();
            }
        } catch (InterruptedException e) {
            LOG.info("Interrupted while waiting for process", e);
        }
        return null;
    }

    private final SystemUtil systemUtil = SystemUtil.getInstance();

    private final boolean isXWindows = systemUtil.isUnix() && !systemUtil.isMac();

    /**
     * <p>
     * A wrapper layer around {@link System#getenv()}.
     * </p>
     *
     * <p>
     * On Windows, the returned map is case-insensitive (i.e. {@code map.get("Path") == map.get("PATH")} holds).
     * </p>
     *
     * <p>
     * On macOS, things are complicated.<br/>
     * An app launched by a GUI launcher (Finder, Dock, Spotlight etc.) receives a pretty empty and useless environment,
     * since standard Unix ways of setting variables via e.g. ~/.profile do not work. What's more important, there are
     * no sane alternatives. This causes a lot of user complaints about tools working in a terminal not working when
     * launched from the IDE. To ease their pain, the IDE loads a shell environment (see {@link #getShellEnv} for gory
     * details) and returns it as the result.<br/>
     * And one more thing (c): locale variables on macOS are usually set by a terminal app - meaning they are missing
     * even from a shell environment above. This again causes user complaints about tools being unable to output
     * anything outside ASCII range when launched from the IDE. Resolved by adding LC_CTYPE variable to the map if it
     * doesn't contain explicitly set locale variables (LANG/LC_ALL/LC_CTYPE). See {@link #setCharsetVar} for details.
     * </p>
     *
     * @return unmodifiable map of the process environment.
     */
    public Map<String, String> getEnvironmentMap() throws IOException {
        var map = environmentMapReference.get();
        while (map == null) {
            map = loadShellEnvironment();
            if (!environmentMapReference.compareAndSet(null, map)) {
                map = environmentMapReference.get();
            }
        }
        return map;
    }

    private Map<String, String> getSystemEnv() {
        if (systemUtil.isWindows()) {
            Map<String, String> result = new TreeMap<>(CASE_INSENSITIVE_ORDER);
            result.putAll(System.getenv());
            return Collections.unmodifiableMap(result);
        }

        // at this point it's a unix, let's check if it is xwindows system
        if (isXWindows) {
            // DESKTOP_STARTUP_ID variable can be set by an application launcher in X Window environment.
            // It shouldn't be passed to child processes as per 'Startup notification protocol'
            // (https://specifications.freedesktop.org/startup-notification-spec/startup-notification-latest.txt).
            // Ideally, JDK should clear this variable, and it actually does, but the snapshot of the environment variables,
            // returned by `System#getenv`, is captured before the removal.
            var env = System.getenv();
            if (env.containsKey(DESKTOP_STARTUP_ID)) {
                env = new HashMap<>(env);
                env.remove(DESKTOP_STARTUP_ID);
                env = Collections.unmodifiableMap(env);
            }
            return env;
        }

        return System.getenv();
    }

    private Map<String, String> loadShellEnvironment() throws IOException {
        if (!shouldLoadShellEnv()) {
            return getSystemEnv();
        }

        var env = new ShellEnvReader().readShellEnv(null, null);
        setCharsetVar(env);
        return Collections.unmodifiableMap(env);
    }

    private boolean shouldLoadShellEnv() {
        if (!systemUtil.isMac()) {
            return false;
        }

        // On macOS, a login shell session is not run when a user logs in, so 'SHLVL > 0' likely means that the IDE is started from a terminal.
        var shLvl = System.getenv(SHLVL);
        try {
            if ((shLvl != null) && (Integer.parseInt(shLvl) > 0)) {
                LOG.info(
                    "loading shell env is skipped: SDK has been launched from a terminal (" + SHLVL + '=' + shLvl
                            + ')');
                return false;
            }
        } catch (NumberFormatException e) {
            LOG.info("loading shell env is skipped: SDK has been launched with malformed " + SHLVL + '=' + shLvl);
            return false;
        }

        return true;
    }
}
