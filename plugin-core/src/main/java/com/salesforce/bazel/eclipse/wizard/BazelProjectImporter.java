package com.salesforce.bazel.eclipse.wizard;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.salesforce.bazel.eclipse.projectimport.ProjectImporter;
import com.salesforce.bazel.eclipse.projectimport.ProjectImporterFactory;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseWorkProgressMonitor;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;

/**
 * Imports projects with a Progress Dialog. This is used by the Import Wizard and the ProjectView machinery.
 */
public class BazelProjectImporter {

    public static void run(BazelPackageLocation workspaceRootProject,
            List<BazelPackageLocation> bazelPackagesToImport) {
        IRunnableWithProgress op = new IRunnableWithProgress() {
            @Override
            public void run(IProgressMonitor monitor) {
                ProjectImporterFactory importerFactory = new ProjectImporterFactory(workspaceRootProject, bazelPackagesToImport);
                ProjectImporter projectImporter = importerFactory.build();
                try {
                    WorkProgressMonitor progressMonitor = new EclipseWorkProgressMonitor(monitor);
                    projectImporter.run(progressMonitor, monitor);
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
                String exceptionMessage = ex.getMessage();
                if (exceptionMessage == null || exceptionMessage.isEmpty()) {
                    // Exception does not have a message, which usually means it is an NPE.
                    exceptionMessage = "An exception of type ["+ex.getClass().getName()+
                            "] was thrown, but no additional message details are available. "+
                            "Check the console window where you launched Eclipse, or Eclipse log for the full stack trace.";
                }
                MessageDialog.openError(new Shell(), title, exceptionMessage);
            }
        });
    }
}
