package com.salesforce.bazel.eclipse.config;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.osgi.service.prefs.Preferences;

import com.salesforce.bazel.eclipse.activator.Activator;
import com.salesforce.bazel.eclipse.runtime.api.BaseResourceHelper;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.utils.BazelProjectSettingsUtils;
import com.salesforce.bazel.eclipse.utils.EclipseProjectSettingsUtils;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.project.BazelProjectTargets;

public class BaseEclipseBazelProjectManager extends BazelProjectManager {
    protected final BaseResourceHelper resourceHelper;
    protected final JavaCoreHelper javaCoreHelper;

    public BaseEclipseBazelProjectManager() {
        resourceHelper = Activator.getDefault().getResourceHelper();
        javaCoreHelper = Activator.getDefault().getJavaCoreHelper();
    }

    @Override
    public BazelProject getOwningProjectForSourcePath(BazelWorkspace bazelWorkspace, String sourcePath) {
        return EclipseProjectSettingsUtils.getOwningProjectForSourcePath(bazelWorkspace, sourcePath, getAllProjects(),
            resourceHelper, javaCoreHelper);
    }
    
    /**
     * The label that identifies the Bazel package that represents this Eclipse project. This will be the 'module' label
     * when we start supporting multiple BUILD files in a single 'module'. Example: //projects/libs/foo See
     * https://github.com/salesforce/bazel-eclipse/issues/24
     */
    @Override
    public String getBazelLabelForProject(BazelProject bazelProject) {
        IProject eclipseProject = (IProject) bazelProject.getProjectImpl();
        Preferences eclipseProjectBazelPrefs = resourceHelper.getProjectBazelPreferences(eclipseProject);
        return eclipseProjectBazelPrefs.get(IEclipseBazelProjectSettings.PROJECT_PACKAGE_LABEL, null);
    }

    /**
     * Returns a map that maps Bazel labels to their Eclipse projects
     */
    @Override
    public Map<BazelLabel, BazelProject> getBazelLabelToProjectMap(Collection<BazelProject> bazelProjects) {
        Map<BazelLabel, BazelProject> labelToProject = new HashMap<>();
        for (BazelProject bazelProject : bazelProjects) {
            BazelProjectTargets activatedTargets = getConfiguredBazelTargets(bazelProject, false);
            List<BazelLabel> labels =
                    activatedTargets.getConfiguredTargets().stream().map(BazelLabel::new).collect(Collectors.toList());
            for (BazelLabel label : labels) {
                labelToProject.merge(label, bazelProject, (k1, k2) -> {
                    throw new IllegalStateException("Duplicate label: " + label + " - this is bug");
                });
            }
        }
        return labelToProject;
    }

    @Override
    public BazelProjectTargets getConfiguredBazelTargets(BazelProject bazelProject, boolean addWildcardIfNoTargets) {
        return BazelProjectSettingsUtils.getConfiguredBazelTargets(resourceHelper, bazelProject,
            addWildcardIfNoTargets);
    }

    @Override
    public void addSettingsToProject(BazelProject bazelProject, String bazelWorkspaceRoot, String packageFSPath,
            List<BazelLabel> bazelTargets, List<String> bazelBuildFlags) {

        IProject eclipseProject = (IProject) bazelProject.getProjectImpl();
        Preferences eclipseProjectBazelPrefs = resourceHelper.getProjectBazelPreferences(eclipseProject);

        eclipseProjectBazelPrefs.put(IEclipseBazelProjectSettings.BAZEL_WORKSPACE_ROOT_ABSPATH_PROPERTY,
            bazelWorkspaceRoot);

        // convert file system path to bazel path; for linux/macos the slashes are already fine,
        // this is only a thing for windows
        String bazelPackagePath = packageFSPath.replace(FSPathHelper.WINDOWS_BACKSLASH, "/");

        if (!bazelPackagePath.startsWith("//")) {
            bazelPackagePath = "//" + bazelPackagePath;
        }
        eclipseProjectBazelPrefs.put(IEclipseBazelProjectSettings.PROJECT_PACKAGE_LABEL, bazelPackagePath);

        for (int i = 0; i < bazelTargets.size(); i++) {
            eclipseProjectBazelPrefs.put(IEclipseBazelProjectSettings.TARGET_PROPERTY_PREFIX + i,
                bazelTargets.get(i).getLabelPath());
        }
        for (int i = 0; i < bazelBuildFlags.size(); i++) {
            eclipseProjectBazelPrefs.put(IEclipseBazelProjectSettings.BUILDFLAG_PROPERTY_PREFIX + i,
                bazelBuildFlags.get(i));
        }
        try {
            eclipseProjectBazelPrefs.flush();
        } catch (Exception anyE) {
            throw new RuntimeException(anyE);
        }
    }

    /**
     * List of Bazel build flags for this Eclipse project, taken from the project configuration
     */
    @Override
    public List<String> getBazelBuildFlagsForProject(BazelProject bazelProject) {
        return BazelProjectSettingsUtils.getBazelBuildFlagsForProject(resourceHelper, bazelProject);
    }

    @Override
    public void setProjectReferences(BazelProject thisProject, List<BazelProject> updatedRefList) {
        EclipseProjectSettingsUtils.setProjectReferences(resourceHelper, thisProject, updatedRefList);
    }
}
