/**
 * Copyright (c) 2019, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.bazel.eclipse.command.internal;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;
import com.salesforce.bazel.eclipse.abstractions.WorkProgressMonitor;
import com.salesforce.bazel.eclipse.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.eclipse.model.BazelBuildFile;

/**
 * Helper that knows how to run bazel query commands.
 */
public class BazelQueryHelper {

    /**
     * Underlying command invoker which takes built Command objects and executes them.
     */
    private final BazelCommandExecutor bazelCommandExecutor;
    
    private final Map<String, BazelBuildFile> buildFileCache = new HashMap<>();
    
    public BazelQueryHelper(BazelCommandExecutor bazelCommandExecutor) {
        this.bazelCommandExecutor = bazelCommandExecutor;
    }

    /**
     * Returns the list of targets found in the BUILD files for the given sub-directories. Uses Bazel Query to build the
     * list.
     *
     * @param progressMonitor
     *            can be null
     * @throws BazelCommandLineToolConfigurationException
     */
    @Deprecated
    public synchronized List<String> listBazelTargetsInBuildFiles(File bazelWorkspaceRootDirectory, WorkProgressMonitor progressMonitor,
            File... directories) throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        ImmutableList.Builder<String> argBuilder = ImmutableList.builder();
        argBuilder.add("query");
        for (File f : directories) {
            String directoryPath = f.toURI().relativize(bazelWorkspaceRootDirectory.toURI()).getPath();
            argBuilder.add(directoryPath+"/...");
        }
        return bazelCommandExecutor.runBazelAndGetOutputLines(bazelWorkspaceRootDirectory, progressMonitor, 
            argBuilder.build(), (t) -> t, BazelCommandExecutor.TIMEOUT_INFINITE);
    }
    
    /**
     * Returns the list of targets, with type data, found in a BUILD files for the given package. Uses Bazel Query to build the list.
     * @param bazelPackageName the label path that identifies the package where the BUILD file lives (//projects/libs/foo)
     */
    public synchronized BazelBuildFile queryBazelTargetsInBuildFile(File bazelWorkspaceRootDirectory, WorkProgressMonitor progressMonitor,
            String bazelPackageName) throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {

        if ("//".equals(bazelPackageName)) {
            // we don't support having buildable code at the root of the WORKSPACE
            return new BazelBuildFile("//...");
        }
        
        BazelBuildFile buildFile = buildFileCache.get(bazelPackageName);
        if (buildFile != null) {
            System.out.println("Retrieved list of targets for package ["+bazelPackageName+"] from cache.");
            return buildFile;
        }
        
        // bazel query 'kind(rule, [label]:*)' --output label_kind
        
        ImmutableList.Builder<String> argBuilder = ImmutableList.builder();
        argBuilder.add("query");
        argBuilder.add("kind(rule, "+bazelPackageName+":*)");
        argBuilder.add("--output");
        argBuilder.add("label_kind");
        List<String> resultLines = bazelCommandExecutor.runBazelAndGetOutputLines(bazelWorkspaceRootDirectory, progressMonitor, 
            argBuilder.build(), (t) -> t, BazelCommandExecutor.TIMEOUT_INFINITE);
        
        // Sample Output:  (format: rule_type 'rule' label)
        // java_binary rule //projects/libs/apple/apple-api:apple-main
        // java_test rule //projects/libs/apple/apple-api:apple-api-test2
        // java_library rule //projects/libs/apple/apple-api:apple-api

        buildFile = new BazelBuildFile(bazelPackageName);
        for (String resultLine : resultLines) {
            String[] tokens = resultLine.split(" ");
            if (tokens.length != 3) {
                continue;
            }
            String ruleType = tokens[0];
            String targetLabel = tokens[2];
            buildFile.addTarget(ruleType, targetLabel);
        }
        buildFileCache.put(bazelPackageName, buildFile);
        
        return buildFile;
    }
    
    public void flushCache(String bazelPackageName) {
        buildFileCache.remove(bazelPackageName);
    }
    

    
    /**
     * Gives a list of target completions for the given beginning string. The result is the list of possible completion
     * for a target pattern starting with string.
     * <p>
     * <b>WARNING:</b> this method was written for the original Bazel plugin for a search feature, but was not actually
     * used as far as we can tell. It may or may not work as advertised.
     *
     * @param userSearchString
     *            the partial target string entered by the user
     *
     * @throws BazelCommandLineToolConfigurationException
     */
    public List<String> getMatchingTargets(File bazelWorkspaceRootDirectory, String userSearchString, WorkProgressMonitor progressMonitor)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        if (userSearchString.equals("/") || userSearchString.isEmpty()) {
            return ImmutableList.of("//");
        } else if (userSearchString.contains(":")) {
            // complete targets using `bazel query`
            int idx = userSearchString.indexOf(':');
            final String packageName = userSearchString.substring(0, idx);
            final String targetPrefix = userSearchString.substring(idx + 1);
            List<String> args = ImmutableList.<String> builder().add("query", packageName + ":*").build();
            Function<String, String> selector = line -> {
                int i = line.indexOf(':');
                String s = line.substring(i + 1);
                return !s.isEmpty() && s.startsWith(targetPrefix) ? (packageName + ":" + s) : null;
            };

            List<String> outputLines = this.bazelCommandExecutor.runBazelAndGetOuputLines(ConsoleType.WORKSPACE,
                bazelWorkspaceRootDirectory, progressMonitor, args, selector, BazelCommandExecutor.TIMEOUT_INFINITE);

            ImmutableList.Builder<String> builder = ImmutableList.builder();
            builder.addAll(outputLines);

            if ("all".startsWith(targetPrefix)) {
                builder.add(packageName + ":all");
            }
            if ("*".startsWith(targetPrefix)) {
                builder.add(packageName + ":*");
            }
            return builder.build();
        } else {
            // complete packages
            int lastSlash = userSearchString.lastIndexOf('/');
            final String prefix = lastSlash > 0 ? userSearchString.substring(0, lastSlash + 1) : "";
            final String suffix = lastSlash > 0 ? userSearchString.substring(lastSlash + 1) : userSearchString;
            final String directory = (prefix.isEmpty() || prefix.equals("//")) ? ""
                    : prefix.substring(userSearchString.startsWith("//") ? 2 : 0, prefix.length() - 1);
            File file = directory.isEmpty() ? bazelWorkspaceRootDirectory : new File(bazelWorkspaceRootDirectory, directory);
            ImmutableList.Builder<String> builder = ImmutableList.builder();
            File[] files = file.listFiles((f) -> {
                // Only give directories whose name starts with suffix...
                return f.getName().startsWith(suffix) && f.isDirectory()
                // ...that does not start with '.'...
                        && !f.getName().startsWith(".")
                // ...and is not a Bazel convenience link
                        && (!file.equals(bazelWorkspaceRootDirectory) || !f.getName().startsWith("bazel-"));
            });
            if (files != null) {
                for (File d : files) {
                    builder.add(prefix + d.getName() + "/");
                    if (new File(d, "BUILD").exists() || new File(d, "BUILD.bazel").exists()) {
                        builder.add(prefix + d.getName() + ":");
                    }
                }
            }
            if ("...".startsWith(suffix)) {
                builder.add(prefix + "...");
            }
            return builder.build();
        }
    }

}
