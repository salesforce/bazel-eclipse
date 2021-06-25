package com.salesforce.bazel.sdk.bep.file;

import java.util.ArrayList;
import java.util.List;

import com.salesforce.bazel.sdk.bep.event.BEPStartedEvent;
import com.salesforce.bazel.sdk.bep.event.BEPEvent;

/**
 * Contents of a single pass parsing of a BEP json file.
 */
public class BEPFileContents {
    public List<BEPEvent> events = new ArrayList<>();

    // commonly needed quick lookups, if any event has these fields set, we set the flag on the result
    public BEPStartedEvent startedEvent = null;
    public boolean hasLastEvent = false;
    public boolean hasBuildError = false;
    public boolean hasTestError = false;
}
