/**
 * Copyright (c) 2021, Salesforce.com, Inc. All rights reserved.
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
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.salesforce.bazel.sdk.lang.jvm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.project.BazelProjectTargets;

/**
 * Helper utilities for computing the real classpath for a JVM test runner (e.g. JUnit).
 * <p>
 * Bazel creates a 'param' file for each java_test that contains the list of jar file paths for the classpath for
 * invoking that test via the java executable.
 */
public class BazelJvmTestClasspathHelper {
    private static final LogHelper LOG = LogHelper.log(BazelJvmTestClasspathHelper.class);

    static final String BAZEL_DEPLOY_PARAMS_SUFFIX = "_deploy.jar-0.params";
    static final String BAZEL_SRC_DEPLOY_PARAMS_SUFFIX = "_deploy-src.jar-0.params";

    // Cache for test classpath computations. In some envs, this can result in huge performance benefits.
    private static Map<Long, ParamFileResult> cachedResults = new HashMap<>();

    // The TTL and LastFlush are public so that the tool can decide how much time is appropriate for the cache.
    // You can lower/raise the TTL, or force a flush by setting cacheLastFlushMS to 0
    // To be safe, you would want to flush this cache whenever a BUILD file is updated.
    // But generally, the user will not immediately run tests right after a build file update because a build would
    // first need to happen.
    public static long cacheLastFlushMS = 0L;
    public static long cacheTTLMS = 60000L;

    /**
     * The jar suffix to be used to find the params file.
     */
    public static String getParamsJarSuffix(boolean isSource) {
        String suffix = BAZEL_DEPLOY_PARAMS_SUFFIX;
        if (isSource) {
            suffix = BAZEL_SRC_DEPLOY_PARAMS_SUFFIX;
        }
        return suffix;
    }

    /**
     * Finds all param files that match the input parameters. This may use Bazel query, depending on the inputs.
     * <p>
     * If a testClassName is passed, it will often speed up the operation as the param file for that test class can
     * often be found on the file system.
     */
    public static ParamFileResult findParamFilesForTests(BazelWorkspace bazelWorkspace, boolean isSource,
            String testClassName, BazelProjectTargets targets) {
        ParamFileResult result = null;

        // we use a cache because some IDEs may issue the same query multiple times in a short period of time
        // for Eclipse, for example, this cache *dramatically* speeds up test execution
        Long cacheKey = generateCacheKey(isSource, testClassName, targets);
        long currentTimeMS = System.currentTimeMillis();
        if ((currentTimeMS - cacheLastFlushMS) > cacheTTLMS) {
            cacheLastFlushMS = currentTimeMS;
            cachedResults = new HashMap<>();
        } else {
            result = cachedResults.get(cacheKey);
        }

        if (result == null) {
            if ((testClassName == null) || testClassName.equals("")) {
                result = findParamFilesForTestTargets(bazelWorkspace, isSource, targets);
            } else {
                result = findParamFilesForTestClassname(bazelWorkspace, isSource, targets, testClassName);
            }
            cachedResults.put(cacheKey, result);
        }

        return result;
    }

    private static Long generateCacheKey(boolean isSource, String testClassName, BazelProjectTargets targets) {
        long key = isSource ? 7 : 13;
        if (testClassName != null) {
            key = testClassName.hashCode() * key;
        }

        // configuredTargets is a set, so we have to be careful in our computation not to assume the order
        for (String target : targets.getConfiguredTargets()) {
            key = key + target.hashCode();
        }
        return key;
    }

    public static class ParamFileResult {
        public Set<File> paramFiles = new HashSet<>();
        public Set<String> unrunnableLabels = new HashSet<>();
    }

    /**
     * Looks up ALL param files for each passed target. If the target is a wildcard, this could return a large number of
     * param files.
     * <p>
     * Internally, this method uses Bazel query, which is somewhat expensive.
     */
    public static ParamFileResult findParamFilesForTestTargets(BazelWorkspace bazelWorkspace, boolean isSource,
            BazelProjectTargets targets) {
        ParamFileResult result = new ParamFileResult();

        File bazelBinDir = bazelWorkspace.getBazelBinDirectory();
        String suffix = BazelJvmTestClasspathHelper.getParamsJarSuffix(isSource);

        for (String target : targets.getConfiguredTargets()) {
            String query = "tests(" + target + ")";
            List<String> labels = bazelWorkspace.getTargetsForBazelQuery(query);

            for (String label : labels) {
                String testRuleName = label.substring(label.lastIndexOf(":") + 1);
                String targetPath = target.split(":")[0];
                String paramFilename = testRuleName + suffix;
                File pFile = new File(new File(bazelBinDir, targetPath), paramFilename);
                if (pFile.exists()) {
                    result.paramFiles.add(pFile);
                } else {
                    result.unrunnableLabels.add(label);
                }
            }
        }
        return result;
    }

    /**
     * Looks up the param files associated with the passed testclass. If this command needs to resort to a bazel query
     * to find it, the scope of the bazel query commands will be the passed targets.
     */
    public static ParamFileResult findParamFilesForTestClassname(BazelWorkspace bazelWorkspace, boolean isSource,
            BazelProjectTargets targets, String testClassName) {
        ParamFileResult result = new ParamFileResult();

        String suffix = BazelJvmTestClasspathHelper.getParamsJarSuffix(isSource);

        for (String target : targets.getConfiguredTargets()) {
            Set<File> testParamFiles = BazelJvmTestClasspathHelper.findParamsFileForTestClassnameAndTarget(bazelWorkspace, target,
                testClassName, suffix);
            result.paramFiles.addAll(testParamFiles);
        }
        return result;
    }

    /**
     * Bazel maintains a params file for each java_test rule. This method finds it.
     * <p>
     * TODO This needs to be re-implemented - the path is hardcoded. It should be path of the test rule Remove hardcoded
     * src/test/java
     *
     * @param project
     * @param paramsName
     * @param target
     * @return
     */
    public static Set<File> findParamsFileForTestClassnameAndTarget(BazelWorkspace bazelWorkspace, String target, String className,
            String suffix) {
        Set<File> paramFiles = new HashSet<>();

        String targetPath = target.split(":")[0];

        // testJar for bazel's iterative test rules
        File bazelBinDir = bazelWorkspace.getBazelBinDirectory();
        String paramsName = className.replace('.', File.separatorChar) + suffix;

        File targetBinPath = new File(bazelBinDir, targetPath);
        File targetBinTestPath = new File(targetBinPath, FSPathHelper.osSeps("src/test/java")); // $SLASH_OK

        File paramFile = new File(targetBinTestPath, paramsName);
        if (paramFile.exists()) {
            paramFiles.add(paramFile);
        } else {
            // find the testJar for single test rule using Bazel Query
            String query = "attr(test_class, " + className + "$, " + target + ")";
            List<String> labels = bazelWorkspace.getTargetsForBazelQuery(query);

            for (String label : labels) {
                String targetName = label.substring(label.lastIndexOf(":") + 1);
                String paramFilename = targetName + suffix;
                paramFile = new File(targetBinPath, paramFilename);

                if (paramFile.exists()) {
                    paramFiles.add(paramFile);
                } else {
                    LOG.error(
                        "Test params file does not exist for: \ntarget [{}] \nclassname [{}] \nsuffix [{}]"
                                + "\nquery [{}] \nlabel [{}] \nfilename [{}] \npath [{}]",
                                target, className, suffix, query, label, paramFilename, paramFile.getAbsolutePath());
                }
            }
        }
        return paramFiles;
    }

    // PARAMS FILE PARSING

    /*
      EXAMPLE param file contents (redacted, only shows what we are looking for)

      --output
      bazel-out/darwin-fastbuild/bin/projects/libs/foo/src/test/java/com/salesforce/bar/MyServiceTest_deploy.jar
      --sources
      bazel-out/darwin-fastbuild/bin/projects/libs/foo/src/test/java/com/salesforce/bar/MyServiceTest.jar,//projects/libs/foo:src/test/java/com/salesforce/bar/MyServiceTest
      bazel-out/darwin-fastbuild/bin/tools/junit5/libbazeljunit5.jar,//tools/junit5:bazeljunit5

     */

    /**
     * Parse the output/source jars from the given params file.
     */
    public static List<String> getSourceAndOutputJarsFromParamsFile(File paramsFile) throws IOException {
        if (!paramsFile.exists()) {
            return null;
        }
        try (Scanner scanner = new Scanner(paramsFile)) {
            return getPathsToJars(scanner);
        }
    }

    /**
     * Parses the param file, looking for source and output entries. These entries point to the materialized files for
     * the classpath for the test.
     */
    public static List<String> getPathsToJars(Scanner scanner) {
        List<String> result = new ArrayList<>();
        boolean addToResult = false;
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (addToResult && !line.startsWith("--")) {
                String jar = line.split(",")[0];
                if (jar.endsWith(".jar")) {
                    result.add(jar);
                }
            } else {
                addToResult = false;
            }
            if (line.startsWith("--output")) {
                addToResult = true;
                continue;
            }
            if (line.startsWith("--sources")) {
                addToResult = true;
                continue;
            }
        }
        return result;
    }

}
