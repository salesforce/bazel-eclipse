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
 *      Salesforce - adapted from ParentProcessWatcher from JDTLS
 */
package com.salesforce.bazel.scipls.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.ExecutionException;

/**
 * {@link InputStream} for communicating via a named pipe
 */
class NamedPipeInputStream extends InputStream {

    private ReadableByteChannel unixChannel;
    private AsynchronousFileChannel winChannel;
    private final ByteBuffer buffer = ByteBuffer.allocate(1024);
    private int readyBytes = 0;

    public NamedPipeInputStream(AsynchronousFileChannel channel) {
        winChannel = channel;
    }

    public NamedPipeInputStream(ReadableByteChannel channel) {
        unixChannel = channel;
    }

    @Override
    public int read() throws IOException {
        if (buffer.position() < readyBytes) {
            return buffer.get() & 0xFF;
        }
        try {
            buffer.clear();
            if (winChannel != null) {
                readyBytes = winChannel.read(buffer, 0).get();
            } else {
                readyBytes = unixChannel.read(buffer);
            }
            if (readyBytes == -1) {
                return -1; // EOF
            }
            buffer.flip();
            return buffer.get() & 0xFF;
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException(e);
        }
    }
}