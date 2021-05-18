package com.salesforce.bazel.app.buildy;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.command.CommandBuilder;
import com.salesforce.bazel.sdk.command.shell.ShellCommandBuilder;
import com.salesforce.bazel.sdk.console.CommandConsoleFactory;
import com.salesforce.bazel.sdk.console.StandardCommandConsoleFactory;
import com.salesforce.bazel.sdk.model.BazelProblem;
import com.salesforce.bazel.sdk.util.BazelPathHelper;

/**
 * This app, as a tool, is not useful. It simply uses the Bazel Java SDK to run a build on a Bazel workspace. In effect,
 * it is the equivalent to 'bazel build //...'.
 * <p>
 * The value in this app is the code sample, that shows how to use the SDK to write tools that are actually useful.
 * <p>
 * Usage: java -jar [path to jar file] [path to Bazel workspace]
 */
public class BazelBuildyApp {
    private static String bazelWorkspacePath;
    private static File bazelWorkspaceDir;

    // update this to your local environment
    private static final String BAZEL_EXECUTABLE = BazelCommandManager.getDefaultBazelExecutablePath();

    public static void main(String[] args) throws Exception {
        parseArgs(args);

        // set up the command line env
        File bazelExecutable = new File(BAZEL_EXECUTABLE);
        CommandConsoleFactory consoleFactory = new StandardCommandConsoleFactory();
        CommandBuilder commandBuilder = new ShellCommandBuilder(consoleFactory);
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = new BazelWorkspaceCommandRunner(bazelExecutable, null,
                commandBuilder, consoleFactory, bazelWorkspaceDir);

        // build all
        Set<String> targets = new HashSet<>();
        targets.add("//...");
        List<BazelProblem> problems = bazelWorkspaceCmdRunner.runBazelBuild(targets, new ArrayList<String>());

        // print the problems
        printResult(problems);
    }

    // HELPERS

    private static void parseArgs(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: java -jar buildyapp.jar [Bazel workspace absolute path]");
        }
        bazelWorkspacePath = args[0];
        bazelWorkspaceDir = new File(bazelWorkspacePath);
        bazelWorkspaceDir = BazelPathHelper.getCanonicalFileSafely(bazelWorkspaceDir);

        if (!bazelWorkspaceDir.exists()) {
            throw new IllegalArgumentException("Usage: java -jar buildyapp.jar [Bazel workspace absolute path]");
        }
        if (!bazelWorkspaceDir.isDirectory()) {
            throw new IllegalArgumentException("Usage: java -jar buildyapp.jar [Bazel workspace absolute path]");
        }
    }

    private static void printResult(List<BazelProblem> problems) {
        if (problems.size() == 0) {
            System.out.println("Build successful.");
        } else {
            System.out.println("Problems found:");
            for (BazelProblem problem : problems) {
                System.out.println(
                    problem.getResourcePath() + ":L" + problem.getLineNumber() + ":  " + problem.getDescription());
            }
        }
    }
}
