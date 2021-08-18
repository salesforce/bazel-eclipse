package com.salesforce.b2eclipse.util;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.salesforce.b2eclipse.BazelJdtPlugin;
import com.salesforce.b2eclipse.BazelNature;
import com.salesforce.b2eclipse.config.BazelEclipseProjectSupport;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.model.BazelLabel;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;

public class BazelEclipseProjectUtils {

	public static Set<IProject> calculateProjectReferences(IProject eclipseProject) {
		try {
			if (eclipseProject.getName().startsWith(BazelNature.BAZELWORKSPACE_PROJECT_BASENAME)) {
				return Collections.emptySet();
			}
			BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = BazelJdtPlugin.getBazelCommandManager()
					.getWorkspaceCommandRunner(BazelJdtPlugin.getBazelWorkspace());
			List<String> bazelTargetsForProject = BazelEclipseProjectSupport
					.getBazelTargetsForEclipseProject(eclipseProject, false);

			Map<BazelLabel, Set<AspectTargetInfo>> aspectTargets = bazelWorkspaceCmdRunner
					.getAspectTargetInfos(bazelTargetsForProject, "calculateProjectReferences");
			List<AspectTargetInfo> packageInfos = aspectTargets.values().stream().flatMap(Collection::stream)
					.collect(Collectors.toList());

			return computeProjectDependencies(eclipseProject, packageInfos);

		} catch (BazelCommandLineToolConfigurationException e) {
			BazelJdtPlugin.logError("Bazel not found: " + e.getMessage());
		} catch (IOException | InterruptedException e) {
			BazelJdtPlugin.logException(
					"Unable to compute classpath containers entries for project " + eclipseProject.getName(), e);
		}
		return Collections.emptySet();
	}

	public static Set<IProject> computeProjectDependencies(IProject eclipseProject,
			List<? extends AspectTargetInfo> packageInfos) {
		Set<IProject> projectDependencies = new HashSet<IProject>();
		for (AspectTargetInfo packageInfo : packageInfos) {
			IJavaProject otherProject = ClasspathUtils.getSourceProjectForSourcePaths(packageInfo.getSources());

			if (otherProject != null
					&& eclipseProject.getProject().getFullPath().equals(otherProject.getProject().getFullPath())) {
				continue;
			} else if (otherProject != null) {
				// now make a project reference between this project and the other project; this
				// allows for features like
				// code refactoring across projects to work correctly
				projectDependencies.add(otherProject.getProject());
			}
		}
		return projectDependencies;
	}

}
