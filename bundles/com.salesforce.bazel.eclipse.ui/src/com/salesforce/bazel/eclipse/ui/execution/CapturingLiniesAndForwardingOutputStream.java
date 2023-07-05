/*-
 * Copyright (c) 2023 Salesforce and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Salesforce - Partially adapted and heavily inspired from Eclipse JDT, M2E and PDE
 */
package com.salesforce.bazel.eclipse.ui.execution;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.function.Predicate;

import org.eclipse.debug.internal.core.StreamDecoder;
import org.eclipse.ui.console.MessageConsoleStream;

/**
 * This output stream captures the last lines of output while forwarding everything else to the console stream.
 * <p>
 * Note, the console stream will never be closed when closing this stream
 * </p>
 */
@SuppressWarnings("restriction")
public class CapturingLiniesAndForwardingOutputStream extends OutputStream {

    /**
     * Flag indicating whether this stream has been closed.
     */
    private volatile boolean closed;

    private final int linesToCapture;
    private final MessageConsoleStream consoleStream;
    private final StreamDecoder decoder;

    private final ArrayDeque<String> capturedLines;
    private final ArrayDeque<String> capturedLinesFiltered;
    private final Predicate<String> capturedLinesFilter;
    private final StringBuilder currentLine;

    public CapturingLiniesAndForwardingOutputStream(MessageConsoleStream consoleStream, Charset charset,
            int linesToCapture, Predicate<String> capturedLinesFilter) {
        this.capturedLinesFilter = capturedLinesFilter;
        if (linesToCapture <= 0) {
            throw new IllegalArgumentException("Cannot use zero or a negative number for number of lines to caputer");
        }
        this.consoleStream = consoleStream;
        this.decoder = new StreamDecoder(charset);
        this.linesToCapture = linesToCapture;
        capturedLines = new ArrayDeque<>(linesToCapture);
        capturedLinesFiltered = new ArrayDeque<>(linesToCapture);
        currentLine = new StringBuilder(250);
    }

    private synchronized void captureString(String writtenString) throws IOException {
        if (closed) {
            throw new IOException("Output Stream is closed"); //$NON-NLS-1$
        }

        // ignore all \r
        writtenString = writtenString.replaceAll("\r", "");

        // create new lines where necessary
        var newLinePos = writtenString.indexOf('\n');
        while (newLinePos > -1) {
            finishCurrentLine(writtenString.substring(0, newLinePos));
            writtenString = writtenString.substring(newLinePos + 1);
            newLinePos = writtenString.indexOf('\n');
        }

        // add remaining to current line
        if (writtenString.length() > 0) {
            currentLine.append(writtenString);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            // Closeable#close() has no effect if already closed
            return;
        }

        // finish current line
        if (currentLine.length() > 0) {
            finishCurrentLine("");
        }
        closed = true;

        // we don't close/manage the console stream
    }

    private void finishCurrentLine(String string) {
        // finish current line
        currentLine.append(string);

        // ensure capture lines remain within limit
        while (capturedLines.size() >= linesToCapture) {
            capturedLines.removeFirst();
        }
        // add line
        var line = currentLine.toString();
        capturedLines.add(line);

        // add line to filtered list if matching
        if ((capturedLinesFilter != null) && capturedLinesFilter.test(line)) {
            while (capturedLinesFiltered.size() >= linesToCapture) {
                capturedLinesFiltered.removeFirst();
            }
            capturedLinesFiltered.add(line);
        }
        // reset current line
        currentLine.setLength(0);
    }

    public synchronized Collection<String> getCapturedLines() {
        var lines = new ArrayDeque<>(capturedLines);
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        while (lines.size() > linesToCapture) {
            lines.removeFirst();
        }
        return lines;
    }

    public synchronized Collection<String> getCapturedLinesFiltered() {
        var lines = new ArrayDeque<>(capturedLinesFiltered);
        if ((currentLine.length() > 0) && (capturedLinesFilter != null)
                && capturedLinesFilter.test(currentLine.toString())) {
            lines.add(currentLine.toString());
        }
        while (lines.size() > linesToCapture) {
            lines.removeFirst();
        }
        return lines;
    }

    public int getLinesToCapture() {
        return linesToCapture;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        consoleStream.write(b, off, len);

        var s = this.decoder.decode(b, off, len);
        captureString(s);
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[] { (byte) b }, 0, 1);
    }
}
