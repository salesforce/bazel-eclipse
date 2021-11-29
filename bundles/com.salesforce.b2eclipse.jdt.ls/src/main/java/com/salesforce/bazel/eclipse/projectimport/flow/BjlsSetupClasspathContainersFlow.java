package com.salesforce.bazel.eclipse.projectimport.flow;

import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IJavaProject;

import com.salesforce.b2eclipse.BazelJdtPlugin;
import com.salesforce.bazel.eclipse.BazelNature;
import com.salesforce.bazel.eclipse.classpath.EclipseSourceClasspathUtil;
import com.salesforce.bazel.eclipse.component.EclipseBazelComponentFacade;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfos;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.init.JvmRuleInit;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.project.structure.ProjectStructure;

public class BjlsSetupClasspathContainersFlow extends SetupClasspathContainersFlow {
    private static final LogHelper LOG = LogHelper.log(BjlsSetupClasspathContainersFlow.class);

    private static final String TEST_BIN_FOLDER = "/testbin";

    /** @see {@link org.eclipse.jdt.ls.core.internal.ProjectUtils#WORKSPACE_LINK} */
    @SuppressWarnings("restriction")
    public static final String WORKSPACE_LINK = "_";

    public BjlsSetupClasspathContainersFlow(BazelCommandManager commandManager, BazelProjectManager projectManager,
            ResourceHelper resourceHelper, JavaCoreHelper javaCoreHelper) {
        super(commandManager, projectManager, resourceHelper, javaCoreHelper);
    }

    @Override
    public void run(ImportContext ctx, SubMonitor progressSubMonitor) throws CoreException {
        Path bazelWorkspaceRootDirectory = new Path(ctx.getBazelWorkspaceRootDirectory().getAbsolutePath());
        List<IProject> importedProjects = ctx.getImportedProjects();
        for (IProject project : importedProjects) {
            BazelPackageLocation packageLocation = ctx.getPackageLocationForProject(project);
            ProjectStructure structure =
                    ctx.getProjectStructure(packageLocation, getBazelWorkspace(), getCommandManager());
            String packageFSPath = packageLocation.getBazelPackageFSRelativePath();
            IJavaProject javaProject = getJavaCoreHelper().getJavaProjectForProject(project);

            // create the source dirs classpath (adding each source directory to the cp, and adding the JDK); there is no
            // return value because the cp is set directly into the passed javaProject; this method also links in the
            // source directory IFolders into the project
            EclipseSourceClasspathUtil.createClasspath(bazelWorkspaceRootDirectory, packageFSPath, structure,
                javaProject, ctx.getJavaLanguageLevel(), getResourceHelper(), getJavaCoreHelper());

            buildWorkspaceLink(javaProject, bazelWorkspaceRootDirectory);
            buildBinLinks(ctx, javaProject);

            progressSubMonitor.worked(1);
        }
    }

    private void buildWorkspaceLink(IJavaProject eclipseJavaProject, IPath bazelWorkspacePath) {
        if (!eclipseJavaProject.getProject().getName().startsWith(BazelNature.BAZELWORKSPACE_PROJECT_BASENAME)) {
            IFolder linkHiddenFolder = eclipseJavaProject.getProject().getFolder(WORKSPACE_LINK);
            if (!linkHiddenFolder.exists()) {
                getResourceHelper().createFolderLink(linkHiddenFolder, bazelWorkspacePath, IResource.NONE, null);
            }
        }
    }

    private void buildBinLinks(ImportContext ctx, IJavaProject javaProject) {
        AspectTargetInfos aspects = ctx.getAspectTargetInfos();
        if (aspects != null) {
            for (AspectTargetInfo aspect : aspects.getTargetInfos()) {
                if (javaProject.getElementName().equals(aspect.getLabel().getPackageName())) {
                    boolean isModule = JvmRuleInit.KIND_JAVA_BINARY.getKindName().equals(aspect.getKind())
                            || JvmRuleInit.KIND_JAVA_LIBRARY.getKindName().equals(aspect.getKind());
                    boolean isTest = JvmRuleInit.KIND_JAVA_TEST.getKindName().equals(aspect.getKind());
                    if (isModule) {
                        buildBinLinkFolder(javaProject, aspect.getLabel());
                    }
                    if (isTest) {
                        buildTestBinLinkFolder(javaProject, aspect.getLabel());
                    }
                }
            }
        }
    }

    private void buildBinLinkFolder(IJavaProject eclipseJavaProject, BazelLabel bazelLabel) {
        String projectMainOutputPath =
                EclipseBazelComponentFacade.getInstance().getWorkspaceCommandRunner().getProjectOutputPath(bazelLabel);

        IPath projectOutputPath = Optional.ofNullable(projectMainOutputPath).map(Path::fromOSString).orElse(null);
        if (projectOutputPath != null) {
            try {
                BazelJdtPlugin.getResourceHelper().createFolderLink(eclipseJavaProject.getProject().getFolder("/bin"),
                    projectOutputPath, IResource.NONE, null);
            } catch (IllegalArgumentException e) {
                LOG.info("Folder link {} already exists", projectOutputPath);
            }
        }
    }

    private void buildTestBinLinkFolder(IJavaProject eclipseJavaProject, BazelLabel bazelLabel) {
        String projectTestOutputPath =
                EclipseBazelComponentFacade.getInstance().getWorkspaceCommandRunner().getProjectOutputPath(bazelLabel);
        IPath projectOutputPath = Optional.ofNullable(projectTestOutputPath).map(Path::fromOSString).orElse(null);
        if (projectOutputPath != null) {
            try {
                BazelJdtPlugin.getResourceHelper().createFolderLink(
                    eclipseJavaProject.getProject().getFolder(TEST_BIN_FOLDER), projectOutputPath, IResource.NONE,
                    null);
            } catch (IllegalArgumentException e) {
                LOG.info("Folder link {} already exists", projectOutputPath);
            }
        }
    }
}
