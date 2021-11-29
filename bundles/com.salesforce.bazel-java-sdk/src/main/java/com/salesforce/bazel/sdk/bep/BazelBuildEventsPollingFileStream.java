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
package com.salesforce.bazel.sdk.bep;

import java.io.File;
import java.lang.invoke.MethodHandles;

import com.salesforce.bazel.sdk.bep.file.BEPMonitoredFile;
import com.salesforce.bazel.sdk.logging.LogHelper;

/**
 * Bazel build event protocol stream (BEP) for a Bazel workspace. A BEP stream allows you to monitor and react to build
 * events emitted from Bazel. This implementation spawns a thread and actively polls one or more BEP files for new
 * events. It is intelligent and will only reparse a file if the file modification time has changed.
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
public class BazelBuildEventsPollingFileStream extends BazelBuildEventsFileStream {
    private static final LogHelper LOG = LogHelper.log(MethodHandles.lookup().lookupClass());

    public int filePollerIntervalSeconds = 5;
    private FilePoller filePoller = null;

    public BazelBuildEventsPollingFileStream() {}

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
        BEPMonitoredFile monitoredFile = addFileToMonitor_Internal(bepFile);

        // if caller does not want the initial state parsed, capture the current last mod
        if (!parseOnStart && monitoredFile.file.exists()) {
            monitoredFile.fileLastModifiedMS = monitoredFile.file.lastModified();
        }
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

                for (BEPMonitoredFile monitoredFile : monitoredFiles) {
                    if (!monitoredFile.file.exists()) {
                        // the monitored file does not exist yet (user needs to run a build?)
                        LOG.info("File [{}] does not exist yet. Run a build?", monitoredFile.file.getAbsolutePath());
                        continue;
                    }

                    // only re-parse the file if it has changed since the last iteration
                    long currentLastMod = monitoredFile.file.lastModified();
                    if (currentLastMod == monitoredFile.fileLastModifiedMS) {
                        // the file hasn't changed since we last parsed it, bail
                        LOG.info("FilePoller will not parse [{}] because it hasn't changed.",
                            monitoredFile.file.getName());
                        continue;
                    }
                    monitoredFile.fileLastModifiedMS = currentLastMod;

                    // parse the file and publish events
                    processFile(monitoredFile);
                }
            }
        }

    }
}
