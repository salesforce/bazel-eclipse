/*-
 * Copyright (c) 2024 Salesforce and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Salesforce - adapted from M2E, JDT or other Eclipse project
 */
package com.salesforce.bazel.eclipse.core.util.trace;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.salesforce.bazel.eclipse.core.util.trace.Trace.Span;

/**
 * Builds a tree of a single {@link Trace} for output purposes
 */
public class TraceTree {

    interface TraceTreeVisitor {
        /**
         * Begin visiting a node.
         *
         * @param node
         *            the node to visit
         * @return <code>true</code> if the node is relevant and its children should be processed, <code>false</code>
         *         otherwise
         */
        boolean visitEnter(SpanNode node);

        /**
         * Called after a node's children were visited.
         * <p>
         * Will always be called for each node where {@link #visitEnter(SpanNode)} was called.
         * </p>
         *
         * @param node
         *            the visited node
         */
        void visitLeave(SpanNode node);
    }

    public static record SpanNode(
            String name,
            long startTimeEpocMilli,
            long durationNanos,
            double percentageOfRoot,
            java.util.List<SpanNode> children) {
        public SpanNode(String name, long startTimeEpocMilli, long durationNanos, double percentageOfRoot, java.util.List<SpanNode> children) {
            this.name = requireNonNull(name);
            this.startTimeEpocMilli = startTimeEpocMilli;
            this.durationNanos = durationNanos;
            this.percentageOfRoot = percentageOfRoot;
            this.children = requireNonNull(children);
        }

        void visit(TraceTreeVisitor visitor) {
            if (visitor.visitEnter(this)) {
                children().forEach(c -> c.visit(visitor));
            }
            visitor.visitLeave(this);
        }

        public JsonObject toJson() {
            var node = new JsonObject();
            node.addProperty("name", name());
            node.addProperty("startTimeEpocMilli", startTimeEpocMilli());
            node.addProperty("durationNanos", durationNanos());
            node.addProperty("persentageOfRoot", percentageOfRoot());
            if (!children().isEmpty()) {
                var children = new JsonArray();
                for (SpanNode child : children()) {
                    children.add(child.toJson());
                }
                node.add("children", children);
            }
            return node;
        }
    }

    public static TraceTree create(Trace trace) {
        var span = trace.getRoot();
        var rootDurationNanos = span.getDuration(TimeUnit.NANOSECONDS);

        return new TraceTree(createNode(span, rootDurationNanos));
    }

    private static SpanNode createNode(Span span, long rootDurationNanos) {
        List<SpanNode> children =
                span.getChildren().stream().map(c -> createNode(c, rootDurationNanos)).collect(toList());
        // if there is only one child, we flatten the hierarchy
        if (children.size() == 1) {
            return children.get(0);
        }

        var durationNanos = span.getDuration(TimeUnit.NANOSECONDS);
        var startTimeEpocMilli = span.getStartTimeEpocMilli();
        return new SpanNode(span.getName(), startTimeEpocMilli, durationNanos, percentage(durationNanos, rootDurationNanos), children);
    }

    private static double percentage(long duration, long rootDuration) {
        return rootDuration == 0 ? 0 : ((double) duration / rootDuration) * 100;
    }

    private final SpanNode rootNode;

    public TraceTree(SpanNode rootNode) {
        this.rootNode = rootNode;
    }

    /**
     * @return the rootNode
     */
    public SpanNode getRootNode() {
        return rootNode;
    }

    public void visit(TraceTreeVisitor visitor) {
        getRootNode().visit(visitor);
    }
}
