package com.salesforce.bazel.sdk.util;

import java.util.LinkedHashMap;
import java.util.Map;

import com.salesforce.bazel.sdk.logging.LogHelper;

public class SimplePerfRecorder {
    private static final LogHelper LOG = LogHelper.log(SimplePerfRecorder.class);

    public static Map<String, Long> elapsedTimes = new LinkedHashMap<>();
    public static Map<String, Integer> counts = new LinkedHashMap<>();

    public static void reset() {
        elapsedTimes = new LinkedHashMap<>();
        counts = new LinkedHashMap<>();
    }

    public static void addTime(String operationId, long startTime) {
        Long previous = elapsedTimes.get(operationId);
        long elapsedTimeMS = System.currentTimeMillis() - startTime;
        if (previous != null) {
            elapsedTimes.put(operationId, previous + elapsedTimeMS);
        } else {
            elapsedTimes.put(operationId, elapsedTimeMS);
        }

        Integer previousCount = counts.get(operationId);
        if (previousCount != null) {
            counts.put(operationId, previousCount + 1);
        } else {
            counts.put(operationId, 1);
        }
    }

    public static void logResults() {
        LOG.info("**** Performance Results ****");
        for (String operationId : elapsedTimes.keySet()) {
            LOG.info("  " + operationId + ": " + elapsedTimes.get(operationId) + "ms  invocations: "
                    + counts.get(operationId));
        }

    }
}
