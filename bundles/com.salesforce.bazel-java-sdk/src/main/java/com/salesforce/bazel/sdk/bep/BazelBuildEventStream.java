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
