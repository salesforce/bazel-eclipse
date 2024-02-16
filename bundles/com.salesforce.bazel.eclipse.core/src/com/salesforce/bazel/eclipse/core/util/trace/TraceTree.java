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

import com.salesforce.bazel.eclipse.core.util.trace.Trace.Span;

/**
 * Builds a tree of a single {@link Trace} for output purposes
 */
public class TraceTree {

    interface TraceTreeVisitor {
        boolean visitEnter(SpanNode node);

        void visitLeave(SpanNode node);
    }

    public static record SpanNode(
            String name,
            long durationNanos,
            float percentageOfRoot,
            java.util.List<SpanNode> children) {
        public SpanNode(String name, long durationNanos, float percentageOfRoot, java.util.List<SpanNode> children) {
            this.name = requireNonNull(name);
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
    }

    public static TraceTree create(Trace trace, TimeUnit resolution) {
        var span = trace.getRoot();
        var rootDurationNanos = span.getDuration(TimeUnit.NANOSECONDS);

        return new TraceTree(createNode(span, rootDurationNanos));
    }

    private static SpanNode createNode(Span span, long rootDurationNanos) {
        List<SpanNode> children =
                span.getChildren().stream().map(c -> createNode(c, rootDurationNanos)).collect(toList());
        var durationNanos = span.getDuration(TimeUnit.NANOSECONDS);
        return new SpanNode(span.getName(), durationNanos, percentage(durationNanos, rootDurationNanos), children);
    }

    private static float percentage(long duration, long rootDuration) {
        return rootDuration == 0 ? 0 : ((float) duration / rootDuration) * 100;
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
