/*-
 *
 */
package com.salesforce.bazel.eclipse.core.classpath;

import static java.nio.file.Files.isRegularFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.launching.RuntimeClasspathEntry;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.IRuntimeClasspathEntryResolver;
import org.eclipse.jdt.launching.IRuntimeClasspathEntryResolver2;
import org.eclipse.jdt.launching.IVMInstall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.idea.blaze.base.command.buildresult.LocalFileOutputArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.model.primitives.Label;
import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.BazelCorePlugin;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.discovery.JavaAspectsClasspathInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.JavaAspectsInfo;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects;
import com.salesforce.bazel.sdk.command.BazelBuildWithIntelliJAspectsCommand;

@SuppressWarnings("restriction")
public class BazelClasspathContainerRuntimeResolver
        implements IRuntimeClasspathEntryResolver, IRuntimeClasspathEntryResolver2 {

    private static Logger LOG = LoggerFactory.getLogger(BazelClasspathContainerRuntimeResolver.class);

    ISchedulingRule getBuildRule() {
        return ResourcesPlugin.getWorkspace().getRuleFactory().buildRule();
    }

    BazelClasspathManager getClasspathManager() {
        return BazelCorePlugin.getInstance().getBazelModelManager().getClasspathManager();
    }

    private IPath guessSrcJarLocation(LocalFileOutputArtifact localJar) {
        var directory = localJar.getPath().getParent();
        var jarName = localJar.getPath().getFileName().toString();

        var srcJar = directory.resolve(jarName.replace(".jar", "-sources.jar"));
        if (isRegularFile(srcJar)) {
            return IPath.fromPath(srcJar);
        }

        srcJar = directory.resolve(jarName.replace(".jar", "-src.jar"));
        if (isRegularFile(srcJar)) {
            return IPath.fromPath(srcJar);
        }

        LOG.warn(
            "Unable to guess source jar for '{}'. Please check configuration if source download is disabled. ",
            localJar);

        return null;
    }

    @Override
    public boolean isVMInstallReference(IClasspathEntry entry) {
        return false;
    }

    private Label readTargetLabel(LocalFileOutputArtifact localJar) {
        try (var jarFile = new JarFile(localJar.getPath().toFile())) {
            var manifest = jarFile.getManifest();
            var mainAttributes = manifest.getMainAttributes();
            if (mainAttributes != null) {
                var targetLabel = mainAttributes.getValue("Target-Label");
                if (targetLabel != null) {
                    return Label.createIfValid(targetLabel);
                }
            }
        } catch (IOException e) {
            LOG.warn("Error inspecting manifest of jar '{}': {}", localJar, e.getMessage());
        }
        return null;
    }

    @Override
    public IRuntimeClasspathEntry[] resolveRuntimeClasspathEntry(IRuntimeClasspathEntry entry, IJavaProject project)
            throws CoreException {
        if ((entry == null) || (entry.getJavaProject() == null)) {
            return new IRuntimeClasspathEntry[0];
        }

        if ((entry.getType() != IRuntimeClasspathEntry.CONTAINER)
                || !BazelClasspathHelpers.isBazelClasspathContainer(entry.getPath())) {
            return new IRuntimeClasspathEntry[0];
        }

        //        var bazelContainer = getClasspathManager().getSavedContainer(project.getProject());
        //        if (bazelContainer != null) {
        //            JavaCore.setClasspathContainer(
        //                containerPath,
        //                new IJavaProject[] { project },
        //                new IClasspathContainer[] { bazelContainer },
        //                new NullProgressMonitor());
        //        }

        var bazelProject = BazelCore.create(project.getProject());
        if (bazelProject.isWorkspaceProject()) {
            // no runtime classpath here
            return new IRuntimeClasspathEntry[0];
        }

        var bazelWorkspace = bazelProject.getBazelWorkspace();

        // get list of targets from project
        List<BazelTarget> targets;
        if (bazelProject.isPackageProject()) {
            targets = bazelProject.getBazelTargets();
        } else {
            targets = List.of(bazelProject.getBazelTarget());
        }

        // run the aspect to compute all required information
        var workspaceRoot = bazelWorkspace.getLocation().toPath();
        var outputGroups = Set.of(IntellijAspects.OUTPUT_GROUP_JAVA_RUNTIME_CLASSPATH);
        var aspects = bazelWorkspace.getParent().getModelManager().getIntellijAspects();
        var command = new BazelBuildWithIntelliJAspectsCommand(
                workspaceRoot,
                targets.stream().map(BazelTarget::getLabel).toList(),
                outputGroups,
                aspects,
                "Running build with IntelliJ aspects to collect classpath information");

        var bepOutput = bazelWorkspace.getCommandExecutor()
                .runWithWorkspaceLock(command, getBuildRule(), Collections.emptyList());
        var aspectsInfo = new JavaAspectsInfo(bepOutput, bazelWorkspace);
        var classpathInfo = new JavaAspectsClasspathInfo(aspectsInfo, bazelWorkspace);
        List<IRuntimeClasspathEntry> result = new ArrayList<>();
        for (OutputArtifact jar : bepOutput
                .getOutputGroupArtifacts(IntellijAspects.OUTPUT_GROUP_JAVA_RUNTIME_CLASSPATH)) {
            if (jar instanceof LocalFileOutputArtifact localJar) {
                var targetLabel = readTargetLabel(localJar);
                if (targetLabel != null) {
                    var projectEntry = classpathInfo.resolveProject(targetLabel);
                    if (projectEntry != null) {
                        result.add(new RuntimeClasspathEntry(JavaCore.newProjectEntry(projectEntry.getPath())));
                        continue;
                    }
                }
                var srcJarLocation = guessSrcJarLocation(localJar);
                result.add(
                    new RuntimeClasspathEntry(
                            JavaCore.newLibraryEntry(IPath.fromPath(localJar.getPath()), srcJarLocation, null)));
            }
        }

        return result.toArray(new IRuntimeClasspathEntry[result.size()]);
    }

    @Override
    public IRuntimeClasspathEntry[] resolveRuntimeClasspathEntry(IRuntimeClasspathEntry entry,
            ILaunchConfiguration configuration) throws CoreException {
        if ((entry == null) || (entry.getJavaProject() == null)) {
            return new IRuntimeClasspathEntry[0];
        }

        return resolveRuntimeClasspathEntry(entry, entry.getJavaProject());
    }

    @Override
    public IVMInstall resolveVMInstall(IClasspathEntry entry) throws CoreException {
        return null;
    }

}
