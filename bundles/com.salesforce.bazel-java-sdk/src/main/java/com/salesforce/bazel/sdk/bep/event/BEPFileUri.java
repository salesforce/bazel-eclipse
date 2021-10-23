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
package com.salesforce.bazel.sdk.bep.event;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import com.salesforce.bazel.sdk.logging.LogHelper;

/**
 * Holder for a uri to a file, and associated helper functions.
 * <p>
 * A repeating pattern in a number of BEP event payloads it the use of a URI to represent a path to a file.
 * <p>
 * Example:<br/>
 * "uri":
 * "file:///private/var/tmp/_bazel_mbenioff/xyz/execroot/myrepo/bazel-out/darwin-fastbuild/testlogs/foo/foo-test/test.log"
 * <p>
 * This class is meant to model the URI property, and provide some common helper functions for working with them.
 */
public class BEPFileUri {
    private static final LogHelper LOG = LogHelper.log(BEPFileUri.class);

    private final String id;
    private final String uriStr;
    private URI uri;
    private final File file;
    private final List<String> prefixes;

    /**
     * Constructor for BEPFileUri
     *
     * @param id
     *            an identifier that will be helpful in logs/debug to know what this uri is pointing at. The caller
     *            should determine the best identifier for this purpose.
     * @param uriString
     *            the file:// String that is the URI
     */
    public BEPFileUri(String id, String uriString) {
        this(id, uriString, null);
    }

    /**
     * Constructor for BEPFileUri
     *
     * @param id
     *            an identifier that will be helpful in logs/debug to know what this uri is pointing at. The caller
     *            should determine the best identifier for this purpose.
     * @param uriString
     *            the file:// String that is the URI
     * @param prefixes
     *            in some cases BEP includes a set of prefixes along with a uri
     */
    public BEPFileUri(String id, String uriString, List<String> prefixes) {
        this.id = id;
        uriStr = uriString;
        this.prefixes = prefixes;

        if (!uriString.startsWith(("file://"))) {
            throw new IllegalArgumentException(
                    "Expected a file:// uri for property " + id + ", instead got " + uriString);
        }

        try {
            uri = new URI(uriString);
        } catch (URISyntaxException use) {
            throw new IllegalArgumentException(use);
        }

        file = new File(uri);
    }

    // GETTERS

    /**
     * Provided by the caller to the constructor. Only used for logging/debugging. Intended to help identify the purpose
     * of the URI.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the URI as a String (e.g. file:///tmp/abc)
     */
    public String getUriString() {
        return uriStr;
    }

    /**
     * Returns the URI
     */
    public URI getUri() {
        return uri;
    }

    /**
     * Returns the File identified by the URI. This file may or may not exist - caller should verify the File exists
     * before using it.
     */
    public File getFile() {
        return file;
    }

    /**
     * A sequence of prefixes to apply to the file name to construct a full path. This feature is optional (only some
     * BEP events provide it) and may be null. If present, there will usually be 3 entries:
     * <ol>
     * <li>A root output directory, eg "bazel-out"</li>
     * <li>A configuration mnemonic, eg "k8-fastbuild"</li>
     * <li>An output category, eg "genfiles"</li>
     * </ol>
     */
    public List<String> getPrefixes() {
        return prefixes;
    }

    // FILE OPS

    /**
     * Loads all the lines of the File identified by the URI into a List. If the File does not exist, the List will be
     * empty.
     */
    public List<String> loadLines() {
        return loadLines(null, null, false);
    }

    /**
     * Loads all the lines of the File identified by the URI into a List. If the File does not exist, the List will be
     * empty.
     * <p>
     * The method accepts a beginRegex and an endRegex. The returned lines will redact any lines prior to the first line
     * matching the beginRegex. It will redact any lines after the line matching the endRegex. If beginRegex is null, no
     * lines are redacted from the beginning. If endRegex is null, no lines are redacted at the end.
     */
    public List<String> loadLines(String beginRegex, String endRegex, boolean ignoreBlankLines) {
        List<String> lines = new ArrayList<>();
        if (!file.exists()) {
            return lines;
        }

        // startRecording is true when lines should recorded
        boolean startRecording = beginRegex == null;

        try (BufferedReader b = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = b.readLine()) != null) {
                if (!startRecording && matchRegex(line, beginRegex)) {
                    startRecording = true;
                }
                if (startRecording) {
                    boolean ignore = ignoreBlankLines && line.trim().isEmpty();
                    if (!ignore) {
                        lines.add(line);
                    }
                    if (matchRegex(line, endRegex)) {
                        break;
                    }
                }
            }
        } catch (Exception ioe) {
            LOG.error("error loading lines from file [{}]", ioe, file.getAbsolutePath());
        }

        return lines;
    }

    /**
     * Loads all the lines of the File identified by the URI into a String. If the File does not exist, the String will
     * be empty.
     */
    public String loadString() {
        if (!file.exists()) {
            return "";
        }
        StringBuffer text = new StringBuffer();

        try (BufferedReader b = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = b.readLine()) != null) {
                text.append(line);
                text.append("\n");
            }
        } catch (Exception ioe) {
            LOG.error("error loading lines from file [{}]", ioe, file.getAbsolutePath());
        }

        return text.toString();
    }

    // TOSTRING

    @Override
    public String toString() {
        return uriStr;
    }

    // INTERNALS

    private boolean matchRegex(String line, String regex) {
        if (regex == null) {
            return true;
        }
        return line.matches(regex);
    }
}
