package com.salesforce.bazel.eclipse.core.model.discovery;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BAZEL_NATURE_ID;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.classpath.BazelClasspathScope;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelProjectFileSystemMapper;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.discovery.JavaInfo.FileEntry;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects.OutputGroup;
import com.salesforce.bazel.sdk.command.BazelBuildWithIntelliJAspectsCommand;
import com.salesforce.bazel.sdk.command.buildresults.OutputArtifact;
import com.salesforce.bazel.sdk.primitives.LanguageClass;

/**
 * Default implementation of {@link TargetProvisioningStrategy} which provisions a single project per supported target.
 *
 * @since 2.0
 */
public class ProjectPerTargetProvisioningStrategy implements TargetProvisioningStrategy {

    private static Logger LOG = LoggerFactory.getLogger(ProjectPerTargetProvisioningStrategy.class);

    public static final String STRATEGY_NAME = "project-per-target";

    private volatile BazelProjectFileSystemMapper fileSystemMapper;

    protected JavaInfo collectJavaInfo(BazelTarget target, IProgressMonitor monitor) throws CoreException {
        var javaInfo = new JavaInfo(target.getBazelPackage(), target.getBazelWorkspace());

        var attributes = target.getRuleAttributes();
        var srcs = attributes.getStringList("srcs");
        if (srcs != null) {
            for (String src : srcs) {
                javaInfo.addSrc(src);
            }
        }

        var deps = attributes.getStringList("deps");
        if (deps != null) {
            for (String dep : deps) {
                javaInfo.addDep(dep);
            }
        }

        var runtimeDeps = attributes.getStringList("runtime_deps");
        if (runtimeDeps != null) {
            for (String dep : runtimeDeps) {
                javaInfo.addRuntimeDep(dep);
            }
        }

        // analyze for recommended project setup
        var recommendations = javaInfo.analyzeProjectRecommendations(monitor);

        // TODO: create project level markers?

        return javaInfo;
    }

    @Override
    public List<ClasspathEntry> computeClasspath(BazelProject bazelProject, BazelClasspathScope scope,
            IProgressMonitor monitor) throws CoreException {

        var bazelWorkspace = bazelProject.getBazelWorkspace();
        var workspaceRoot = bazelWorkspace.getLocation().toFile().toPath();

        if (bazelProject.isWorkspaceProject()) {
            // FIXME: implement support for reading all jars from WORKSPACE
            //
            // For example:
            //   1. get list of all external repos
            //      > bazel query "//external:*"
            //   2. query for java rules for each external repo
            //      > bazel query "kind('java_.* rule', @evernal_repo_name//...)"
            //
            return List.of();
        }
        if (!bazelProject.isSingleTargetProject()) {
            throw new CoreException(Status.error(format(
                "Unable to compute classpath for project '%s'. Please check the setup. This is not a Bazel target project created by the project per target strategy.",
                bazelProject)));
        }
        var targets = List.of(bazelProject.getBazelTarget().getLabel());
        var onlyDirectDeps = bazelWorkspace.getBazelProjectView().deriveTargetsFromDirectories();
        var outputGroups = Set.of(OutputGroup.INFO, OutputGroup.RESOLVE);
        var languages = Set.of(LanguageClass.JAVA);
        var aspects = bazelWorkspace.getParent().getModelManager().getIntellijAspects();
        var command = new BazelBuildWithIntelliJAspectsCommand(workspaceRoot, targets, outputGroups, aspects, languages,
                onlyDirectDeps);

        var executionService = bazelWorkspace.getParent().getModelManager().getExecutionService();
        var result = executionService.executeWithWorkspaceLock(command, bazelWorkspace,
            List.of(bazelProject.getProject()), monitor);

        if (LOG.isDebugEnabled()) {
            result.getOutputGroupArtifacts(OutputGroup.RESOLVE::isPrefixOf)
                    .forEach(o -> LOG.debug("Resolve Output: {}", o));
            result.getOutputGroupArtifacts(OutputGroup.INFO::isPrefixOf).forEach(o -> LOG.debug("Info Output: {}", o));
        }

        var outputArtifacts = result.getOutputGroupArtifacts(OutputGroup.INFO::isPrefixOf,
            IntellijAspects.ASPECT_OUTPUT_FILE_PREDICATE);
        for (OutputArtifact outputArtifact : outputArtifacts) {
            try {
                var info = aspects.readAspectFile(outputArtifact.getPath());
                if (info.hasJavaIdeInfo()) {
                    var javaIdeInfo = info.getJavaIdeInfo();
                    var allFields = javaIdeInfo.getAllFields();
                    //LOG.debug("Help: {}", allFields);
                }
            } catch (IOException e) {
                throw new CoreException(Status
                        .error(format("Unable to compute classpath for project '%s'. Error reading aspect file '%s'.",
                            bazelProject, outputArtifact), e));
            }
        }

        // convert the logical entries into concrete Eclipse entries
        //        List<IClasspathEntry> entries = new ArrayList<>();
        //        for (JvmClasspathEntry entry : jcmClasspathData.jvmClasspathEntries) {
        //            if (entry.pathToJar != null) {
        //                var jarPath = getAbsoluteLocation(entry.pathToJar);
        //                if (jarPath != null) {
        //                    // srcJarPath must be relative to the workspace, by order of Eclipse
        //                    var srcJarPath = getAbsoluteLocation(entry.pathToSourceJar);
        //                    IPath srcJarRootPath = null;
        //                    entries.add(newLibraryEntry(jarPath, srcJarPath, srcJarRootPath, entry.isTestJar));
        //                }
        //            } else {
        //                entries.add(newProjectEntry(entry.bazelProject));
        //            }
        //        }

        return null;

    }

    private void configureClasspath(BazelTarget target, BazelProject project, JavaInfo javaInfo,
            IProgressMonitor progress) {
        // TODO Auto-generated method stub

    }

    protected void createFolderAndParents(final IContainer folder, IProgressMonitor progress) throws CoreException {
        var monitor = SubMonitor.convert(progress, 2);
        try {
            if ((folder == null) || folder.exists()) {
                return;
            }

            if (!folder.getParent().exists()) {
                createFolderAndParents(folder.getParent(), monitor.newChild(1));
            }
            switch (folder.getType()) {
                case IResource.FOLDER:
                    ((IFolder) folder).create(IResource.NONE, true, monitor.newChild(1));
                    break;
                default:
                    throw new CoreException(Status.error(format("Cannot create resource '%s'", folder)));
            }
        } finally {
            progress.done();
        }
    }

    protected void deleteAllFilesNotInAllowList(IFolder folder, Set<IFile> allowList, IProgressMonitor progress)
            throws CoreException {
        try {
            Set<IFile> toRemove = new HashSet<>();
            folder.accept(r -> {
                if ((r.getType() == IResource.FILE) && !allowList.contains(r)) {
                    toRemove.add((IFile) r);
                }
                return true;
            });

            if (toRemove.isEmpty()) {
                return;
            }

            var monitor = SubMonitor.convert(progress, toRemove.size());
            for (IFile file : toRemove) {
                file.delete(true, monitor.newChild(1));
            }
        } finally {
            progress.done();
        }
    }

    protected IWorkspace getEclipseWorkspace() {
        return ResourcesPlugin.getWorkspace();
    }

    protected IWorkspaceRoot getEclipseWorkspaceRoot() {
        return ResourcesPlugin.getWorkspace().getRoot();
    }

    /**
     * @return the {@link BazelProjectFileSystemMapper} (only set during provisioning of a single target)
     */
    protected BazelProjectFileSystemMapper getFileSystemMapper() {
        return requireNonNull(fileSystemMapper,
            "file system mapper not initialized, check code flow/implementation (likely a bug)");
    }

    private void linkJavaSourcesIntoProject(BazelTarget target, BazelProject project, JavaInfo javaInfo,
            IProgressMonitor progress) throws CoreException {
        var monitor = SubMonitor.convert(progress);
        try {
            if (javaInfo.hasSourceFilesWithoutCommonRoot()) {
                var virtualSourceFolder = getFileSystemMapper().getVirtualSourceFolder(project);
                if (!virtualSourceFolder.exists()) {
                    virtualSourceFolder.create(IResource.NONE, true, monitor.newChild(1));
                }
                var files = javaInfo.getSourceFilesWithoutCommonRoot();
                Set<IFile> linkedFiles = new HashSet<>();
                for (FileEntry fileEntry : files) {
                    // peek at Java package to find proper "root"
                    var packagePath = fileEntry.getDetectedPackagePath();
                    var packageFolder = virtualSourceFolder.getFolder(packagePath);
                    if (!packageFolder.exists()) {
                        createFolderAndParents(packageFolder, monitor.newChild(1));
                    }

                    // create link to file
                    var file = packageFolder.getFile(fileEntry.getPath().lastSegment());
                    file.createLink(fileEntry.getLocation(), IResource.REPLACE, monitor.newChild(1));
                    file.setPersistentProperty(BazelProject.PROJECT_PROPERTY_TARGETS, target.getLabel().toString());
                    file.setPersistentProperty(BazelProject.PROJECT_PROPERTY_WORKSPACE_ROOT,
                        target.getBazelWorkspace().getLocation().toString());

                    // remember for cleanup
                    linkedFiles.add(file);
                }

                // remove all files not created as part of this loop
                deleteAllFilesNotInAllowList(virtualSourceFolder, linkedFiles, monitor.newChild(1));
            }

            if (javaInfo.hasSourceDirectories()) {
                var directories = javaInfo.getSourceDirectories();
                NEXT_FOLDER: for (FileEntry dir : directories) {
                    var sourceFolder = project.getProject().getFolder(dir.getPath());
                    if (sourceFolder.exists() && !sourceFolder.isLinked()) {
                        // check if there is any linked parent we can remove
                        var parent = sourceFolder.getParent();
                        while ((parent != null) && (parent.getType() != IResource.PROJECT)) {
                            if (parent.isLinked()) {
                                parent.delete(true, monitor.newChild(1));
                                break;
                            }
                            parent = parent.getParent();
                        }
                        if (sourceFolder.exists()) {
                            // TODO create problem marker
                            continue NEXT_FOLDER;
                        }
                    }

                    // ensure the parent exists
                    if (!sourceFolder.getParent().exists()) {
                        createFolderAndParents(sourceFolder.getParent(), monitor.newChild(1));
                    }

                    // create link to folder
                    sourceFolder.createLink(dir.getLocation(), IResource.REPLACE, monitor.newChild(1));
                }
            }
        } finally {
            progress.done();
        }
    }

    protected BazelProject provisionJavaBinaryProject(BazelTarget target, IProgressMonitor progress)
            throws CoreException {

        // TODO: create a shared launch configuration

        return provisionJavaLibraryProject(target, progress);
    }

    protected BazelProject provisionJavaImportProject(BazelTarget target, IProgressMonitor progress)
            throws CoreException {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Provisions a Java project for the specified {@link BazelTarget}
     *
     * @param target
     *            the <code>java_library</code> target
     * @param progress
     *            monitor for reporting progress and tracking cancellation
     * @return the provisioned project
     * @throws CoreException
     */
    protected BazelProject provisionJavaLibraryProject(BazelTarget target, IProgressMonitor progress)
            throws CoreException {
        var monitor = SubMonitor.convert(progress, format("Provision project for target %s", target.getLabel()), 4);
        try {
            var project = provisionTargetProject(target, monitor.newChild(1));

            // build the Java information
            var javaInfo = collectJavaInfo(target, monitor.newChild(1));

            // configure links
            linkJavaSourcesIntoProject(target, project, javaInfo, monitor.newChild(1));

            // configure classpath
            configureClasspath(target, project, javaInfo, monitor.newChild(1));

            return project;
        } finally {
            progress.done();
        }
    }

    protected BazelProject provisionProjectForTarget(BazelTarget target, SubMonitor monitor) throws CoreException {
        var ruleName = target.getRuleClass();
        return switch (ruleName) {
            case "java_library": {
                yield provisionJavaLibraryProject(target, monitor);
            }
            case "java_import": {
                yield provisionJavaImportProject(target, monitor);
            }
            case "java_binary": {
                yield provisionJavaBinaryProject(target, monitor);
            }
            default: {
                LOG.debug("{}: Skipping provisioning due to unsupported rule '{}'.", target, ruleName);
                yield null;
            }
        };
    }

    @Override
    public List<BazelProject> provisionProjectsForTarget(Collection<BazelTarget> targets, IProgressMonitor progress)
            throws CoreException {
        try {
            var monitor = SubMonitor.convert(progress, "Provisioning projects", targets.size());
            List<BazelProject> result = new ArrayList<>();
            for (BazelTarget target : targets) {
                monitor.subTask(target.getLabel().toString());

                // ensure there is a mapper
                if (fileSystemMapper == null) {
                    fileSystemMapper = new BazelProjectFileSystemMapper(target.getBazelWorkspace());
                }

                // provision project
                var project = provisionProjectForTarget(target, monitor.newChild(1));
                if (project != null) {
                    result.add(project);
                }
            }

            // after provisioning we go over the projects a second time to
            // populate all projects with links and configure the classpath
            for (BazelProject bazelProject : result) {
                computeClasspath(bazelProject, BazelClasspathScope.DEFAULT_CLASSPATH, monitor.newChild(1));
            }

            // done
            return result;
        } finally {
            progress.done();
            fileSystemMapper = null; // reset mapper
        }
    }

    protected BazelProject provisionTargetProject(BazelTarget target, IProgressMonitor progress) throws CoreException {
        var monitor = SubMonitor.convert(progress, 4);
        try {
            if (target.hasBazelProject()) {
                return target.getBazelProject();
            }

            var label = target.getLabel();
            var projectName = format("%s:%s", label.getPackagePath().replace('/', '.'), label.getTargetName());
            var projectDescription = getEclipseWorkspace().newProjectDescription(projectName);

            // place the project into the Bazel workspace project area
            var projectLocation = getFileSystemMapper().getProjectsArea().append(projectName);
            projectDescription.setLocation(projectLocation);
            projectDescription.setComment("Bazel Target Project managed by Bazel Eclipse Feature");

            // create project
            var project = getEclipseWorkspaceRoot().getProject(projectName);
            project.create(projectDescription, monitor.newChild(1));

            project.open(monitor.newChild(1));

            // set natures separately in order to ensure they are configured properly
            projectDescription = project.getDescription();
            projectDescription.setNatureIds(new String[] { JavaCore.NATURE_ID, BAZEL_NATURE_ID });
            project.setDescription(projectDescription, monitor.newChild(1));

            // set properties
            var workspaceRoot = target.getBazelWorkspace().getLocation();
            project.setPersistentProperty(BazelProject.PROJECT_PROPERTY_WORKSPACE_ROOT, workspaceRoot.toString());
            project.setPersistentProperty(BazelProject.PROJECT_PROPERTY_TARGETS, label.toString());

            // this call is no longer expected to fail now (unless we need to poke the element info cache manually here)
            return target.getBazelProject();
        } finally {
            progress.done();
        }
    }

}
