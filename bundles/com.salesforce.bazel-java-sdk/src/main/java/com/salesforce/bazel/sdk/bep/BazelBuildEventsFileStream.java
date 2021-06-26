package com.salesforce.bazel.sdk.bep;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.salesforce.bazel.sdk.bep.event.BEPEvent;
import com.salesforce.bazel.sdk.bep.file.BEPMonitoredFile;
import com.salesforce.bazel.sdk.bep.file.BEPFileContents;
import com.salesforce.bazel.sdk.bep.file.BEPFileParser;
import com.salesforce.bazel.sdk.logging.LogHelper;

/**
 * /** Bazel build event protocol stream (BEP) for a Bazel workspace. This implementation reads a file, publishes the
 * events in the file to the subscriber(s), and ends the stream.
 * <p>
 * Because this stream integrates with your builds with Bazel using BEP, this stream will work with command line builds
 * but also with IDE builds.
 * <p>
 * After creation, you must start the stream by calling activateStream().
 * <p>
 * <b>NOTE:</b> This feature requires you to provide BEP JSON file(s). These can be generated with a configuration
 * change to your Bazel workspace: you must add the following lines to your .bazelrc file to enable BEP:<br/>
 * build --build_event_json_file bep_build.json<br/>
 * test --build_event_json_file bep_test.json<br/>
 * <p>
 * In the above example, you must call addFileToMonitor() for each configured json file (bep_build.json and
 * bep_test.json).
 */
public class BazelBuildEventsFileStream extends BazelBuildEventStream {
    private static final LogHelper LOG = LogHelper.log(BazelBuildEventsFileStream.class);

    protected final List<BEPMonitoredFile> monitoredFiles = new ArrayList<>();

    public BazelBuildEventsFileStream() {}

    // PUBLIC API

    /**
     * Bazel supports output of BEP events to one or more files. For each BEP file you have configured for your
     * workspace, you can add it here. The file does not need to exist. This helps in cases when the build has not yet
     * run and the file has not been written yet. But this does mean that passing an incorrect File object will cause
     * your events to be missed when you activate your stream.
     */
    public void addFileToMonitor(File bepFile) {
        addFileToMonitor_Internal(bepFile);
    }

    @Override
    public void activateStream() {
        super.activateStream();

        for (BEPMonitoredFile bepFile : monitoredFiles) {
            processFile(bepFile);
        }
    }

    // INTERNAL

    protected BEPMonitoredFile addFileToMonitor_Internal(File bepFile) {
        BEPMonitoredFile monitoredFile = new BEPMonitoredFile();
        monitoredFile.file = bepFile;

        // set last mod to zero so we will parse the file on the first run
        monitoredFile.fileLastModifiedMS = 0L;

        // create the parser object
        monitoredFile.bepFile = new BEPFileParser(bepFile);

        monitoredFiles.add(monitoredFile);

        return monitoredFile;
    }

    protected void processFile(BEPMonitoredFile monitoredFile) {
        if (!monitoredFile.file.exists()) {
            // the monitored file does not exist yet (user needs to run a build?)
            LOG.info("File [{}] does not exist yet. Run a build?", monitoredFile.file.getAbsolutePath());
            return;
        }

        // parse the file, but use the previous results (if this file has been scanned before) as a cache, 
        // to avoid reparsing lines we have already parsed
        BEPFileContents previousContent = monitoredFile.previousResults;
        BEPFileParser bepFile = monitoredFile.bepFile;
        BEPFileContents newContent = bepFile.readEvents("BazelBuildEventsFileStream", previousContent);
        monitoredFile.previousResults = newContent;

        // iterate through the newly found lines and send them to the subscribers
        for (BEPEvent event : newContent.events) {
            if (event.isProcessed()) {
                continue;
            }
            event.processed();
            publishEventToSubscribers(event);
        }
    }
}
