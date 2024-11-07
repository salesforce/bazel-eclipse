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
package com.salesforce.bazel.scipls.app;

import static java.lang.String.format;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.CompletionHandler;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.scipls.ScipBazelJavaLanguageServer;

/**
 * Main OSGi application for launching the language server process.
 * <p>
 * The server process is the main master process. From a design perspective only one server process is needed to support
 * multiple language server clients. This allows for better scalability in the back-end for Web IDEs where multiple IDE
 * processes exist for different browser tabs. However, this application also supports a single mode where the process
 * is launched by a language server client parent process.
 * </p>
 */
public class ServerApplication implements IApplication {

    private static final Logger LOG = LoggerFactory.getLogger(ServerApplication.class);

    private static void closeChannelQuietly(Channel channelToClose) {
        try {
            channelToClose.close();
        } catch (IOException e) {
            LOG.debug("Ignored exception closing channel '{}': {}", channelToClose, e.getMessage(), e);
        }
    }

    static void forceShutdown() {
        FrameworkUtil.getBundle(ServerApplication.class.getClassLoader())
                .ifPresentOrElse(ServerApplication::stopSystemBundle, () -> {
                    LOG.debug("No OSGi system found. Exiting.");
                    System.exit(1);
                });
    }

    private static void stopSystemBundle(Bundle b) {
        try {
            var bundleContext = b.getBundleContext();
            if (bundleContext != null) {
                var bundle = bundleContext.getBundle(Constants.SYSTEM_BUNDLE_ID);
                if (bundle != null) {
                    LOG.debug("Found OSGi system bundle. Stopping framework.");
                    bundle.stop();
                    return;
                }
            }
        } catch (BundleException e) {
            LOG.error("Error stopping system bundle: {}", e.getMessage(), e);
        }

        LOG.warn("Unable to shutdown OSGi system properly. Exiting.");
        System.exit(1);
    }

    private final CountDownLatch stopSignal = new CountDownLatch(1);
    private final List<ScipBazelJavaLanguageServer> activeLanguageServers = new CopyOnWriteArrayList<>();

    private boolean isWindows() {
        return Platform.OS_WIN32.equals(Platform.getOS());
    }

    private int launchPipeBasedSingleServer(Path pipe) {
        LOG.info("Launching language server for pipe '{}'", pipe);

        Channel channelToClose;
        InputStream in;
        OutputStream out;
        if (isWindows()) {
            try {
                var channel = AsynchronousFileChannel.open(pipe, StandardOpenOption.READ, StandardOpenOption.WRITE);
                channelToClose = channel;
                in = new NamedPipeInputStream(channel);
                out = new NamedPipeOutputStream(channel);
            } catch (IOException e) {
                throw new IllegalStateException(
                        format("Unable to initialize pipe communication on Windows to '%s': %s", pipe, e.getMessage()),
                        e);
            }
        } else {
            var socketAddress = UnixDomainSocketAddress.of(pipe);
            try {
                var channel = SocketChannel.open(StandardProtocolFamily.UNIX);
                channelToClose = channel;
                channel.connect(socketAddress);
                in = new NamedPipeInputStream(channel);
                out = new NamedPipeOutputStream(channel);
            } catch (IOException e) {
                throw new IllegalStateException(
                        format("Unable to initialize pipe communication on Windows to '%s': %s", pipe, e.getMessage()),
                        e);
            }
        }

        var parentProcessWatcher = ProcessWatcher.forCurrentParentProcess(ServerApplication::forceShutdown);

        var languageServer = new ScipBazelJavaLanguageServer(ls -> {
            // remove from list of active servers
            activeLanguageServers.remove(ls);

            // stop process watcher
            parentProcessWatcher.stop();

            // close channel
            closeChannelQuietly(channelToClose);

            // since this is the only active language server we immediately trigger the stop signal
            stopSignal.countDown();
        });
        activeLanguageServers.add(languageServer);
        var launcher = LSPLauncher.createServerLauncher(languageServer, in, out);
        languageServer.connect(launcher.getRemoteProxy());
        var listeningFuture = launcher.startListening();
        try {
            while (!stopSignal.await(10, TimeUnit.SECONDS)) {
                if (listeningFuture.isDone()) {
                    // the language server is done listening, trigger orderly shutdown
                    stopSignal.countDown();
                }
            }
        } catch (InterruptedException e) {
            LOG.debug("Interrupted. Shutting down.", e);
            return 1;
        }

        activeLanguageServers.clear();
        parentProcessWatcher.stop();

        LOG.info("Language server finished.");
        return IApplication.EXIT_OK;
    }

    private int launchTcpBasedMultiServer(String host, int port) {
        LOG.info("Launching language server for accepting connections on '{}:{}'", host, port);

        var inetSocketAddress = new InetSocketAddress(host, port);
        try {
            var serverSocket = AsynchronousServerSocketChannel.open().bind(inetSocketAddress);
            while (stopSignal.getCount() > 0) {
                serverSocket.accept(null, new CompletionHandler<AsynchronousSocketChannel, Object>() {
                    @Override
                    public void completed(AsynchronousSocketChannel socketChannel, Object attachment) {
                        // get ready for handling another connection
                        if ((stopSignal.getCount() > 0) && serverSocket.isOpen()) {
                            serverSocket.accept(null, this);
                        }

                        // initiate LS
                        try {
                            LOG.info("New connection from '{}'", socketChannel.getRemoteAddress());
                        } catch (IOException e) {
                            LOG.error("Invalid connection '{}': {}", socketChannel, e.getMessage(), e);
                            return;
                        }

                        var in = Channels.newInputStream(socketChannel);
                        var out = Channels.newOutputStream(socketChannel);

                        var languageServer = new ScipBazelJavaLanguageServer(ls -> {
                            // remove from list of active servers
                            activeLanguageServers.remove(ls);

                            // close channel
                            closeChannelQuietly(socketChannel);
                        });
                        activeLanguageServers.add(languageServer);
                        var launcher = LSPLauncher.createServerLauncher(languageServer, in, out);
                        languageServer.connect(launcher.getRemoteProxy());
                        launcher.startListening();
                    }

                    @Override
                    public void failed(Throwable e, Object attachment) {
                        if (e instanceof AsynchronousCloseException) {
                            LOG.debug("Channel closed.");
                            return;
                        }

                        LOG.error("Error waiting for connection: {}", e.getMessage(), e);

                        // get ready for handling another connection
                        if ((stopSignal.getCount() > 0) && serverSocket.isOpen()) {
                            serverSocket.accept(null, this);
                        }
                    }
                });

                stopSignal.await();
            }

            // notify all active LS
            while (!activeLanguageServers.isEmpty()) {
                try {
                    var ls = activeLanguageServers.remove(0);
                    if (ls != null) {
                        ls.notifyClientOfShutdown();
                    }
                } catch (IndexOutOfBoundsException ignored) {
                    // can happen when there are shutdowns from clients in parallel
                    LOG.debug(
                        "Concurrent shutdowns from clients in progress while shutting down server. If logged more than once please check the implementation.",
                        ignored);
                }
            }

            // close the socket
            closeChannelQuietly(serverSocket);
        } catch (IOException e) {
            LOG.error("Error initializing server socket on '{}:{}': {}", host, port, e.getMessage(), e);
            return 1;
        } catch (InterruptedException e) {
            LOG.debug("Interrupted. Shutting down.", e);
            return 1;
        }

        LOG.info("Language server finished.");
        return IApplication.EXIT_OK;
    }

    private Path readPipeFromEnv() {
        var value = System.getenv("SCIP_LS_PIPE");
        if (value != null) {
            return Path.of(value);
        }
        return null;
    }

    private Integer readPortFromEnv() {
        var value = System.getenv("SCIP_LS_PORT");
        if (value != null) {
            try {
                var port = Integer.parseInt(value);
                if ((port <= 0) || (port > 65535)) {
                    throw new IllegalArgumentException("Port out of range. Check SCIP_LS_PORT: " + value);
                }
                return port;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid port specified. Check SCIP_LS_PORT: " + value);
            }
        }

        return null;
    }

    @Override
    public Object start(IApplicationContext context) throws Exception {
        // signal running immediately (there should neither be a splash screen nor other startup tasks)
        context.applicationRunning();

        // check TCP first, takes precedence over pipe because it implies multiple clients
        var port = readPortFromEnv();
        if (port != null) {
            return launchTcpBasedMultiServer("localhost", port.intValue());
        }

        // no TCP so let's check for pipe
        var pipe = readPipeFromEnv();
        if (pipe != null) {
            return launchPipeBasedSingleServer(pipe);
        }

        LOG.error("Neither port nor pipe specified! STDIN/OUT not supported.");
        forceShutdown();
        return 1;
    }

    @Override
    public void stop() {
        stopSignal.countDown();
    }

}
