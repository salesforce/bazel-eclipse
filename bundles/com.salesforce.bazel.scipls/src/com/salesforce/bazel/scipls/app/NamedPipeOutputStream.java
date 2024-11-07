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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.WritableByteChannel;

/**
 * {@link OutputStream} for communicating via a named pipe.
 */
class NamedPipeOutputStream extends OutputStream {

    private WritableByteChannel unixChannel;
    private AsynchronousFileChannel winChannel;
    private final ByteBuffer buffer = ByteBuffer.allocate(1);

    public NamedPipeOutputStream(AsynchronousFileChannel channel) {
        winChannel = channel;
    }

    public NamedPipeOutputStream(WritableByteChannel channel) {
        unixChannel = channel;
    }

    @Override
    public void write(byte[] b) throws IOException {
        final var BUFFER_SIZE = 1024;
        var blocks = b.length / BUFFER_SIZE;
        var writeBytes = 0;
        for (var i = 0; i <= blocks; i++) {
            var offset = i * BUFFER_SIZE;
            var length = Math.min(b.length - writeBytes, BUFFER_SIZE);
            if (length <= 0) {
                break;
            }
            writeBytes += length;
            var buffer = ByteBuffer.wrap(b, offset, length);
            if (winChannel != null) {
                var result = winChannel.write(buffer, 0);
                try {
                    result.get();
                } catch (Exception e) {
                    throw new IOException(e);
                }
            } else {
                unixChannel.write(buffer);
            }
        }
    }

    @Override
    public void write(int b) throws IOException {
        buffer.clear();
        buffer.put((byte) b);
        buffer.position(0);
        if (winChannel != null) {
            var result = winChannel.write(buffer, 0);
            try {
                result.get();
            } catch (Exception e) {
                throw new IOException(e);
            }
        } else {
            unixChannel.write(buffer);
        }
    }
}