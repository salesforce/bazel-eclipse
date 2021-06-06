package com.salesforce.bazel.sdk.bep;

import org.json.simple.JSONObject;

import com.salesforce.bazel.sdk.bep.event.BEPEvent;

/**
 * Interface to implement if you need to add additional/alternate support for a particular BEP event type. The resolvers
 * are invoked prior to the default type constructors, so you can override the implementation of any event type.
 * <p>
 * You must register your eventType and resolver with BazelBuildEventTypeManager.
 */
public interface BazelBuildEventTypeResolver {

    /**
     * Create the event object for the passed parameters. Or, return null to allow the default SDK functionality for the
     * BEP event.
     */
    BEPEvent createEvent(String eventType, String rawEvent, int index, JSONObject eventObject);

}
