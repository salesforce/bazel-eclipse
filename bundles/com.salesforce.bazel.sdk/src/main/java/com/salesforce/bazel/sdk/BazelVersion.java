/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.salesforce.bazel.sdk;

import static java.lang.String.format;

import java.util.regex.Pattern;

import com.google.common.collect.ComparisonChain;

/**
 * Bazel Version information
 */
public record BazelVersion(int major, int minor, int bugfix) {

    static final BazelVersion DEVELOPMENT = new BazelVersion(999, 999, 999);
    private static final Pattern PATTERN = Pattern.compile("([[0-9]\\.]+)");

    public BazelVersion(int major, int minor, int bugfix) {
        if (major < 0) {
            throw new IllegalArgumentException("major must be >= 0");
        }
        if (minor < 0) {
            throw new IllegalArgumentException("minor must be >= 0");
        }
        if (bugfix < 0) {
            throw new IllegalArgumentException("bugfix must be >= 0");
        }
        this.major = major;
        this.minor = minor;
        this.bugfix = bugfix;
    }

    public static BazelVersion parseVersion(String string) {
        // treat all unknown / development versions as the very latest version
        if (string == null) {
            return DEVELOPMENT;
        }
        var matcher = PATTERN.matcher(string);
        if (!matcher.find()) {
            return DEVELOPMENT;
        }
        try {
            var version = parseVersion(matcher.group(1).split("\\."));
            if (version == null) {
                return DEVELOPMENT;
            }
            return version;
        } catch (Exception e) {
            return DEVELOPMENT;
        }
    }

    private static BazelVersion parseVersion(String[] numbers) {
        if ((numbers.length < 1) || (numbers[0] == null) || numbers[0].isBlank()) {
            return null;
        }
        var major = Integer.parseInt(numbers[0]);
        if (major < 0) {
            return null;
        }
        var minor = numbers.length > 1 ? Integer.parseInt(numbers[1]) : 0;
        var bugfix = numbers.length > 2 ? Integer.parseInt(numbers[2]) : 0;
        return new BazelVersion(major, minor, bugfix);
    }

    public boolean isAtLeast(BazelVersion version) {
        return isAtLeast(version.major, version.minor, version.bugfix);
    }

    public boolean isAtLeast(int major, int minor, int bugfix) {
        return ComparisonChain.start().compare(this.major, major).compare(this.minor, minor)
                .compare(this.bugfix, bugfix).result() >= 0;
    }

    @Override
    public String toString() {
        return format("%s.%s.%s", major, minor, bugfix);
    }
}
