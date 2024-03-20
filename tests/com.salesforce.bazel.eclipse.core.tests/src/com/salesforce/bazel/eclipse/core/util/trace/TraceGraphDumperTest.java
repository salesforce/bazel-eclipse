package com.salesforce.bazel.eclipse.core.util.trace;

import static java.util.stream.Collectors.joining;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

public class TraceGraphDumperTest {

    @Test
    void ensure_output_produced_for_sample_trace() throws Exception {
        var traceTree = simpleSampleTree();

        var traceGraphDumper = new TraceGraphDumper(20, 0F, TimeUnit.MICROSECONDS);
        traceTree.visit(traceGraphDumper);

        var dump = traceGraphDumper.getDump();
        System.out.println(dump.stream().collect(joining(System.lineSeparator())));

        assertThat(
            dump,
            contains(
                " 80.0% Root                          100µs",
                "  3.0% +- Child 1                     50µs",
                "  5.0% \\- Child 2                     30µs"));

    }

    private TraceTree simpleSampleTree() {
        var rootNode = new TraceTree.SpanNode(
                "Root",
                1999999L,
                100000L,
                80.0F,
                List.of(
                    new TraceTree.SpanNode("Child 1", 1999999L, 50000L, 3.0F, Collections.emptyList()),
                    new TraceTree.SpanNode("Child 2", 1999999L, 30000L, 5.0F, Collections.emptyList())));

        return new TraceTree(rootNode);
    }

}
