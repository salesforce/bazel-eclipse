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
package com.salesforce.bazel.scipls;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * The SCIP bases Java & Bazel Language Server
 */
public class ScipBazelJavaLanguageServer implements LanguageServer, LanguageClientAware {

    @FunctionalInterface
    public interface ExitHandler {
        void exitCalled(ScipBazelJavaLanguageServer languageServer);
    }

    private final ExitHandler exitHandler;
    private final AtomicReference<LanguageClient> client = new AtomicReference<>();

    /**
     * Creates a new language server instance.
     *
     * @param exitHandler
     *            called by {@link #exit()} to trigger system specific process shutdown
     */
    public ScipBazelJavaLanguageServer(ExitHandler exitHandler) {
        this.exitHandler = requireNonNull(exitHandler);
    }

    @Override
    public void connect(LanguageClient client) {
        if (!this.client.compareAndSet(null, client)) {
            throw new IllegalStateException("Invalid concurrent use. Client must only be set once!");
        }
    }

    @Override
    public void exit() {
        exitHandler.exitCalled(this);
    }

    private LanguageClient getClient() {
        return requireNonNull(client.getPlain(), "No Language Client connected!");
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Called by the external application runtime when a shutdown is in progress.
     * <p>
     * The idea is to inform any active client of the upcoming shutdown to allow clients to discover the situation. It
     * should not be part of the regular flow.
     * </p>
     */
    public void notifyClientOfShutdown() {
        getClient().logMessage(
            new MessageParams(
                    MessageType.Warning,
                    "The language server is being shut down forcefully. The client will be disconnect. Certain functionallity may become unavilable until restored."));
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        // TODO Auto-generated method stub
        return null;
    }

}
