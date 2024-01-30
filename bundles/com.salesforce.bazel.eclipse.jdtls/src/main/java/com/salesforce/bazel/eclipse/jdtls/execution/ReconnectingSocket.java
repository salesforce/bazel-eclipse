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
package com.salesforce.bazel.eclipse.jdtls.execution;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReconnectingSocket implements Supplier<OutputStream> {

    private static Logger LOG = LoggerFactory.getLogger(ReconnectingSocket.class);

    private final int port;
    private Socket socket;
    private boolean closed;

    public ReconnectingSocket(int port) throws UnknownHostException, IOException {
        this.port = port;
        connect();
    }

    public synchronized void close() {
        closed = true;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            }
            socket = null;
        }
    }

    private void connect() throws UnknownHostException, IOException {
        LOG.debug("Connecting socket to localhost:{}", port);
        this.socket = new Socket("localhost", port);
    }

    @Override
    public synchronized OutputStream get() {
        if (!closed) {
            try {
                if (!socket.isConnected()) {
                    connect();
                }
                return socket.getOutputStream();
            } catch (IOException e) {
                LOG.warn("Error retreiving output", e);
            }
        }
        return System.out; // fallback
    }

}