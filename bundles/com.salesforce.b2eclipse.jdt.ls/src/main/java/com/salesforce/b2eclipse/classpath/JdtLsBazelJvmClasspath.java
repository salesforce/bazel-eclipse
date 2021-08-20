package com.salesforce.b2eclipse.classpath;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.salesforce.b2eclipse.BazelJdtPlugin;
import com.salesforce.b2eclipse.BazelNature;
import com.salesforce.b2eclipse.util.BazelEclipseProjectUtils;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.aspect.jvm.JVMAspectOutputJarSet;
import com.salesforce.bazel.sdk.aspect.jvm.JVMAspectTargetInfo;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.lang.jvm.BazelJvmClasspathResponse;
import com.salesforce.bazel.sdk.lang.jvm.JvmClasspath;
import com.salesforce.bazel.sdk.lang.jvm.JvmClasspathEntry;
import com.salesforce.bazel.sdk.model.BazelBuildFile;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.project.BazelProjectTargets;

import org.eclipse.core.resources.IProject;

public class JdtLsBazelJvmClasspath implements JvmClasspath {
	private static final String ASPECT_PACKAGE_KIND_JAVA_TEST = "java_test";
	private static final List<String> EXCLUDE_FROM_CLASSPATH_ASPECTS = Arrays.asList("java_library", "java_binary",
			"java_test");
	private final IProject eclipseProject;
	private final BazelProject bazelProject;
	private final BazelProjectManager bazelProjectManager;
	private final JdtLsImplicitClasspathHelper implicitClasspathHelper;
	private final BazelJvmClasspathResponse EMPTY_RESPONSE = new BazelJvmClasspathResponse();

	public JdtLsBazelJvmClasspath(IProject eclipseProject, BazelProject bazelProject,
			BazelProjectManager bazelProjectManager) {
		super();
		this.eclipseProject = eclipseProject;
		this.bazelProject = bazelProject;
		this.bazelProjectManager = bazelProjectManager;
		this.implicitClasspathHelper = new JdtLsImplicitClasspathHelper();
	}

	public void clean() {
	}

	@Override
	public BazelJvmClasspathResponse getClasspathEntries() {
		synchronized (this) {
			if (this.eclipseProject.getName().startsWith(BazelNature.BAZELWORKSPACE_PROJECT_BASENAME)) {
				return EMPTY_RESPONSE;
			}
			List<JvmClasspathEntry> classpathEntries = new ArrayList<>();

			try {
				BazelProjectTargets configuredTargetsForProject = bazelProjectManager
						.getConfiguredBazelTargets(bazelProject, false);

				BazelBuildFile bazelBuildFile = getBazelBuildFile(configuredTargetsForProject);

				List<JVMAspectTargetInfo> jvmAspectTargetInfos = getJvmAspectInfos(configuredTargetsForProject,
						bazelBuildFile);

				List<JvmClasspathEntry> librariesClasspath = calculateBazelClasspath(jvmAspectTargetInfos);
				List<BazelProject> projectsDependencies = calculateProjectClasspathDependencies(jvmAspectTargetInfos);
				List<JvmClasspathEntry> projectsClasspath = projectsDependencies
						.stream()
						.map(project -> new JvmClasspathEntry(project)).
						collect(Collectors.toList());

				Set<JvmClasspathEntry> implicitClasspath = calculateImplicitDependencies(jvmAspectTargetInfos);

				classpathEntries.addAll(librariesClasspath);
				classpathEntries.addAll(projectsClasspath);
				classpathEntries.addAll(implicitClasspath);

				BazelJvmClasspathResponse response = new BazelJvmClasspathResponse();
				response.jvmClasspathEntries = classpathEntries.toArray(JvmClasspathEntry[]::new);
				response.classpathProjectReferences.clear();
				response.classpathProjectReferences.addAll(projectsDependencies);

				return response;
			} catch (Exception exc) {
				BazelJdtPlugin.logException(
						"Unable to compute classpath containers entries for project " + bazelProject.name, exc);
			}

		}
		return EMPTY_RESPONSE;
	}

	private Set<JvmClasspathEntry> calculateImplicitDependencies(List<JVMAspectTargetInfo> jvmAspectTargetInfos) {
		Set<JvmClasspathEntry> implicitDependensies = jvmAspectTargetInfos //
				.stream() //
				.filter(aspectTargetInfo -> ASPECT_PACKAGE_KIND_JAVA_TEST.equals(aspectTargetInfo.getKind())) //
				.map(aspectTargetInfo -> implicitClasspathHelper
						.computeImplicitDependencies(BazelJdtPlugin.getBazelWorkspace(), aspectTargetInfo)) //
				.flatMap(Collection::stream) //
				.collect(Collectors.toSet()); //
		return implicitDependensies;
	}

	private List<BazelProject> calculateProjectClasspathDependencies(List<JVMAspectTargetInfo> jvmAspectTargetInfos) {
		Set<IProject> projectDependencies = BazelEclipseProjectUtils.computeProjectDependencies(eclipseProject, jvmAspectTargetInfos);
		List<BazelProject> bazelProjects = projectDependencies.stream()
				.map(project ->  bazelProjectManager.getProject(project.getName()))
				.collect(Collectors.toList());
		return bazelProjects;
	}

	private List<JVMAspectTargetInfo> getJvmAspectInfos(BazelProjectTargets configuredTargetsForProject,
			BazelBuildFile bazelBuildFile)
			throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
		Set<String> actualActivatedTargets = configuredTargetsForProject.getActualTargets(bazelBuildFile);

		Map<BazelLabel, Set<AspectTargetInfo>> targetLabelToAspectTargetInfos = BazelJdtPlugin
				.getWorkspaceCommandRunner().getAspectTargetInfos(actualActivatedTargets, "getClasspathEntries");
		return targetLabelToAspectTargetInfos.values() // values from map
				.stream() // stream to process
				.flatMap(Collection::stream) // convert to flat map
				.filter(JVMAspectTargetInfo.class::isInstance) // filter {@link JVMAspectTargetInfo} only
				.map(JVMAspectTargetInfo.class::cast) // cast to {@link JVMAspectTargetInfo}
				.collect(Collectors.toList()); // return as list
	}

	private BazelBuildFile getBazelBuildFile(BazelProjectTargets configuredTargetsForProject) throws Exception {
		// we pass the targets that are configured for the current project to bazel
		// query
		// typically, this is a single wildcard target, but the user may
		// also have specified explicit targets to use
		List<BazelLabel> labels = configuredTargetsForProject.getConfiguredTargets().stream().map(BazelLabel::new)
				.collect(Collectors.toList());
		Collection<BazelBuildFile> buildFiles = BazelJdtPlugin.getWorkspaceCommandRunner()
				.queryBazelTargetsInBuildFile(labels);
		// since we only call query with labels for the same package, we expect to get a
		// single BazelBuildFile instance back
		if (buildFiles.isEmpty()) {
			throw new IllegalStateException("Unexpected empty BazelBuildFile collection, this is a bug");
		} else if (buildFiles.size() > 1) {
			throw new IllegalStateException("Expected a single BazelBuildFile instance, this is a bug");
		} else {
			return buildFiles.iterator().next();
		}
	}

	private List<JvmClasspathEntry> calculateBazelClasspath(Collection<JVMAspectTargetInfo> aspectTargetInfos) {
		BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = BazelJdtPlugin.getBazelCommandManager()
				.getWorkspaceCommandRunner(BazelJdtPlugin.getBazelWorkspace());
		// filter-out main modules and
		List<JvmClasspathEntry> classpathEntries = aspectTargetInfos.stream() //
				.filter((JVMAspectTargetInfo info) -> !EXCLUDE_FROM_CLASSPATH_ASPECTS.contains(info.getKind()))//
				.map(JVMAspectTargetInfo::getJars) //
				.flatMap(List::stream) //
				.map((JVMAspectOutputJarSet jars) -> jarsToClasspathEntry(bazelWorkspaceCmdRunner, jars, false)) //
				.filter(Objects::nonNull) //
				.collect(Collectors.toList()); //

		aspectTargetInfos.stream()//
				.map(JVMAspectTargetInfo::getGeneratedJars) //
				.flatMap(List::stream) //
				.map((JVMAspectOutputJarSet jars) -> jarsToClasspathEntry(bazelWorkspaceCmdRunner, jars, false)) //
				.filter(Objects::nonNull) //
				.forEachOrdered(classpathEntries::add); //

		return classpathEntries;
	}

	private JvmClasspathEntry jarsToClasspathEntry(BazelWorkspaceCommandRunner bazelCommandRunner,
			JVMAspectOutputJarSet jarSet, boolean isTestLib) {
		JvmClasspathEntry cpEntry = null;
		File bazelOutputBase = bazelCommandRunner.computeBazelWorkspaceOutputBase();
		File bazelExecRoot = bazelCommandRunner.computeBazelWorkspaceExecRoot();
		String jarPath = getJarPathOnDisk(bazelOutputBase, bazelExecRoot, jarSet.getJar());
		if (jarPath != null) {
			String srcJarPath = getJarPathOnDisk(bazelOutputBase, bazelExecRoot, jarSet.getSrcJar());
			cpEntry = new JvmClasspathEntry(jarPath, srcJarPath, false);
		}
		return cpEntry;
	}

	private String getJarPathOnDisk(File bazelOutputBase, File bazelExecRoot, String file) {
		if (file == null) {
			return null;
		}
		Path path = null;
		if (file.startsWith("external")) {
			path = Paths.get(bazelOutputBase.toString(), file);
		} else {
			path = Paths.get(bazelExecRoot.toString(), file);
		}

		// We have had issues with Eclipse complaining about symlinks in the Bazel
		// output directories not being real,
		// so we resolve them before handing them back to Eclipse.
		if (Files.isSymbolicLink(path)) {
			try {
				// resolving the link will fail if the symlink does not a point to a real file
				path = Files.readSymbolicLink(path);
			} catch (IOException ex) {
				BazelJdtPlugin.logError("Problem adding jar to project [" + eclipseProject.getName()
						+ "] because it does not exist on the filesystem: " + path);
			}
		} else // it is a normal path, check for existence
		if (!Files.exists(path)) {
			BazelJdtPlugin.logError("Problem adding jar to project [" + eclipseProject.getName()
					+ "] because it does not exist on the filesystem: " + path);
		}

		return path.toString();
	}

}
