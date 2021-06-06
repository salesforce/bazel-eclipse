package com.salesforce.bazel.sdk.bep;

import com.salesforce.bazel.sdk.bep.event.BEPEvent;

/**
 * Interface for subscribing to a BazelBuildEventStream.
 * <p>
 * Avoided using newer Java features here to keep this code approachable (see design tenets).
 */
public interface BazelBuildEventSubscriber {

    /**
     * Invoked when each subscribed event is received from Bazel
     */
    void onEvent(BEPEvent event);

}
