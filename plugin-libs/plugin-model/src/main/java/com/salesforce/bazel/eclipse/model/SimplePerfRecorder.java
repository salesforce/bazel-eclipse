package com.salesforce.bazel.eclipse.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class SimplePerfRecorder {
    
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
            elapsedTimes.put(operationId, previous+elapsedTimeMS);
        } else {
            elapsedTimes.put(operationId, elapsedTimeMS);
        }
        
        Integer previousCount = counts.get(operationId);
        if (previousCount != null) {
            counts.put(operationId, previousCount+1);
        } else {
            counts.put(operationId, 1);
        }
    }
    
    
    public static void logResults() {
        System.out.println("**** Performance Results ****");
        for (String operationId : elapsedTimes.keySet()) {
            System.out.println("  "+operationId+": "+elapsedTimes.get(operationId)+"ms  invocations: "+counts.get(operationId));
        }
        
    }
}

