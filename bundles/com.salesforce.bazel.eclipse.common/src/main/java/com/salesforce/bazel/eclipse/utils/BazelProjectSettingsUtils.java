package com.salesforce.bazel.eclipse.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IProject;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

import com.salesforce.bazel.eclipse.config.IEclipseBazelProjectSettings;
import com.salesforce.bazel.eclipse.runtime.api.BaseResourceHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectTargets;

public class BazelProjectSettingsUtils {

    /**
     * List of Bazel build flags for this Eclipse project, taken from the project configuration
     */
    public static List<String> getBazelBuildFlagsForProject(BaseResourceHelper resourceHelper,
            BazelProject bazelProject) {
        IProject eclipseProject = (IProject) bazelProject.getProjectImpl();
        Preferences eclipseProjectBazelPrefs = resourceHelper.getProjectBazelPreferences(eclipseProject);

        List<String> listBuilder = new ArrayList<>();
        for (String property : getKeys(eclipseProjectBazelPrefs)) {
            if (property.startsWith(IEclipseBazelProjectSettings.BUILDFLAG_PROPERTY_PREFIX)) {
                listBuilder.add(eclipseProjectBazelPrefs.get(property, ""));
            }
        }
        return listBuilder;
    }

    /**
     * List the Bazel targets the user has chosen to activate for this Eclipse project. Each project configured for
     * Bazel is configured to track certain targets and this function fetches this list from the project preferences.
     * After initial import, this will be just the wildcard target (:*) which means all targets are activated. This is
     * the safest choice as new targets that are added to the BUILD file will implicitly get picked up. But users may
     * choose to be explicit if one or more targets in a BUILD file is not needed for development.
     * <p>
     * By contract, this method will return only one target if the there is a wildcard target, even if the user does
     * funny things in their prefs file and sets multiple targets along with the wildcard target.
     */
    public static BazelProjectTargets getConfiguredBazelTargets(BaseResourceHelper resourceHelper,
            BazelProject bazelProject, boolean addWildcardIfNoTargets) {
        IProject eclipseProject = (IProject) bazelProject.getProjectImpl();
        Preferences eclipseProjectBazelPrefs = resourceHelper.getProjectBazelPreferences(eclipseProject);
        String projectLabel = eclipseProjectBazelPrefs.get(IEclipseBazelProjectSettings.PROJECT_PACKAGE_LABEL, null);

        BazelProjectTargets activatedTargets = new BazelProjectTargets(bazelProject, projectLabel);

        boolean addedTarget = false;
        Set<String> activeTargets = new TreeSet<>();

        for (String propertyName : getKeys(eclipseProjectBazelPrefs)) {

            if (propertyName.startsWith(IEclipseBazelProjectSettings.TARGET_PROPERTY_PREFIX)) {
                String target = eclipseProjectBazelPrefs.get(propertyName, "");
                if (!target.isEmpty()) {
                    BazelLabel label = new BazelLabel(target);
                    if (!label.getLabelPath().startsWith(projectLabel)) {
                        // the user jammed in a label not associated with this project, ignore
                        //continue;
                    }
                    if (!label.isConcrete()) {
                        // we have a wildcard target, so discard any existing targets we gathered (if the user messed up their prefs)
                        // and just go with that.
                        activatedTargets.activateWildcardTarget(label.getTargetName());
                        return activatedTargets;
                    }
                    activeTargets.add(target);
                    addedTarget = true;
                }
            }
        }
        if (!addedTarget && addWildcardIfNoTargets) {
            activeTargets.add("//...");
        }

        activatedTargets.activateSpecificTargets(activeTargets);

        return activatedTargets;
    }

    private static String[] getKeys(Preferences prefs) {
        try {
            return prefs.keys();
        } catch (BackingStoreException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
