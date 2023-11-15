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

import static com.salesforce.bazel.eclipse.core.launchconfiguration.BazelLaunchConfigurationConstants.ADD_DEBUG_TARGET_ARG;
import static com.salesforce.bazel.eclipse.core.launchconfiguration.BazelLaunchConfigurationConstants.JAVA_DEBUG;
import static com.salesforce.bazel.eclipse.core.launchconfiguration.BazelLaunchConfigurationConstants.PROJECT_NAME;
import static java.lang.String.format;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_ALLOW_TERMINATE;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_CONNECT_MAP;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_VM_CONNECTOR;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ID_SOCKET_ATTACH_VM_CONNECTOR;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IVMConnector;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jface.action.LegacyActionTools;
import org.eclipse.jface.fieldassist.AutoCompleteField;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.statushandlers.StatusAdapter;
import org.eclipse.ui.statushandlers.StatusManager;
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
import com.sun.jdi.connect.Connector;

@SuppressWarnings("restriction")
public class BazelTargetTab extends AbstractLaunchConfigurationTab implements IPropertyChangeListener {

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

    private Composite fArgumentComposite;
    private Combo fConnectorCombo;

    private Button fAllowTerminateButton;

    // the selected connector
    private IVMConnector fConnector;
    private final IVMConnector[] fConnectors = JavaRuntime.getVMConnectors();

    private Map<String, Connector.Argument> fArgumentMap;
    private final Map<String, FieldEditor> fFieldEditorMap = new HashMap<>();

    private Button addDebugProgramArgumentButton;

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
        createTargetEditor(comp, "Target:");
        createVerticalSpacer(comp, 1);
        createJavaDebuggerEditor(comp);
        setControl(comp);
    }

    /**
     * This method allows the group for main type to be extended with custom controls. All control added via this method
     * come after the main type text editor and search button in the order they are added to the parent composite
     *
     * @param parent
     *            the parent to add to
     * @since 3.3
     */
    protected void createJavaDebuggerEditor(Composite parent) {

        //connection type
        var group = SWTFactory.createGroup(parent, "Java Remote Debugger", 1, 1, GridData.FILL_HORIZONTAL);

        attachJavaDebuggerCheckButton = createCheckButton(group, "Attach Java debugger to the launched JVM process");
        attachJavaDebuggerCheckButton.addSelectionListener(getDefaultListener());

        addDebugProgramArgumentButton = createCheckButton(group, "Add '--debug' argument when attaching debugger");
        addDebugProgramArgumentButton.addSelectionListener(getDefaultListener());

        var names = new String[fConnectors.length];
        for (var i = 0; i < fConnectors.length; i++) {
            names[i] = fConnectors[i].getName();
        }
        fConnectorCombo = SWTFactory.createCombo(group, SWT.READ_ONLY, 1, GridData.FILL_HORIZONTAL, names);
        fConnectorCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                handleConnectorComboModified();
            }
        });

        fArgumentComposite = SWTFactory.createComposite(group, parent.getFont(), 1, 1, GridData.FILL_HORIZONTAL);

        fAllowTerminateButton = createCheckButton(group, "Allow termination of the remote VM");
        fAllowTerminateButton.addSelectionListener(getDefaultListener());
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

    /**
     * Creates the widgets for specifying a main type.
     *
     * @param parent
     *            the parent composite
     */
    protected void createTargetEditor(Composite parent, String text) {
        var group = SWTFactory.createGroup(parent, text, 2, 1, GridData.FILL_HORIZONTAL);
        fMainText = SWTFactory.createSingleText(group, 1);
        fMainText.addModifyListener(e -> updateLaunchConfigurationDialog());

        targetAutoCompleteField = new AutoCompleteField(fMainText, new TextContentAdapter());
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
     * Returns the selected connector
     *
     * @return the selected {@link IVMConnector}
     */
    private IVMConnector getSelectedConnector() {
        return fConnector;
    }

    /**
     * Convenience method to get the workspace root.
     */
    protected IWorkspaceRoot getWorkspaceRoot() {
        return ResourcesPlugin.getWorkspace().getRoot();
    }

    /**
     * Update the argument area to show the selected connector's arguments
     */
    private void handleConnectorComboModified() {
        var index = fConnectorCombo.getSelectionIndex();
        if ((index < 0) || (index >= fConnectors.length)) {
            return;
        }
        var vm = fConnectors[index];
        if (vm.equals(fConnector)) {
            return; // selection did not change
        }
        fConnector = vm;
        try {
            fArgumentMap = vm.getDefaultArguments();
        } catch (CoreException e) {
            var status = new StatusAdapter(Status.error("Unable to display connection arguments.", e));
            StatusManager.getManager().handle(status, StatusManager.BLOCK | StatusManager.LOG);
            return;
        }

        // Dispose of any current child widgets in the tab holder area
        var children = fArgumentComposite.getChildren();
        for (Control child : children) {
            child.dispose();
        }
        fFieldEditorMap.clear();
        var store = new PreferenceStore();
        for (String key : vm.getArgumentOrder()) {
            var arg = fArgumentMap.get(key);
            FieldEditor field = null;
            if (arg instanceof Connector.IntegerArgument integerArg) {
                store.setDefault(arg.name(), integerArg.intValue());
                field = new IntegerFieldEditor(arg.name(), arg.label(), fArgumentComposite);
            } else if (arg instanceof Connector.SelectedArgument selectedArg) {
                var choices = selectedArg.choices();
                var namesAndValues = new String[choices.size()][2];
                var count = 0;
                for (String choice : choices) {
                    namesAndValues[count][0] = choice;
                    namesAndValues[count][1] = choice;
                    count++;
                }
                store.setDefault(arg.name(), arg.value());
                field = new ComboFieldEditor(arg.name(), arg.label(), namesAndValues, fArgumentComposite);
            } else if (arg instanceof Connector.StringArgument) {
                store.setDefault(arg.name(), arg.value());
                field = new StringFieldEditor(arg.name(), arg.label(), fArgumentComposite);
            } else if (arg instanceof Connector.BooleanArgument bool) {
                store.setDefault(arg.name(), bool.booleanValue());
                field = new BooleanFieldEditor(arg.name(), arg.label(), fArgumentComposite);
            }
            if (field != null) {
                field.setPreferenceStore(store);
                field.loadDefault();
                field.setPropertyChangeListener(this);
                fFieldEditorMap.put(key, field);
            }
        }
        fArgumentComposite.getParent().getParent().layout();
        fArgumentComposite.layout(true);
        updateLaunchConfigurationDialog();
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
        getAttributesLabelsForPrototype().put(PROJECT_NAME, "Bazel Workspace Project");
        getAttributesLabelsForPrototype().put(BazelLaunchConfigurationConstants.TARGET_LABEL, "Target");
        getAttributesLabelsForPrototype().put(JAVA_DEBUG, "Attach Java Debugger");
        getAttributesLabelsForPrototype().put(ADD_DEBUG_TARGET_ARG, "Add '--debug' when attaching debugger");
        getAttributesLabelsForPrototype().put(ATTR_ALLOW_TERMINATE, "Allow termination of the remote VM");
    }

    @Override
    public void initializeFrom(ILaunchConfiguration config) {
        updateProjectFromConfig(config);
        updateTargetFromConfig(config);
        updateJavaDebugConnectionFromConfig(config);
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
        config.setAttribute(PROJECT_NAME, name);
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

        for (String key : fFieldEditorMap.keySet()) {
            var arg = fArgumentMap.get(key);
            var editor = fFieldEditorMap.get(key);
            if (editor instanceof StringFieldEditor stringEditor) {
                var value = stringEditor.getStringValue();
                if (!arg.isValid(value)) {
                    var argLabel = new StringBuilder(LegacyActionTools.removeMnemonics(arg.label()));
                    if (argLabel.lastIndexOf(":") == (argLabel.length() - 1)) {
                        argLabel = argLabel.deleteCharAt(argLabel.length() - 1);
                    }
                    setErrorMessage(label.toString() + " is invalid.");
                    return false;
                }
            }
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
        config.setAttribute(PROJECT_NAME, fProjText.getText().trim());
        config.setAttribute(BazelLaunchConfigurationConstants.TARGET_LABEL, fMainText.getText().trim());
        config.setAttribute(JAVA_DEBUG, attachJavaDebuggerCheckButton.getSelection());
        config.setAttribute(ADD_DEBUG_TARGET_ARG, addDebugProgramArgumentButton.getSelection());
        config.setAttribute(ATTR_ALLOW_TERMINATE, fAllowTerminateButton.getSelection());
        mapResources(config);

        config.setAttribute(
            IJavaLaunchConfigurationConstants.ATTR_VM_CONNECTOR,
            getSelectedConnector().getIdentifier());
        Map<String, String> attrMap = new HashMap<>(fFieldEditorMap.size());
        for (String key : fFieldEditorMap.keySet()) {
            var editor = fFieldEditorMap.get(key);
            if (!editor.isValid()) {
                return;
            }
            var arg = fArgumentMap.get(key);
            editor.store();
            if ((arg instanceof Connector.StringArgument) || (arg instanceof Connector.SelectedArgument)) {
                attrMap.put(key, editor.getPreferenceStore().getString(key));
            } else if (arg instanceof Connector.BooleanArgument) {
                attrMap.put(key, Boolean.toString(editor.getPreferenceStore().getBoolean(key)));
            } else if (arg instanceof Connector.IntegerArgument) {
                attrMap.put(key, Integer.toString(editor.getPreferenceStore().getInt(key)));
            }
        }
        config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CONNECT_MAP, attrMap);

    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        updateLaunchConfigurationDialog();
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
            initializeTargetAndName(bazelProject, config);
        } else {
            config.setAttribute(PROJECT_NAME, EMPTY_STRING);
        }

        config.setAttribute(ATTR_VM_CONNECTOR, ID_SOCKET_ATTACH_VM_CONNECTOR);
        config.setAttribute(JAVA_DEBUG, true);
        config.setAttribute(ADD_DEBUG_TARGET_ARG, true);
        config.setAttribute(ATTR_ALLOW_TERMINATE, true);
        config.setAttribute(ATTR_CONNECT_MAP, Map.of("hostname", "localhost", "port", "5005"));
    }

    /**
     * Updates the connection argument field editors from the specified configuration
     *
     * @param config
     *            the config to load from
     */
    private void updateJavaDebugConnectionFromConfig(ILaunchConfiguration config) {
        try {
            attachJavaDebuggerCheckButton.setSelection(config.getAttribute(JAVA_DEBUG, true));
            addDebugProgramArgumentButton.setSelection(config.getAttribute(ADD_DEBUG_TARGET_ARG, true));
            fAllowTerminateButton.setSelection(config.getAttribute(ATTR_ALLOW_TERMINATE, true));

            var id = config.getAttribute(ATTR_VM_CONNECTOR, JavaRuntime.getDefaultVMConnector().getIdentifier());
            fConnectorCombo.setText(JavaRuntime.getVMConnector(id).getName());
            handleConnectorComboModified();

            var attrMap =
                    config.getAttribute(IJavaLaunchConfigurationConstants.ATTR_CONNECT_MAP, (Map<String, String>) null);
            if (attrMap == null) {
                return;
            }
            for (String key : attrMap.keySet()) {
                var arg = fArgumentMap.get(key);
                var editor = fFieldEditorMap.get(key);
                if ((arg != null) && (editor != null)) {
                    var value = attrMap.get(key);
                    if ((arg instanceof Connector.StringArgument) || (arg instanceof Connector.SelectedArgument)) {
                        editor.getPreferenceStore().setValue(key, value);
                    } else if (arg instanceof Connector.BooleanArgument) {
                        editor.getPreferenceStore().setValue(key, Boolean.parseBoolean(value));
                    } else if (arg instanceof Connector.IntegerArgument) {
                        editor.getPreferenceStore().setValue(key, Integer.parseInt(value));
                    }
                    editor.load();
                }
            }
        } catch (CoreException ce) {
            LOG.error("Unable to load connection info from config", ce);
        }
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
            projectName = config.getAttribute(PROJECT_NAME, EMPTY_STRING);
        } catch (CoreException ce) {
            LOG.error("Error reading launch config", ce);
        }
        fProjText.setText(projectName);
    }

    /**
     * Loads the main type from the launch configuration's preference store
     *
     * @param config
     *            the config to load the main type from
     */
    protected void updateTargetFromConfig(ILaunchConfiguration config) {
        var mainTypeName = EMPTY_STRING;
        try {
            mainTypeName = config.getAttribute(BazelLaunchConfigurationConstants.TARGET_LABEL, EMPTY_STRING);
        } catch (CoreException ce) {
            LOG.error("Error reading launch config", ce);
        }
        fMainText.setText(mainTypeName);
    }

}