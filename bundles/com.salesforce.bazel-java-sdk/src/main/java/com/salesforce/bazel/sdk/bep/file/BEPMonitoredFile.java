package com.salesforce.bazel.sdk.bep.file;

import java.io.File;

/**
 * A file that is monitored by a file stream.
 */
public class BEPMonitoredFile {
    public File file;
    public long fileLastModifiedMS = 0L;
    public BEPFileParser bepFile;
    public BEPFileContents previousResults;
}
