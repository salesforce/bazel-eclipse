/*-
 *
 */
package com.salesforce.bazel.eclipse.jdtls.commands;

import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.BazelCorePlugin;
import com.salesforce.bazel.eclipse.core.classpath.InitializeOrRefreshClasspathJob;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.SynchronizeProjectViewJob;
import com.salesforce.bazel.eclipse.jdtls.execution.StreamingSocketBazelCommandExecutor;

/**
 * Bazel JDT LS Commands
 */
@SuppressWarnings("restriction")
public class BazelJdtLsDelegateCommandHandler implements IDelegateCommandHandler {

    static class ReconnectingSocket implements Supplier<OutputStream> {

        private static Logger LOG = LoggerFactory.getLogger(BazelJdtLsDelegateCommandHandler.ReconnectingSocket.class);

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

    private static final AtomicReference<ReconnectingSocket> reconnectingSocketRef = new AtomicReference<>();

    @Override
    public Object executeCommand(String commandId, List<Object> arguments, IProgressMonitor monitor) throws Exception {
        if (commandId != null) {
            switch (commandId) {
                case "java.bazel.updateClasspaths":
                    var sourceFileUri = (String) arguments.get(0);
                    var containers = ResourcesPlugin.getWorkspace()
                            .getRoot()
                            .findContainersForLocationURI(new URI(sourceFileUri));
                    Set<IProject> projects = new HashSet<>();
                    for (IContainer container : containers) {
                        projects.add(container.getProject());
                    }
                    new InitializeOrRefreshClasspathJob(
                            projects,
                            BazelCorePlugin.getInstance().getBazelModelManager().getClasspathManager(),
                            true /* force */).schedule();
                    return new Object();
                case "java.bazel.syncProjects":
                    var workspaces = BazelCore.getModel().getBazelWorkspaces();
                    for (BazelWorkspace workspace : workspaces) {
                        new SynchronizeProjectViewJob(workspace).schedule();
                    }
                    return new Object();
                case "java.bazel.connectProcessStreamSocket":
                    var port = 0;
                    var portArg = arguments.get(0);
                    if (portArg instanceof Number) {
                        port = ((Number) portArg).intValue();
                    } else if (portArg instanceof String) {
                        port = Integer.parseInt((String) portArg);
                    }
                    if ((port > 0) && (port < 65535)) {
                        Integer staticPort = port;
                        var reconnectingSocket = new ReconnectingSocket(staticPort);
                        setReconnectingSocket(reconnectingSocket);
                        logInfo("Enabled Bazel command output streaming to port: " + port);
                        return Boolean.TRUE;
                    } else {
                        StreamingSocketBazelCommandExecutor.setLocalPortHostSupplier(null);
                        logInfo("Disabled Bazel command output streaming");
                        return Boolean.FALSE;
                    }
                default:
                    break;
            }
        }
        throw new UnsupportedOperationException(
                String.format("Bazel JDT LS extension doesn't support the command '%s'.", commandId));
    }

    private void setReconnectingSocket(ReconnectingSocket reconnectingSocket) {
        // switch to new
        StreamingSocketBazelCommandExecutor.setLocalPortHostSupplier(reconnectingSocket);

        // dispose old
        var old = reconnectingSocketRef.getAndSet(reconnectingSocket);
        if (old != null) {
            old.close();
        }
    }

}
