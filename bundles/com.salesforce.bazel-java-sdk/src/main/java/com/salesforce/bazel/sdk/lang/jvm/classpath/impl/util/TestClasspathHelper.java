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
package com.salesforce.bazel.sdk.lang.jvm.classpath.impl.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.project.BazelProjectOld;
import com.salesforce.bazel.sdk.project.BazelProjectTargets;

/**
 * Helper utilities for computing the real classpath for a JVM test runner (e.g. JUnit).
 * <p>
 * Bazel creates a 'param' file for each java_test that contains the list of jar file paths for the classpath for
 * invoking that test via the java executable.
 */
public class TestClasspathHelper {
    private static Logger LOG = LoggerFactory.getLogger(TestClasspathHelper.class);

    static final String BAZEL_DEPLOY_PARAMS_SUFFIX = "_deploy.jar-0.params";
    static final String BAZEL_SRC_DEPLOY_PARAMS_SUFFIX = "_deploy-src.jar-0.params";

    // Cache for test classpath computations. In some envs, this can result in huge performance benefits.
    private Map<Long, ParamFileResult> cachedResults = new HashMap<>();

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
    public String getParamsJarSuffix(boolean isSource) {
        String suffix = BAZEL_DEPLOY_PARAMS_SUFFIX;
        if (isSource) {
            // TODO what is the use case for finding a test classpath for a src jar?
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
    public ParamFileResult findParamFilesForTests(BazelWorkspace bazelWorkspace, BazelProjectOld bazelProject,
            boolean isSource, String testClassName, BazelProjectTargets targets) {
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
                result = findParamFilesForTestTargets(bazelWorkspace, bazelProject, isSource, targets);
            } else {
                result = findParamFilesForTestClassname(bazelWorkspace, bazelProject, isSource, targets, testClassName);
            }
            cachedResults.put(cacheKey, result);
        }

        return result;
    }

    private Long generateCacheKey(boolean isSource, String testClassName, BazelProjectTargets targets) {
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

    /**
     * Convenience bundle of the test param files generated from a set of targets
     */
    public static class ParamFileResult {
        /**
         * The ordered list of param files obtained by querying on the targets
         */
        public List<File> paramFiles = new ArrayList<>();

        /**
         * Labels that did not resolve to a param file. Usually means the test class is not referenced by
         * a java_test target, but is a different kind of test that we don't know how to run.
         */
        public Set<String> unrunnableLabels = new HashSet<>();
    }

    /**
     * Looks up ALL param files for each passed target. If the target is a wildcard, this could return a large number of
     * param files.
     * <p>
     * Internally, this method uses Bazel query, which is somewhat expensive.
     */
    public ParamFileResult findParamFilesForTestTargets(BazelWorkspace bazelWorkspace, BazelProjectOld bazelProject,
            boolean isSource, BazelProjectTargets targets) {
        ParamFileResult result = new ParamFileResult();

        File bazelBinDir = bazelWorkspace.getBazelBinDirectory();
        String suffix = getParamsJarSuffix(isSource);

        for (String target : targets.getConfiguredTargets()) {
            String query = "tests(" + target + ")";
            List<String> labels = bazelWorkspace.getTargetsForBazelQuery(query);

            for (String label : labels) { // //projects/apple:src/test/java/com/foo/apple/AppleTest
                String testRuleName = label.substring(label.lastIndexOf(":") + 1); // src/test/java/com/foo/apple/AppleTest
                String targetPath = target.split(":")[0]; // //projects/apple
                String paramFilename = testRuleName + suffix; // src/test/java/com/foo/apple/AppleTest_deploy.jar-0.params
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
    public ParamFileResult findParamFilesForTestClassname(BazelWorkspace bazelWorkspace,
            BazelProjectOld bazelProject, boolean isSource, BazelProjectTargets targets, String testClassName) {
        ParamFileResult result = new ParamFileResult();

        String suffix = getParamsJarSuffix(isSource);

        for (String target : targets.getConfiguredTargets()) {
            Set<File> testParamFiles = findParamsFileForTestClassnameAndTarget(
                bazelWorkspace, bazelProject, target, testClassName, suffix);
            result.paramFiles.addAll(testParamFiles);
        }
        return result;
    }

    /**
     * Bazel maintains a params file for each java_test rule. It contains classpath info for the test. This method finds
     * the params file.
     */
    public Set<File> findParamsFileForTestClassnameAndTarget(BazelWorkspace bazelWorkspace,
            BazelProjectOld bazelProject, String target, String className, String suffix) {
        // TODO in what case will there be multiple test param files?
        Set<File> paramFiles = new HashSet<>();
        File paramFile = null;
        String targetPath = target.split(":")[0];
        File bazelBinDir = bazelWorkspace.getBazelBinDirectory();

        // check the expected locations for param files; this is the fastest way but only works
        // if the java_test rule instance is named the same as the package+class (which is common)
        //   java_test( name = "com.salesforce.foo.FooTest" ...
        String paramsName = className.replace('.', File.separatorChar) + suffix;
        List<String> testPaths = bazelProject.getProjectStructure().testSourceDirFSPaths;
        for (String relTestPath : testPaths) {
            File targetBinTestPath = new File(bazelBinDir, FSPathHelper.osSeps(relTestPath));
            paramFile = new File(targetBinTestPath, paramsName);
            if (paramFile.exists()) {
                paramFiles.add(paramFile);
                LOG.info("Found the test params file for {} the cheap way.", className);
                break;
            } else {
                paramFile = null;
            }
        }
        if (paramFiles.size() > 0) {
            // found useful param files
            return paramFiles;
        }

        // the cheap way failed, now find the target name for the test rule using more expensive Bazel Query
        // TODO we are using the param files because that is a cheap option, but once we start hitting bazel query and builds
        //  below, I think we should be using the aspects instead (which should be cached by this point)
        String query = "attr(test_class, " + className + "$, " + target + ")";
        List<String> labels = bazelWorkspace.getTargetsForBazelQuery(query);

        // we can now sanity check the request - does this test class even have java_test target?
        if (labels.size() == 0) {
            // no, this is a dangling test class without a java_test rule
            String message =
                    "The test class " + className + " does not have a java_test target defined in the BUILD file.";
            LOG.error(message);
            throw new IllegalStateException(message);
        }

        // TODO we can have multiple labels if the same test class is used multiple times in a BUILD file? perhaps with different resource files to test different cases? figure this out
        for (String label : labels) {
            String targetName = label.substring(label.lastIndexOf(":") + 1);
            String paramFilename = targetName + suffix;
            File targetBinPath = new File(bazelBinDir, targetPath);
            paramFile = new File(targetBinPath, paramFilename);

            if (paramFile.exists()) {
                paramFiles.add(paramFile);
                LOG.info("Found the test params file for {} the expensive way.", className);
            } else {
                // still haven't found the param file; this is because the output dir does not have a
                // {target}_deploy.jar-0.params file, which is because a build with * target hasnt been run
                // recently with this package now we have to do more expensive operations - run the deploy
                // build target for this test
                BazelWorkspaceCommandRunner commandRunner = bazelWorkspace.getBazelWorkspaceCommandRunner();
                Set<String> buildTargets = new HashSet<>();
                buildTargets.add(label + "_deploy.jar");
                try {
                    commandRunner.runBazelBuild(buildTargets, new ArrayList<>());
                    if (paramFile.exists()) {
                        paramFiles.add(paramFile);
                        LOG.info("Found the test params file for {} the extra expensive way.", className);
                    }
                } catch (Exception anyE) {
                    LOG.error("Could not build target {}, which is needed to compute the classpath for the test",
                        label + "_deploy.jar");
                }

            }
        }
        if (paramFiles.size() > 0) {
            // found useful param files
            return paramFiles;
        }

        // TODO we failed to get the classpath; we need to set the classpath to something, so we dont blow up so badly;
        //   we should just use the already computed classpath for the entire project
        LOG.error("The test class {} does not have a classpath data persisted in the output directories.", className);

        return paramFiles;
    }

    /**
     * Given the set of param files in the passed testParamFilesResult, parse each param file and extract a list
     * of jar files from the sources and output sections of each file. Then assemble a de-duplicated list of these
     * jar files. The path for each jar file comes from the param file and is known to be relative to the Bazel
     * exec root of the workspace.
     * <p>
     * @param testParamFiles the ordered list of discovered param files for the set of targets
     * @param includeDeployJars deploy jars contain the Bazel test runner and the full classpath of classes to run
     *              the test. For some environments, this isn't necessary because the environment externally specifies
     *              the classpath at launch, and provides the test runner (e.g. Eclipse RemoteTestRunner)
     * @return a List of paths to jar files; the list is ordered the same as the param files
     */
    public List<String> aggregateJarFilesFromParamFiles(List<File> testParamFiles,
            boolean includeDeployJars) {

        // Bazel is deterministic in the ordering of classpath elements, so use a list here not a Set
        // to allow us to better model the classpath order of Bazel
        List<String> allPaths = new ArrayList<>();

        for (File paramsFile : testParamFiles) {
            List<String> jarPaths = null;
            try {
                jarPaths = getClasspathJarsFromParamsFile(paramsFile);
            } catch (IOException ioe) {
                LOG.warn("Failed to parse test classpath file {}", paramsFile.getAbsolutePath());
            }
            if (jarPaths == null) {
                // error has already been logged, just try to soldier on
                continue;
            }

            for (String jarPath : jarPaths) {
                if (!includeDeployJars && jarPath.endsWith("_deploy.jar")) {
                    // deploy jars are bloated and redundant for some callers, exclude them if asked to
                    continue;
                }
                if (allPaths.contains(jarPath)) {
                    // we don't want dupes
                    continue;
                }

                // it is important to use a Set for allPaths, as the jarPaths will contain many dupes
                // across ParamFiles and we only want each one listed once
                allPaths.add(jarPath);
            }
        }
        return allPaths;
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
     * Parse the classpath jars from the given params file.
     */
    public List<String> getClasspathJarsFromParamsFile(File paramsFile) throws IOException {
        if (!paramsFile.exists()) {
            return null;
        }
        try (Scanner scanner = new Scanner(paramsFile)) {
            return getClasspathJarsFromParamsFile(scanner);
        }
    }

    /**
     * Parses the param file, looking for classpath entries. These entries point to the materialized files for the
     * classpath for the test.
     */
    public List<String> getClasspathJarsFromParamsFile(Scanner scanner) {
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
