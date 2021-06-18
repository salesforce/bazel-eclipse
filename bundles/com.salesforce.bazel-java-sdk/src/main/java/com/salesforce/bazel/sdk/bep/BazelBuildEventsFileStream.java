package com.salesforce.bazel.sdk.bep;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.salesforce.bazel.sdk.bep.event.BEPEvent;
import com.salesforce.bazel.sdk.bep.file.BazelBuildEventsFileContents;
import com.salesforce.bazel.sdk.bep.file.BazelBuildEventsFileParser;
import com.salesforce.bazel.sdk.logging.LogHelper;

/**
 * Bazel build event protocol stream (BEP) for a Bazel workspace. A BEP stream allows you to monitor and react to build
 * events emitted from Bazel.
 * <p>
 * Because this stream integrates with your builds with Bazel using BEP, this stream will work with command line builds
 * but also with IDE builds.
 * <p>
 * After creation, you must start the stream by calling activateStream().
 * <p>
 * <b>NOTE:</b> This feature requires a configuration change to your Bazel workspace: you must add the following lines
 * to your .bazelrc file to enable BEP:<br/>
 * build --build_event_json_file bep_build.json<br/>
 * test --build_event_json_file bep_test.json<br/>
 * <p>
 * In the above example, you must call addFileToMonitor() for each configured json file (bep_build.json and
 * bep_test.json).
 */
public class BazelBuildEventsFileStream extends BazelBuildEventStream {
    private static final LogHelper LOG = LogHelper.log(BazelBuildEventsFileStream.class);

    private int filePollerIntervalSeconds = 5;
    private final List<MonitoredFile> monitoredFiles = new ArrayList<>();
    private FilePoller filePoller = null;

    public BazelBuildEventsFileStream() {}

    // PUBLIC API

    /**
     * Bazel supports output of BEP events to one or more files. For each BEP file you have configured for your
     * workspace, add it here. The file does not need to exist. This helps in cases when the build has not yet run and
     * the file has not been written yet. But this does mean that passing an incorrect File object will cause your
     * events to be missed.
     * <p>
     * You must decide if the stream should parse the state of the BEP file when this stream is created. This might be
     * correct behavior, but think about cases in which the user has not run the build in many days. The BEP file would
     * contain build events from many days ago. If it would be odd for your app to react to build events that are many
     * days old, set <i>parseOnStart</i> to false.
     */
    public void addFileToMonitor(File bepFile, boolean parseOnStart) {
        MonitoredFile monitoredFile = new MonitoredFile();
        monitoredFile.file = bepFile;

        // set last mod to zero so we will parse the file on the first run
        monitoredFile.fileLastModifiedMS = 0L;

        // but if caller does not want the initial state parsed, capture the current
        // last mod
        if (!parseOnStart && monitoredFile.file.exists()) {
            monitoredFile.fileLastModifiedMS = monitoredFile.file.lastModified();
        }

        // create the parser object
        monitoredFile.bepFile = new BazelBuildEventsFileParser(bepFile);

        monitoredFiles.add(monitoredFile);
    }

    /**
     * The stream will monitor the BEP json files for any changes, based on a polling interval.
     */
    public void setFilePollerIntervalSeconds(int seconds) {
        filePollerIntervalSeconds = seconds;
    }

    @Override
    public void activateStream() {
        if (filePoller == null) {
            filePoller = new FilePoller();
            // let's go
            new Thread(filePoller).start();
        }
        super.activateStream();
    }

    // INTERNAL

    /**
     * Internal thread for polling the BEP files on an interval.
     */
    private class FilePoller implements Runnable {

        @Override
        public void run() {

            while (true) {
                try {
                    Thread.sleep(filePollerIntervalSeconds * 1000);
                } catch (InterruptedException ie) {}

                if (paused) {
                    // if still paused, just restart the loop
                    continue;
                }

                for (MonitoredFile monitoredFile : monitoredFiles) {
                    if (!monitoredFile.file.exists()) {
                        // the monitored file does not exist yet (user needs to run a build?)
                        LOG.info("File [{}] does not exist yet. Run a build?", monitoredFile.file.getAbsolutePath());
                        continue;
                    }

                    // only re-parse the file if it has changed since the last iteration
                    long currentLastMod = monitoredFile.file.lastModified();
                    if (currentLastMod == monitoredFile.fileLastModifiedMS) {
                        // the file hasn't changed since we last parsed it, bail
                        LOG.info("FilePoller will not parse [{}] because it hasn't changed.", monitoredFile.file.getName());
                        continue;
                    }
                    monitoredFile.fileLastModifiedMS = currentLastMod;

                    // reparse the file, but use the previous results as a cache, to avoid reparsing lines we have already parsed
                    BazelBuildEventsFileContents previousContent = monitoredFile.previousResults;
                    BazelBuildEventsFileParser bepFile = monitoredFile.bepFile;
                    BazelBuildEventsFileContents newContent =
                            bepFile.readEvents("BazelBuildEventsFileStream", previousContent);
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
        }

    }

    private static class MonitoredFile {
        public File file;
        public long fileLastModifiedMS = 0L;
        public BazelBuildEventsFileParser bepFile;
        public BazelBuildEventsFileContents previousResults;
    }
}
