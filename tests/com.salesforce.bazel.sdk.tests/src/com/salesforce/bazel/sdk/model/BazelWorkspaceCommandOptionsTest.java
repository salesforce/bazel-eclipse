package com.salesforce.bazel.sdk.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.mockito.Mockito;

import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandOptions;

/**
 * Tests .bazelrc options gathering and parsing. Note that we don't parse .bazelrc directly (as that would be hard, and
 * there can be multiple .bazelrc type files).
 */
public class BazelWorkspaceCommandOptionsTest {

    @Test
    public void testCommandOptionsParsing() {
        // the options class doesn't use BazelWorkspace for anything important, just send in a mock
        var bazelWorkspace = Mockito.mock(BazelWorkspace.class);
        var options = new BazelWorkspaceCommandOptions(bazelWorkspace);

        // simulate some log lines from 'bazel test --announce_rc' command
        List<String> optionLines = new ArrayList<>();
        optionLines.add("INFO: Options provided by the client:");
        optionLines.add("  Inherited 'common' options: --isatty=1 --terminal_columns=260");
        optionLines.add("INFO: Reading rc options for 'test' from " + "/Users/darth/dev/deathstar/.base-bazelrc:"); // $SLASH_OK test code
        optionLines.add(
            "  Inherited 'build' options: --javacopt=-source 8 -target 8 --host_javabase=//tools/jdk:my-linux-jdk11 --javabase=//tools/jdk:my-linux-jdk8 --stamp"); // $SLASH_OK bazel path
        optionLines.add("INFO: Reading rc options for 'test' from /Users/darth/dev/deathstar/.user-bazelrc:");
        optionLines.add(
            "  'test' options: --explicit_java_test_deps=true --test_timeout=45,180,300,360 --test_tag_filters=-flaky");
        optionLines.add("INFO: Analyzed 0 targets (0 packages loaded, 0 targets configured).");
        optionLines.add("INFO: Found 0 test targets...");

        // parse the simulated log lines from a 'bazel test --announce_rc' command
        options.parseOptionsFromOutput(optionLines);

        // test
        assertEquals("1", options.getOption("isatty"));
        assertEquals("1", options.getContextualOption("common", "isatty"));
        assertNull(options.getContextualOption("build", "isatty"));

        assertEquals("//tools/jdk:my-linux-jdk11", options.getOption("host_javabase")); // $SLASH_OK bazel path
        assertEquals("//tools/jdk:my-linux-jdk11", options.getContextualOption("build", "host_javabase")); // $SLASH_OK bazel path
        assertNull(options.getContextualOption("test", "host_javabase"));

        assertEquals("true", options.getOption("explicit_java_test_deps"));
        assertEquals("true", options.getContextualOption("test", "explicit_java_test_deps"));
        assertNull(options.getContextualOption("common", "explicit_java_test_deps"));

        // verify that --stamp gets interpreted as --stamp=true
        assertEquals("true", options.getOption("stamp"));
        assertEquals("true", options.getContextualOption("build", "stamp"));
        assertNull(options.getContextualOption("test", "stamp"));

        // defaults are not implemented yet
        //assertEquals("blue", options.getOptionWithDefault("nonexistentoption"));
        //assertEquals("red", options.getContextualOptionWithDefault("test", "nonexistentoption"));
    }

}
