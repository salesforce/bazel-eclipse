/*-
 *  Copyright (c) 2000 IBM Corporation and others.
 *
 *  This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License 2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-2.0/
 *
 *  SPDX-License-Identifier: EPL-2.0
 *
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *     Salesforce - copied and adapted from JDT SharedJavaMainTab and BazelTargetTab
 */
package com.salesforce.bazel.eclipse.ui.launchconfiguration;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.internal.ui.SWTFactory;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jface.fieldassist.AutoCompleteField;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.idea.blaze.base.model.primitives.Label;
import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.launchconfiguration.BazelLaunchConfigurationConstants;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.ui.BazelUIPlugin;
import com.salesforce.bazel.eclipse.ui.utils.BazelProjectUtilitis;
import com.salesforce.bazel.sdk.model.BazelLabel;

@SuppressWarnings("restriction")
public class BazelTargetTab extends AbstractLaunchConfigurationTab {

    final class TargetProposalRefreshJob extends Job {
        final BazelProject bazelProject;

        private TargetProposalRefreshJob(BazelProject bazelProject) {
            super("Refresh Proposal");
            this.bazelProject = bazelProject;
            setSystem(true);
        }

        @Override
        protected IStatus run(IProgressMonitor monitor) {
            if ((fProjText == null) || fProjText.isDisposed()) {
                return Status.CANCEL_STATUS;
            }

            SortedSet<String> proposals = new TreeSet<>();
            try {
                // all binaries from the current project
                if (bazelProject.isTargetProject()) {
                    var bazelTarget = bazelProject.getBazelTarget();
                    if (isPublicBinaryTarget(bazelTarget)) {
                        proposals.add(bazelTarget.getLabel().toString());
                    }
                } else if (bazelProject.isPackageProject()) {
                    for (BazelTarget bazelTarget : bazelProject.getBazelTargets()) {
                        if (isPublicBinaryTarget(bazelTarget)) {
                            proposals.add(bazelTarget.getLabel().toString());
                        }
                    }
                }

                // also all binaries from the workspace project
                var bazelWorkspace = bazelProject.getBazelWorkspace();
                var targets = bazelWorkspace.getBazelPackage(IPath.EMPTY).getBazelTargets();
                for (BazelTarget bazelTarget : targets) {
                    if (isPublicBinaryTarget(bazelTarget)) {
                        proposals.add(bazelTarget.getLabel().toString());
                    }
                }

                // all in-memory targets as well
                for (BazelTarget bazelTarget : bazelWorkspace.getAllOpenTargets()) {
                    if (isPublicBinaryTarget(bazelTarget)) {
                        proposals.add(bazelTarget.getLabel().toString());
                    }
                }
            } catch (CoreException e) {
                // ignore
            }

            if (!fProjText.getDisplay().isDisposed()) {
                fProjText.getDisplay().asyncExec(() -> {
                    targetAutoCompleteField.setProposals(proposals.toArray(new String[proposals.size()]));
                });
            }

            return Status.OK_STATUS;
        }
    }

    /**
     * A listener which handles widget change events for the controls in this tab.
     */
    private class WidgetListener implements ModifyListener, SelectionListener {

        @Override
        public void modifyText(ModifyEvent e) {
            updateLaunchConfigurationDialog();
        }

        @Override
        public void widgetDefaultSelected(SelectionEvent e) {/*do nothing*/}

        @Override
        public void widgetSelected(SelectionEvent e) {
            var source = e.getSource();
            if (source == fProjButton) {
                handleProjectButtonSelected();
            } else {
                updateLaunchConfigurationDialog();
            }
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(BazelTargetTab.class);

    protected static final String EMPTY_STRING = "";

    protected Text fProjText;
    private Button fProjButton;

    protected Text fMainText;

    private Button attachJavaDebuggerCheckButton;

    private final WidgetListener fListener = new WidgetListener();

    private AutoCompleteField targetAutoCompleteField;

    private TargetProposalRefreshJob targetProposalRefreshJob;

    /**
     * chooses a project for the type of java launch config that it is
     *
     * @return the selected project or <code>null</code> if none
     */
    private IJavaProject chooseWorkspaceProject() {
        ILabelProvider labelProvider = new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT);
        var dialog = new ElementListSelectionDialog(getShell(), labelProvider);
        dialog.setTitle("Project Selection");
        dialog.setMessage("Select a project to constrain your search.");
        try {
            List<IJavaProject> workspaceProjects = new ArrayList<>();
            for (IJavaProject javaProject : JavaCore.create(getWorkspaceRoot()).getJavaProjects()) {
                if (BazelProject.isBazelProject(javaProject.getProject())
                        && BazelCore.create(javaProject.getProject()).isWorkspaceProject()) {
                    workspaceProjects.add(javaProject);
                }
            }
            dialog.setElements(workspaceProjects.toArray());
        } catch (CoreException jme) {
            LOG.error("Error selecting project.", jme);
        }
        var javaProject = getJavaProject();
        if (javaProject != null) {
            dialog.setInitialSelections(javaProject);
        }
        if (dialog.open() == Window.OK) {
            return (IJavaProject) dialog.getFirstResult();
        }
        return null;
    }

    @Override
    public void createControl(Composite parent) {
        var comp = SWTFactory.createComposite(parent, parent.getFont(), 1, 1, GridData.FILL_BOTH);
        ((GridLayout) comp.getLayout()).verticalSpacing = 0;
        createProjectEditor(comp);
        createVerticalSpacer(comp, 1);
        createMainTypeEditor(comp, "Target:");
        setControl(comp);
    }

    /**
     * Creates the widgets for specifying a main type.
     *
     * @param parent
     *            the parent composite
     */
    protected void createMainTypeEditor(Composite parent, String text) {
        var group = SWTFactory.createGroup(parent, text, 2, 1, GridData.FILL_HORIZONTAL);
        fMainText = SWTFactory.createSingleText(group, 1);
        fMainText.addModifyListener(e -> updateLaunchConfigurationDialog());

        targetAutoCompleteField = new AutoCompleteField(fMainText, new TextContentAdapter());

        createMainTypeExtensions(group);
    }

    /**
     * This method allows the group for main type to be extended with custom controls. All control added via this method
     * come after the main type text editor and search button in the order they are added to the parent composite
     *
     * @param parent
     *            the parent to add to
     * @since 3.3
     */
    protected void createMainTypeExtensions(Composite parent) {
        attachJavaDebuggerCheckButton = SWTFactory.createCheckButton(parent, "Attach Java debugger", null, false, 1);
        attachJavaDebuggerCheckButton.addSelectionListener(getDefaultListener());
    }

    /**
     * Creates the widgets for specifying a main type.
     *
     * @param parent
     *            the parent composite
     */
    protected void createProjectEditor(Composite parent) {
        var group = SWTFactory.createGroup(parent, "Project:", 2, 1, GridData.FILL_HORIZONTAL);

        fProjText = SWTFactory.createSingleText(group, 1);
        fProjText.addModifyListener(fListener);
        fProjButton = createPushButton(group, "Browse...", null);
        fProjButton.addSelectionListener(fListener);
    }

    private BazelLabel findFirstPublicBinaryRule(List<BazelTarget> bazelTargets) throws CoreException {
        for (BazelTarget bazelTarget : bazelTargets) {
            if (isPublicBinaryTarget(bazelTarget)) {
                return bazelTarget.getLabel();
            }
        }
        return null;
    }

    private BazelProject getContext() {
        var projects = BazelProjectUtilitis.findSelectedProjects(PlatformUI.getWorkbench().getActiveWorkbenchWindow());
        for (IProject project : projects) {
            return BazelCore.create(project); // first project wins
        }
        return null;
    }

    /**
     * returns the default listener from this class. For all subclasses this listener will only provide the
     * functionality of updating the current tab
     *
     * @return a widget listener
     */
    protected WidgetListener getDefaultListener() {
        return fListener;
    }

    @Override
    public String getId() {
        return "com.salesforce.bazel.eclipse.ui.launchconfiguration.tabs.target";
    }

    @Override
    public Image getImage() {
        return BazelUIPlugin.getDefault().getImageRegistry().get(BazelUIPlugin.ICON_BAZEL);
    }

    /**
     * Convenience method to get access to the java model.
     */
    private IJavaModel getJavaModel() {
        return JavaCore.create(getWorkspaceRoot());
    }

    /**
     * Return the IJavaProject corresponding to the project name in the project name text field, or null if the text
     * does not match a project name.
     */
    protected IJavaProject getJavaProject() {
        var projectName = fProjText.getText().trim();
        if (projectName.length() < 1) {
            return null;
        }
        return getJavaModel().getJavaProject(projectName);
    }

    @Override
    public String getName() {
        return "Bazel";
    }

    /**
     * Convenience method to get the workspace root.
     */
    protected IWorkspaceRoot getWorkspaceRoot() {
        return ResourcesPlugin.getWorkspace().getRoot();
    }

    /**
     * Show a dialog that lets the user select a project. This in turn provides context for the main type, allowing the
     * user to key a main type name, or constraining the search for main types to the specified project.
     */
    protected void handleProjectButtonSelected() {
        var project = chooseWorkspaceProject();
        if (project == null) {
            return;
        }
        var projectName = project.getElementName();
        fProjText.setText(projectName);
    }

    @Override
    protected void initializeAttributes() {
        getAttributesLabelsForPrototype()
                .put(BazelLaunchConfigurationConstants.PROJECT_NAME, "Bazel Workspace Project");
        getAttributesLabelsForPrototype().put(BazelLaunchConfigurationConstants.TARGET_LABEL, "Target");
        getAttributesLabelsForPrototype().put(BazelLaunchConfigurationConstants.JAVA_DEBUG, "Attach Java Debugger");
    }

    @Override
    public void initializeFrom(ILaunchConfiguration config) {
        updateProjectFromConfig(config);
        updateMainTypeFromConfig(config);
        updateStopInMainFromConfig(config);
    }

    /**
     * Sets the Java project attribute on the given working copy to the Java project associated with the given Java
     * element.
     *
     * @param bazelProject
     *            Java model element this tab is associated with
     * @param config
     *            configuration on which to set the Java project attribute
     */
    protected void initializeProject(BazelProject bazelProject, ILaunchConfigurationWorkingCopy config) {
        var project = bazelProject.getProject();
        String name = null;
        if ((project != null) && project.exists()) {
            name = project.getName();
        }
        config.setAttribute(BazelLaunchConfigurationConstants.PROJECT_NAME, name);
    }

    /**
     * Set the main type & name attributes on the working copy based on the IJavaElement
     */
    protected void initializeTargetAndName(BazelProject bazelProject, ILaunchConfigurationWorkingCopy config) {
        BazelLabel target = null;
        try {
            if (bazelProject.isTargetProject()) {
                target = bazelProject.getBazelTarget().getLabel();
            } else if (bazelProject.isPackageProject()) {
                target = findFirstPublicBinaryRule(bazelProject.getBazelTargets());
            } else if (bazelProject.isWorkspaceProject()) {
                var rootPackage = bazelProject.getBazelWorkspace().getBazelPackage(IPath.EMPTY);
                target = findFirstPublicBinaryRule(rootPackage.getBazelTargets());
            }
        } catch (CoreException e) {
            LOG.error("Error initializing target: {}", e.getMessage(), e);
        }

        var name = target == null ? EMPTY_STRING : target.toString();
        config.setAttribute(BazelLaunchConfigurationConstants.TARGET_LABEL, name);
        config.setAttribute(BazelLaunchConfigurationConstants.JAVA_DEBUG, true);
        if (target != null) {
            // use just the targetName
            name = getLaunchConfigurationDialog().generateName(target.getTargetName());
            config.rename(name);
        }
    }

    private boolean isPublicBinaryTarget(BazelTarget bazelTarget) throws CoreException {
        return bazelTarget.getRuleClass().endsWith("_binary") && bazelTarget.isVisibleToIde()
                && bazelTarget.getVisibility().isPublic();
    }

    @Override
    public boolean isValid(ILaunchConfiguration config) {
        setErrorMessage(null);
        setWarningMessage(null);
        setMessage(null);
        var name = fProjText.getText().trim();
        if (name.length() <= 0) {
            setErrorMessage("Project not specified");
            return false;
        }
        var workspace = ResourcesPlugin.getWorkspace();
        var status = workspace.validateName(name, IResource.PROJECT);
        if (!status.isOK()) {
            setErrorMessage(NLS.bind("Illegal project name: {0}", new String[] { status.getMessage() }));
            return false;
        }
        var project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
        if (!project.exists()) {
            setErrorMessage(NLS.bind("Project {0} does not exist", new String[] { name }));
            return false;
        }
        if (!project.isOpen()) {
            setErrorMessage(NLS.bind("Project {0} is closed", new String[] { name }));
            return false;
        }
        if (!BazelProject.isBazelProject(project)) {
            setErrorMessage(NLS.bind("Project {0} is not a Bazel project!", new String[] { name }));
            return false;
        }

        var bazelProject = BazelCore.create(project);
        refreshTargetPropsals(bazelProject);

        name = fMainText.getText().trim();
        if (name.length() == 0) {
            setErrorMessage("Target not specified");
            return false;
        }
        var labelError = Label.validate(name);
        if (labelError != null) {
            setErrorMessage(NLS.bind("Illegal target: {0}", new String[] { labelError }));
            return false;
        }
        var label = new BazelLabel(name);
        try {
            var target = bazelProject.getBazelWorkspace().getBazelPackage(label).getBazelTarget(label.getTargetName());
            if (!target.exists()) {
                setErrorMessage(NLS.bind("Target {0} does not exist", new String[] { name }));
                return false;
            }

            var ruleClass = target.getRuleClass();
            if ((ruleClass != null) && !ruleClass.endsWith("_binary")) {
                setWarningMessage(format("Target '%s' doesn't look like a binary rule. Launch may fail.", name));
            }
        } catch (CoreException e) {
            setErrorMessage(NLS.bind("Error resolving target: {0}", new String[] { e.getStatus().getMessage() }));
            return false;
        }
        return true;
    }

    /**
     * Maps the config to associated java resource
     *
     * @param config
     */
    protected void mapResources(ILaunchConfigurationWorkingCopy config) {
        var javaProject = getJavaProject();
        if ((javaProject != null) && javaProject.exists() && javaProject.isOpen()) {
            config.setMappedResources(new IResource[] { javaProject.getProject() });
        }
    }

    @Override
    public void performApply(ILaunchConfigurationWorkingCopy config) {
        config.setAttribute(BazelLaunchConfigurationConstants.PROJECT_NAME, fProjText.getText().trim());
        config.setAttribute(BazelLaunchConfigurationConstants.TARGET_LABEL, fMainText.getText().trim());
        config.setAttribute(BazelLaunchConfigurationConstants.JAVA_DEBUG, attachJavaDebuggerCheckButton.getSelection());
        mapResources(config);
    }

    private void refreshTargetPropsals(BazelProject bazelProject) {
        if (targetProposalRefreshJob != null) {
            if (targetProposalRefreshJob.bazelProject.equals(bazelProject)) {
                return; // project did not change
            }
            targetProposalRefreshJob.cancel();
        }
        targetProposalRefreshJob = new TargetProposalRefreshJob(bazelProject);
        targetProposalRefreshJob.schedule();
    }

    @Override
    public void setDefaults(ILaunchConfigurationWorkingCopy config) {
        var bazelProject = getContext();
        if (bazelProject != null) {
            initializeProject(bazelProject, config);
        } else {
            config.setAttribute(BazelLaunchConfigurationConstants.PROJECT_NAME, EMPTY_STRING);
        }
        initializeTargetAndName(bazelProject, config);
    }

    /**
     * Loads the main type from the launch configuration's preference store
     *
     * @param config
     *            the config to load the main type from
     */
    protected void updateMainTypeFromConfig(ILaunchConfiguration config) {
        var mainTypeName = EMPTY_STRING;
        try {
            mainTypeName = config.getAttribute(BazelLaunchConfigurationConstants.TARGET_LABEL, EMPTY_STRING);
        } catch (CoreException ce) {
            LOG.error("Error reading launch config", ce);
        }
        fMainText.setText(mainTypeName);
    }

    /**
     * updates the project text field form the configuration
     *
     * @param config
     *            the configuration we are editing
     */
    private void updateProjectFromConfig(ILaunchConfiguration config) {
        var projectName = EMPTY_STRING;
        try {
            projectName = config.getAttribute(BazelLaunchConfigurationConstants.PROJECT_NAME, EMPTY_STRING);
        } catch (CoreException ce) {
            LOG.error("Error reading launch config", ce);
        }
        fProjText.setText(projectName);
    }

    /**
     * updates the stop in main attribute from the specified launch config
     *
     * @param config
     *            the config to load the stop in main attribute from
     */
    private void updateStopInMainFromConfig(ILaunchConfiguration config) {
        var stop = false;
        try {
            stop = config.getAttribute(BazelLaunchConfigurationConstants.JAVA_DEBUG, false);
        } catch (CoreException e) {
            LOG.error("Error reading launch config", e);
        }
        attachJavaDebuggerCheckButton.setSelection(stop);
    }

}