/**
 * Copyright (c) 2020, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.sdk.index.jvm.jar;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.index.jvm.JvmCodeIndex;
import com.salesforce.bazel.sdk.index.model.ClassIdentifier;
import com.salesforce.bazel.sdk.index.model.CodeLocationDescriptor;
import com.salesforce.bazel.sdk.lang.jvm.external.BazelExternalJarRuleManager;
import com.salesforce.bazel.sdk.lang.jvm.external.BazelExternalJarRuleType;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;

/**
 * Crawler that descends into nested directories of jar files and adds found files to the index.
 * <p>
 * There is a one to many relationship between JvmCodeIndex and JavaJarCrawler. Many crawlers will
 * be used to build a single JvmCodeIndex. See JvmCodeIndexer to see how that works.
 */
public class JavaJarCrawler {
    private static Logger LOG = LoggerFactory.getLogger(JavaJarCrawler.class);

    private BazelWorkspace bazelWorkspace;
    private final JvmCodeIndex index;
    private final JarIdentiferResolver resolver;
    private BazelExternalJarRuleManager externalJarRuleManager;

    public JavaJarCrawler(JvmCodeIndex index, JarIdentiferResolver resolver) {
        this.index = index;
        this.resolver = resolver;
    }

    public JavaJarCrawler(BazelWorkspace bazelWorkspace, JvmCodeIndex index, JarIdentiferResolver resolver,
            BazelExternalJarRuleManager externalJarRuleManager) {
        this.bazelWorkspace = bazelWorkspace;
        this.index = index;
        this.resolver = resolver;
        this.externalJarRuleManager = externalJarRuleManager;
    }

    /**
     * Crawls the passed file system path, descending directories looking for jar files.
     * Entries are added to the JvmCodeIndex as they are found.
     */
    public void index(File rootCrawlDirectory) {

        if (!rootCrawlDirectory.exists()) {
            LOG.error("JavaJarCrawler was passed a directory location that does not exist, it has been ignored. ", rootCrawlDirectory.getAbsolutePath());
            return;
        }
        if (!rootCrawlDirectory.isDirectory()) {
            LOG.error("JavaJarCrawler was passed a location that is not a directory, it has been ignored. ", rootCrawlDirectory.getAbsolutePath());
            return;
        }

        // To prevent concurrency bugs, after the indexing has begun we don't want the index configuration to change
        // The indexer should have already done this, but just in case...
        index.getOptions().setLock();

        // gavRoot is a tricky concept, see comments below; it starts off unknown
        File gavRoot = null;

        indexRecur(gavRoot, rootCrawlDirectory);
    }

    /**
     * Looks in the passed directory for jar files and processes those found. Recursively descends
     * into any child directories.
     *
     * @param gavRoot see code comments about what the gavRoot is, it is complicated
     * @param currentDirectory
     */
    protected void indexRecur(File gavRoot, File currentDirectory) {
        File[] children = currentDirectory.listFiles();
        if (children == null) {
            return;
        }

        // some file system layouts put gav information in the path, e.g.
        // ~/.m2/repository/com/acme/blue/1.0.0/blue.jar
        // we want to track the start of the gav info in the path if possible so we can decode it
        if (gavRoot == null) {
            File[] gavRootIndicators = currentDirectory.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    // TODO sketchy logic here, we assume at least one downloaded jar comes from a common domain
                    return name.equals("com") || name.equals("org") || name.equals("net");
                }
            });
            if (gavRootIndicators.length > 0) {
                gavRoot = currentDirectory;
            }
        }

        for (File child : children) {
            ZipFile zipFile = null;
            try {
                if (child.isDirectory()) {
                    if (doSkipDirectory(child.getPath())) {
                        continue;
                    }
                    indexRecur(gavRoot, child);
                } else if (child.canRead()) {
                    if (child.getName().endsWith(".jar")) {
                        zipFile = new ZipFile(child);

                        // TODO run this method async in a different thread
                        foundJar(gavRoot, child, zipFile);
                    }
                }
            } catch (Exception anyE) {
                LOG.error("Reading jar file lead to unexpected error for path [{}]", anyE, child.getPath());
            } finally {
                if (zipFile != null) {
                    try {
                        zipFile.close();
                    } catch (Exception ioE) {}
                }
            }
        }
    }

    static boolean doSkipDirectory(String directoryName) {
        if (directoryName.contains(".runfiles")) {
            // bazel test sandbox, stay out of here as the jars in here are for running tests
            return true;
        }
        return false;
    }

    /**
     * For a given jar file found on disk, check if it is an interesting jar file and add it to the index
     * if so.
     * <p>
     * Configure the CodeIndexOption class in the CodeIndex to alter the behavior of this operation.
     * <p>
     * TODO someday we will run this method concurrent to other threads, be careful of shared state
     */
    protected void foundJar(File gavRootDir, File jarFile, ZipFile zipFile) {
        LOG.debug("found jar: [{}]", jarFile.getName());

        // precisely identify the jar file
        JarIdentifier jarId = resolver.resolveJarIdentifier(gavRootDir, jarFile, zipFile);
        if (jarId == null) {
            // this jar is not part of the typical dependencies (e.g. it is a jar used in the build toolchain); ignore
            return;
        }
        String absoluteFilepath = jarFile.getAbsolutePath();

        // use shorter names
        boolean doComputeTypes = index.getOptions().doComputeTypeDictionary();
        boolean doUseFileAge = index.getJvmOptions().doComputeJarAgeUsingInternalFiles();
        boolean doUseRemoteAge = index.getJvmOptions().doComputeJarAgeUsingRemoteMavenRepo();

        // we want to determine the Bazel label based on filename (not always possible)
        // for example: @maven//:org_slf4j_slf4j_api
        // this logic is dependent on the external jar rule type that we are using
        // are we using maven_install, jvm_importexport, etc
        String bazelLabel = null;
        // TODO why would we run this outside of a workspace? is this code just being paranoid?
        if (bazelWorkspace != null) {
            BazelExternalJarRuleType externalJarRuleType =
                    externalJarRuleManager.findOwningRuleType(bazelWorkspace, absoluteFilepath);
            if (externalJarRuleType != null) {
                bazelLabel = externalJarRuleType.deriveBazelLabel(bazelWorkspace, absoluteFilepath, jarId);
            }
        }

        // we have enough information to add an index entry, build the descriptor
        CodeLocationDescriptor jarLocationDescriptor = new CodeLocationDescriptor(jarFile, jarId, bazelLabel, jarId.version);

        // add to our index using artifact name (eg. junit, hamcrest-core, slf4j-api)
        index.addArtifactLocation(jarId.artifact, jarLocationDescriptor);
        // add to our index using file name (eg. junit-4.12.jar)
        index.addFileLocation(jarFile.getName(), jarLocationDescriptor);

        // if we don't want an index of each class found in a jar, and we aren't computing internal file ages
        // we can bail here and save a lot of work
        if (!doComputeTypes && !doUseFileAge) {
            return;
        }

        // BEGIN GOING INSIDE THE JAR FILE

        // attempt to load the zipentries; if this is not actually a zip file (or jar/tar) this operation
        // will fail so we guard against that
        Enumeration<? extends ZipEntry> entries = null;
        try {
            entries = zipFile.entries();
        } catch (Exception anyE) {
            LOG.error("Failure opening file [{}] as a zip/jar. Corrupt file?", anyE, jarFile.getPath());
            return;
        }

        // iterate through the contents for the jar file, for as long as the keepGoing flag is still set
        ZipEntriesProcessingState processEntriesState = new ZipEntriesProcessingState();
        while (processEntriesState.keepGoing && entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();

            // name of the zip file entry, which will contain the path info: com/salesforce/foo/Bar.class
            String fqClassname = entry.getName();
            // gets the time when the entry was written (as according to the builder of the jar)
            long writtenTimeMillis = entry.getTime();

            processJarFileZipEntry(processEntriesState, fqClassname, writtenTimeMillis, jarLocationDescriptor);
        }

        // END GOING INSIDE THE JAR FILE

        if (!processEntriesState.foundValidAge && doUseRemoteAge) {
            // TODO also provide an option to call a remote system (e.g. Maven Central). It will be slow.
        }
    }

    /**
     * Simple holder class of ongoing state as we index through the internal entries in a jar file.
     */
    protected class ZipEntriesProcessingState {
        // if we are building the type dictionary, we will keep going through the whole jar
        // but for some index configurations, we don't iterate through all files in the jar
        // the loop will set this flag to false to stop iterating through zip file entries in the jar
        boolean keepGoing = true;

        // convenience booleans
        boolean doComputeTypes =  index.getJvmOptions().doComputeTypeDictionary();
        boolean doComputeAgeViaFileTimes = index.getJvmOptions().doComputeJarAgeUsingInternalFiles();

        // if we are computing the age of the jar, we only try a limited number of times
        int computeFileAgeAttemptsRemaining = doComputeAgeViaFileTimes ?
                index.getJvmOptions().getTriesForComputeJarAgeUsingInternalFiles() : 0;

        // if we found a valid age using the entry times, we set this to true so more expensive techniques
        // (e.g. calling to an external maven repo) are avoided
        boolean foundValidAge = false;
    }

    /**
     * For a given zipfile entry, process it for information that the indexer is asking for
     */
    void processJarFileZipEntry(ZipEntriesProcessingState processEntriesState, String filepath, long writtenTimeMillis,
            CodeLocationDescriptor jarLocationDescriptor) {

        // we only care about Java class files
        if (!hasClassfileName(filepath)) {
            return;
        }

        // convert path / into . to form the legal package name, and trim the .class off the end
        String fqClassname = convertClassfileNameToClassname(filepath);
        if (fqClassname == null) {
            return;
        }
        LOG.debug("Indexer found classname: {} in jar {}", fqClassname, jarLocationDescriptor.id);

        ClassIdentifier classId = new ClassIdentifier(fqClassname);
        jarLocationDescriptor.addClass(classId);
        index.addTypeLocation(classId.classname, jarLocationDescriptor);

        if (processEntriesState.computeFileAgeAttemptsRemaining > 0) {
            long currentTimeMillis = System.currentTimeMillis();
            long earliestRealTimeMillis = index.getJvmOptions().getEarliestTimestampForComputeJarAgeUsingInternalFiles();
            processEntriesState.foundValidAge = jarLocationDescriptor.computeAge(writtenTimeMillis, currentTimeMillis, earliestRealTimeMillis);

            if (processEntriesState.foundValidAge && !processEntriesState.doComputeTypes) {
                // we can stop now, we have our age, and we aren't building the type dictionary
                processEntriesState.keepGoing = false;
            }
            processEntriesState.computeFileAgeAttemptsRemaining = processEntriesState.foundValidAge ? 0 : processEntriesState.computeFileAgeAttemptsRemaining-1;
        }
    }

    /**
     * @param filepath name of the zip file entry, which will contain the path info: com/salesforce/foo/Bar.class
     * @return true if the filename indicates a Java classfile
     */
    static boolean hasClassfileName(String filepath) {
        if (filepath == null) {
            // bug
            return false;
        } else if (!filepath.endsWith(".class")) {
            // non-class file, don't care
            return false;
        } else if (filepath.endsWith("package-info.class")) {
            // non-class file, don't care
            return false;
        } else if (filepath.contains("$")) {
            // inner class like Foo$Bar.class, don't care
            return false;
        }
        return true;
    }

    /**
     * @param filepath name of the zip file entry, which will contain the path info: com/salesforce/foo/Bar.class
     * @return the fully qualified classname: com.salesforce.foo.Bar
     */
    static String convertClassfileNameToClassname(String filepath) {
        if (filepath == null || filepath.isEmpty() || filepath.length() < 7) {
            // since indexing is a bulk operation, we want to avoid throwing exceptions just in case someone
            // forgets to catch; we don't want one bad file to ruin the whole thing
            return null;
        }

        // convert path / into . to form the legal package name,
        filepath = filepath.replace(FSPathHelper.JAR_SLASH, ".");

        // trim the .class off the end
        filepath = filepath.substring(0, filepath.length() - 6);

        return filepath;
    }
}
