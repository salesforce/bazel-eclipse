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
