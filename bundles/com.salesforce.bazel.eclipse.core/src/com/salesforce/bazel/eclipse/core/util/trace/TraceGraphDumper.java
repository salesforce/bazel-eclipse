package com.salesforce.bazel.eclipse.core.util.trace;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.salesforce.bazel.eclipse.core.util.trace.TraceTree.SpanNode;
import com.salesforce.bazel.eclipse.core.util.trace.TraceTree.TraceTreeVisitor;

/**
 * A dependency visitor that dumps the graph to the console.
 */
public class TraceGraphDumper implements TraceTreeVisitor {

    private static class ChildInfo {

        final int count;

        int index;

        ChildInfo(int count) {
            this.count = count;
        }

        public String formatIndentation(boolean end) {
            final var last = (index + 1) >= count;
            if (end) {
                return last ? "\\- " : "+- ";
            }
            return last ? "   " : "|  ";
        }

    }

    private final float minimumPercentage;

    private final List<String> out = new ArrayList<>();

    private final List<ChildInfo> childInfos = new ArrayList<>();

    private final int nameLength;

    private final TimeUnit timeUnit;

    public TraceGraphDumper(int nameLength, float minimumPercentage, TimeUnit timeUnit) {
        this.nameLength = nameLength;
        this.minimumPercentage = minimumPercentage;
        this.timeUnit = timeUnit;
    }

    private String formatIndentation() {
        final var buffer = new StringBuilder(128);
        for (final var it = childInfos.iterator(); it.hasNext();) {
            buffer.append(it.next().formatIndentation(!it.hasNext()));
        }
        return buffer.toString();
    }

    private String formatNode(SpanNode node) {
        final var buffer = new StringBuilder(128);

        buffer.append(String.format("%5.1f%% ", node.percentageOfRoot()));
        var indentation = formatIndentation();
        buffer.append(indentation);
        buffer.append(node.name());
        for (var i = node.name().length() + indentation.length(); i < nameLength; i++) {
            buffer.append(" ");
        }

        var duration = timeUnit.convert(node.durationNanos(), TimeUnit.NANOSECONDS);
        var durationUnit = switch (timeUnit) {
            case NANOSECONDS -> "ns";
            case MICROSECONDS -> "µs";
            case MILLISECONDS -> "ms";
            case SECONDS -> "s";
            case MINUTES -> "m";
            case HOURS -> "h";
            case DAYS -> "d";
            default -> throw new IllegalArgumentException("Unexpected value: " + timeUnit);
        };
        buffer.append(String.format("%15s", String.format("%d%s", duration, durationUnit)));

        return buffer.toString();
    }

    public List<String> getDump() {
        return out;
    }

    @Override
    public boolean visitEnter(SpanNode node) {
        out.add(formatNode(node));
        childInfos.add(new ChildInfo(node.children().size()));

        // skip children if percentage is below threshold
        return node.percentageOfRoot() >= minimumPercentage;
    }

    @Override
    public void visitLeave(SpanNode node) {
        if (!childInfos.isEmpty()) {
            childInfos.remove(childInfos.size() - 1);
        }
        if (!childInfos.isEmpty()) {
            childInfos.get(childInfos.size() - 1).index++;
        }
    }

}