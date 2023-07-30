/*-
 *
 */
package com.salesforce.bazel.eclipse.jdtls.commands;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.BazelCorePlugin;
import com.salesforce.bazel.eclipse.core.classpath.InitializeOrRefreshClasspathJob;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.SynchronizeProjectViewJob;

/**
 * Bazel JDT LS Commands
 */
@SuppressWarnings("restriction")
public class BazelJdtLsDelegateCommandHandler implements IDelegateCommandHandler {

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
                            projects.toArray(new IProject[projects.size()]),
                            BazelCorePlugin.getInstance().getBazelModelManager().getClasspathManager(),
                            true /* force */).schedule();
                    return new Object();
                case "java.bazel.syncProjects":
                    var workspaces = BazelCore.getModel().getWorkspaces();
                    for (BazelWorkspace workspace : workspaces) {
                        new SynchronizeProjectViewJob(workspace).schedule();
                    }
                    return new Object();
                default:
                    break;
            }
        }
        throw new UnsupportedOperationException(
                String.format("Bazel JDT LS extension doesn't support the command '%s'.", commandId));
    }

}
