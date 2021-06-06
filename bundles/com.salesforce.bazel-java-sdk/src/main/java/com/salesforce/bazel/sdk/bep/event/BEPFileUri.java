package com.salesforce.bazel.sdk.bep.event;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

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
            use.printStackTrace();
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
            ioe.printStackTrace();
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
            ioe.printStackTrace();
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
