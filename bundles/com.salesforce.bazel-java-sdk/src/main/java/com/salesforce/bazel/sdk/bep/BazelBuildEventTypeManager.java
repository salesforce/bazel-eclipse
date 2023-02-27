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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.bep.event.BEPBuildFinishedEvent;
import com.salesforce.bazel.sdk.bep.event.BEPBuildMetricsEvent;
import com.salesforce.bazel.sdk.bep.event.BEPConfigurationEvent;
import com.salesforce.bazel.sdk.bep.event.BEPEvent;
import com.salesforce.bazel.sdk.bep.event.BEPNamedSetEvent;
import com.salesforce.bazel.sdk.bep.event.BEPOptionsParsedEvent;
import com.salesforce.bazel.sdk.bep.event.BEPPatternEvent;
import com.salesforce.bazel.sdk.bep.event.BEPProgressEvent;
import com.salesforce.bazel.sdk.bep.event.BEPStartedEvent;
import com.salesforce.bazel.sdk.bep.event.BEPTargetCompletedEvent;
import com.salesforce.bazel.sdk.bep.event.BEPTargetConfiguredEvent;
import com.salesforce.bazel.sdk.bep.event.BEPTestResultEvent;
import com.salesforce.bazel.sdk.bep.event.BEPTestSummaryEvent;
import com.salesforce.bazel.sdk.bep.event.BEPUnstructuredCommandLineEvent;

public class BazelBuildEventTypeManager {
    private static Logger LOG = LoggerFactory.getLogger(BazelBuildEventTypeManager.class);

    public static final String EVENTTYPE_IGNORED = "ignored";

    /**
     * List of resolvers that can map a textual BEP event type to a concrete implementation. SDK users can add their own
     * resolver if they need to process more BEP event types than what the SDK supports.
     */
    private static List<BazelBuildEventTypeResolver> resolvers = new ArrayList<>();
    static {
        resolvers.add(new DefaultBazelBuildEventTypeResolver());
    }

    /**
     * List of recognized BEP event types. BEP supports many event types that the SDK does not implement because they
     * are uncommon or of limited use. The SDK just instantiates these event types and ignores any others.
     */
    protected static Set<String> eventTypes = new HashSet<>();
    static {
        eventTypes.add(BEPBuildFinishedEvent.NAME);
        eventTypes.add(BEPBuildMetricsEvent.NAME);
        eventTypes.add("buildToolLogs"); // this one has the lastMessage on it normally
        eventTypes.add(BEPConfigurationEvent.NAME);
        eventTypes.add(BEPOptionsParsedEvent.NAME);
        eventTypes.add(BEPPatternEvent.NAME);
        eventTypes.add(BEPProgressEvent.NAME);
        eventTypes.add(BEPNamedSetEvent.NAME);
        eventTypes.add(BEPStartedEvent.NAME);
        eventTypes.add(BEPTargetCompletedEvent.NAME);
        eventTypes.add(BEPTargetConfiguredEvent.NAME);
        eventTypes.add(BEPTestResultEvent.NAME);
        eventTypes.add(BEPTestSummaryEvent.NAME);
        eventTypes.add(BEPUnstructuredCommandLineEvent.NAME);
    }

    /**
     * Add a new event type to the supported list. Requires that you also register a BazelBuildEventTypeResolver.
     */
    public static void addEventType(String eventType) {
        eventTypes.add(eventType);
    }

    /**
     * Add a new BEP event type resolver.
     */
    public static void registerTypeResolver(BazelBuildEventTypeResolver resolver) {
        // insert the new resolver at the head of the line
        resolvers.add(0, resolver);
    }

    /**
     * Normally called by a BazelBuildEventsStream while loading an event json.
     */
    public static BEPEvent parseEvent(String json, int index) {
        BEPEvent event = null;

        try {
            JSONObject eventObject = (JSONObject) new JSONParser().parse(json);
            JSONObject id = (JSONObject) eventObject.get("id");

            if (id != null) {
                // it is a little awkward to determine the event type, since the type is expressed
                // as a key name, not a key value. So we iterate through the type we care about and
                // look for a key match.
                for (String eventType : eventTypes) {
                    Object type = id.get(eventType);
                    if (type != null) {
                        // this is a supported type in the SDK
                        event = createEvent(eventType, json, index, eventObject);
                    }
                }
            }
            if (event == null) {
                // placeholder event, having this in the list makes it possible to skip reparsing
                // lines that were seen in the last iteration of this file by keeping the file lines/events
                // lists in sync
                event = createEvent(EVENTTYPE_IGNORED, json, index, null);
            }
        } catch (Exception anyE) {
            LOG.error("Error parsing json BEP event [{}]", anyE, json);
            return null;
        }

        return event;
    }

    /**
     * Used by a BazelBuildEventStream to create an event object when it receives the json event.
     */
    static BEPEvent createEvent(String eventType, String rawEvent, int index, JSONObject eventObject) {
        BEPEvent event = null;

        for (BazelBuildEventTypeResolver resolver : resolvers) {
            event = resolver.createEvent(eventType, rawEvent, index, eventObject);
            if (event != null) {
                return event;
            }
        }
        return event;
    }
}
