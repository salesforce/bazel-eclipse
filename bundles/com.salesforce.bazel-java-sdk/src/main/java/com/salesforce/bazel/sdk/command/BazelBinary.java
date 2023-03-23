package com.salesforce.bazel.sdk.command;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;

import com.salesforce.bazel.sdk.BazelVersion;

/**
 * Record of a Bazel binary to use.
 */
public record BazelBinary(Path executable, BazelVersion bazelVersion) {

    public BazelBinary(Path executable, BazelVersion bazelVersion) {
        this.executable = requireNonNull(executable, "executable must not be null");
        this.bazelVersion = requireNonNull(bazelVersion, "bazelVersion must not be null");
    }

}
