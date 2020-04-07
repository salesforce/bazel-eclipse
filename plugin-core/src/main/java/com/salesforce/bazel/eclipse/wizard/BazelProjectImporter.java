package com.salesforce.bazel.eclipse.wizard;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.salesforce.bazel.eclipse.abstractions.WorkProgressMonitor;
import com.salesforce.bazel.eclipse.config.BazelEclipseProjectFactory;
import com.salesforce.bazel.eclipse.model.BazelPackageLocation;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseWorkProgressMonitor;

/**
 * Imports projects with a Progress Dialog.  This is used by the Import Wizard and the ProjectView machinery.
 */
public class BazelProjectImporter {
    
    public static void run(BazelPackageLocation workspaceRootProject, List<BazelPackageLocation> bazelPackagesToImport) {
        WorkProgressMonitor progressMonitor = new EclipseWorkProgressMonitor(null);
        IRunnableWithProgress op = new IRunnableWithProgress() {
            @Override
            public void run(IProgressMonitor monitor) {
                try {
                    BazelEclipseProjectFactory.importWorkspace(workspaceRootProject, bazelPackagesToImport, progressMonitor,
                        monitor);
                } catch (Exception e) {
                    e.printStackTrace();
                    openError("Error", e);
                }
            }
        };

        try {
            new ProgressMonitorDialog(new Shell()).run(true, true, op);
        } catch (InvocationTargetException e) {
            openError("Error", e.getTargetException());
        } catch (Exception e) {
            openError("Error", e);
        }
    }
    
    private static void openError(String title, Throwable ex) {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                MessageDialog.openError(new Shell(), title, ex.getMessage());
            }
        });
    }
}
