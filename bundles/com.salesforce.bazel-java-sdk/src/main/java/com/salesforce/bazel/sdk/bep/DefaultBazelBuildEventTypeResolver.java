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

import org.json.simple.JSONObject;

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

/**
 * Default event type resolver. Normally this is the only resolver that BazelBuildEventTypeManager knows about. But if
 * you want to override the implementation used for any event, you can insert another resolver that will resolve events
 * as needed.
 */
class DefaultBazelBuildEventTypeResolver implements BazelBuildEventTypeResolver {

    /**
     * Create the event object for the passed parameters. Or, return null to allow the default SDK functionality for the
     * BEP event.
     */
    @Override
    public BEPEvent createEvent(String eventType, String rawEvent, int index, JSONObject eventObject) {
        BEPEvent event = null;
        switch (eventType) {
        case BEPBuildMetricsEvent.NAME:
            event = new BEPBuildMetricsEvent(rawEvent, index, eventObject);
            break;
        case BEPBuildFinishedEvent.NAME:
            event = new BEPBuildFinishedEvent(rawEvent, index, eventObject);
            break;
        case BEPConfigurationEvent.NAME:
            event = new BEPConfigurationEvent(rawEvent, index, eventObject);
            break;
        case BEPOptionsParsedEvent.NAME:
            event = new BEPOptionsParsedEvent(rawEvent, index, eventObject);
            break;
        case BEPPatternEvent.NAME:
            event = new BEPPatternEvent(rawEvent, index, eventObject);
            break;
        case BEPProgressEvent.NAME:
            event = new BEPProgressEvent(rawEvent, index, eventObject);
            break;
        case BEPNamedSetEvent.NAME:
            event = new BEPNamedSetEvent(rawEvent, index, eventObject);
            break;
        case BEPStartedEvent.NAME:
            event = new BEPStartedEvent(rawEvent, index, eventObject);
            break;
        case BEPTargetCompletedEvent.NAME:
            event = new BEPTargetCompletedEvent(rawEvent, index, eventObject);
            break;
        case BEPTargetConfiguredEvent.NAME:
            event = new BEPTargetConfiguredEvent(rawEvent, index, eventObject);
            break;
        case BEPTestResultEvent.NAME:
            event = new BEPTestResultEvent(rawEvent, index, eventObject);
            break;
        case BEPTestSummaryEvent.NAME:
            event = new BEPTestSummaryEvent(rawEvent, index, eventObject);
            break;
        case BEPUnstructuredCommandLineEvent.NAME:
            event = new BEPUnstructuredCommandLineEvent(rawEvent, index, eventObject);
            break;
        default:
            event = new BEPEvent(eventType, rawEvent, index, eventObject);
        }
        return event;
    }
}
