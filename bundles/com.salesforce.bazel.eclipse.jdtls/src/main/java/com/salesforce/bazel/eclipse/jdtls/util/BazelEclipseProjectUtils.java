package com.salesforce.bazel.eclipse.jdtls.util;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.component.EclipseBazelWorkspaceContext;
import com.salesforce.bazel.eclipse.core.resources.BazelNature;
import com.salesforce.bazel.eclipse.jdtls.config.BazelEclipseProjectSupport;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;

public class BazelEclipseProjectUtils {
    private static Logger LOG = LoggerFactory.getLogger(BazelEclipseProjectUtils.class);

    public static Set<IProject> calculateProjectReferences(IProject eclipseProject) {
        try {
            if (eclipseProject.getName().startsWith(BazelNature.BAZELWORKSPACE_PROJECT_BASENAME)) {
                return Collections.emptySet();
            }
            var bazelWorkspace = EclipseBazelWorkspaceContext.getInstance().getBazelWorkspace();
            var bazelWorkspaceCmdRunner =
                    ComponentContext.getInstance().getBazelCommandManager().getWorkspaceCommandRunner(bazelWorkspace);
            var bazelTargetsForProject =
                    BazelEclipseProjectSupport.getBazelTargetsForEclipseProject(eclipseProject, false);

            var aspectTargets =
                    bazelWorkspaceCmdRunner.getAspectTargetInfos(bazelTargetsForProject, "calculateProjectReferences");
            List<AspectTargetInfo> packageInfos =
                    aspectTargets.values().stream().flatMap(Collection::stream).collect(Collectors.toList());

            return computeProjectDependencies(eclipseProject, packageInfos);

        } catch (BazelCommandLineToolConfigurationException e) {
            LOG.error("Bazel not found", e);
        } catch (IOException | InterruptedException e) {
            LOG.error("Unable to compute classpath containers entries for project {}", e, eclipseProject.getName());
        }
        return Collections.emptySet();
    }

    public static Set<IProject> computeProjectDependencies(IProject eclipseProject,
            List<? extends AspectTargetInfo> packageInfos) {
        Set<IProject> projectDependencies = new HashSet<>();
        for (AspectTargetInfo packageInfo : packageInfos) {
            var otherProject = ClasspathUtils.getSourceProjectForSourcePaths(packageInfo.getSources());

            if ((otherProject != null)
                    && eclipseProject.getProject().getFullPath().equals(otherProject.getProject().getFullPath())) {
                continue;
            }
            if (otherProject != null) {
                // now make a project reference between this project and the other project; this
                // allows for features like
                // code refactoring across projects to work correctly
                projectDependencies.add(otherProject.getProject());
            }
        }
        return projectDependencies;
    }

}
