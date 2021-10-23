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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.salesforce.bazel.sdk.bep.event.BEPEvent;

/**
 * Abstraction for a Bazel Build Event Protocol event stream. See docs/buildeventprotocol.md for details.
 */
public abstract class BazelBuildEventStream {

    List<BazelBuildEventSubscriber> subscribeAll = new ArrayList<>();

    Map<String, List<BazelBuildEventSubscriber>> subscribeFiltered = new HashMap<>();
    List<BazelBuildEventSubscriber> subscribeLastMessage = new ArrayList<>();

    boolean paused = false;

    // PUBLIC API

    /**
     * Subscribe to all BEP events.
     */
    public void subscribe(BazelBuildEventSubscriber subscriber) {
        subscribeAll.add(subscriber);
    }

    /**
     * Subscribe to particular BEP events.
     *
     * @param subscriber
     *            your subscriber that processes the events
     * @param eventTypes
     *            the list of event types that you wish to notified for this subscriber, null to subscribe to all event
     *            types
     * @param matchLastMessage
     *            if the last message in a build is not in the eventTypes, should the event still be published to this
     *            subscriber?
     */
    public void subscribe(BazelBuildEventSubscriber subscriber, Set<String> eventTypes, boolean matchLastMessage) {
        if (eventTypes == null) {
            subscribeAll.add(subscriber);
        } else {
            for (String eventType : eventTypes) {
                List<BazelBuildEventSubscriber> subscribers = subscribeFiltered.get(eventType);
                if (subscribers == null) {
                    subscribers = new ArrayList<>();
                    subscribeFiltered.put(eventType, subscribers);
                }
                subscribers.add(subscriber);
            }

            if (matchLastMessage) {
                subscribeLastMessage.add(subscriber);
            }
        }
    }

    /**
     * API to pause monitoring the Bazel build.
     */
    public void pauseStream() {
        paused = true;
    }

    /**
     * API to reactivate a stream after calling pauseStream().
     */
    public void activateStream() {
        paused = false;
    }

    // INTERNALS

    protected void publishEventToSubscribers(BEPEvent event) {
        if (paused) {
            return;
        }
        if (BazelBuildEventTypeManager.EVENTTYPE_IGNORED.equals(event.getEventType())) {
            // these are the BEP events we don't care about; see how to register additional
            // types (so the one you want will not be ignored) in BazelBuildEventTypeManager
            return;
        }

        for (BazelBuildEventSubscriber subscriber : subscribeAll) {
            subscriber.onEvent(event);
        }

        String eventType = event.getEventType();

        if (event.isLastMessage()) {
            for (BazelBuildEventSubscriber subscriber : subscribeLastMessage) {
                subscriber.onEvent(event);
            }
        } else {
            List<BazelBuildEventSubscriber> subscribers = subscribeFiltered.get(eventType);
            if (subscribers != null) {
                for (BazelBuildEventSubscriber subscriber : subscribers) {
                    subscriber.onEvent(event);
                }
            }
        }
    }

    protected void publishLastEventToFilterSubscribers(BEPEvent event) {
        if (paused) {
            return;
        }
        if (BazelBuildEventTypeManager.EVENTTYPE_IGNORED.equals(event.getEventType())) {
            // these are the BEP events we don't care about; see how to register additional
            // types (so the one you want will not be ignored) in BazelBuildEventTypeManager
            return;
        }

        List<BazelBuildEventSubscriber> notified = new ArrayList<>();

        for (BazelBuildEventSubscriber subscriber : subscribeAll) {
            subscriber.onEvent(event);
            notified.add(subscriber);
        }

        String eventType = event.getEventType();
        List<BazelBuildEventSubscriber> subscribers = subscribeFiltered.get(eventType);
        if (subscribers != null) {
            for (BazelBuildEventSubscriber subscriber : subscribers) {
                subscriber.onEvent(event);
                notified.add(subscriber);
            }
        }
    }

}
