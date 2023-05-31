package com.salesforce.bazel.eclipse.core.model.discovery;

import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;

import com.salesforce.bazel.eclipse.core.classpath.BazelClasspathScope;
import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.discovery.JavaProjectInfo.FileEntry;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * Legacy implementation of {@link TargetProvisioningStrategy} which provisions a project for all targets in the same
 * package.
 * <p>
 * This strategy implements the BEF behavior in versions 1.x.
 * <ul>
 * <li>All <code>java_*</code> targets in the same package are merged into a single Eclipse project.</li>
 * <li>The build path is merged so Eclipse does not have proper visibility in potentially unsupported imports.</li>
 * <li>Projects are created directly in the package location.</li>
 * <li>The root (empty) package <code>//</code> is not supported.</li>
 * </ul>
 * </p>
 */
public class ProjectPerPackageProvisioningStrategy extends BaseProvisioningStrategy {

    public static final String STRATEGY_NAME = "project-per-package";

    @Override
    public List<ClasspathEntry> computeClasspath(BazelProject bazelProject, BazelClasspathScope scope,
            IProgressMonitor monitor) throws CoreException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected List<BazelProject> doProvisionProjects(Collection<BazelTarget> targets, IProgressMonitor progress)
            throws CoreException {
        try {
            // group into packages
            Map<BazelPackage, List<BazelTarget>> targetsByPackage =
                    targets.stream().filter(this::isSupported).collect(groupingBy(BazelTarget::getBazelPackage));

            var monitor = SubMonitor.convert(progress, "Provisioning projects", targetsByPackage.size() * 3);

            var result = new ArrayList<BazelProject>();
            for (Entry<BazelPackage, List<BazelTarget>> entry : targetsByPackage.entrySet()) {
                var bazelPackage = entry.getKey();
                var packageTargets = entry.getValue();

                // create the project for the package
                var project = provisionPackageProject(bazelPackage, packageTargets, monitor.newChild(1));

                // build the Java information
                var javaInfo = collectJavaInfo(project, packageTargets, monitor.newChild(1));

                // sanity check
                if (javaInfo.hasSourceFilesWithoutCommonRoot()) {
                    for (FileEntry file : javaInfo.getSourceFilesWithoutCommonRoot()) {
                        createBuildPathProblem(project, Status.warning(format(
                            "File '%s' could not be mapped into a common source directory. The project may not build successful in Eclipse.",
                            file.getPath())));
                    }
                }
                if (!javaInfo.hasSourceDirectories()) {
                    createBuildPathProblem(project,
                        Status.error(format(
                            "No source directories detected when analyzihng package '%s' using targets '%s'",
                            bazelPackage.getLabel().getPackagePath(), packageTargets.stream().map(BazelTarget::getLabel)
                                    .map(BazelLabel::getLabelPath).collect(joining(", ")))));
                }

                // configure classpath
                configureRawClasspath(project, javaInfo, monitor.newChild(1));

                result.add(project);
            }
            return result;
        } finally {
            progress.done();
        }
    }

    private boolean isSupported(BazelTarget bazeltarget) {
        String ruleName;
        try {
            ruleName = bazeltarget.getRuleClass();
        } catch (CoreException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return switch (ruleName) {
            case "java_library", "java_import", "java_binary": {
                yield true;

            }
            default: {
                yield false;
            }
        };
    }

    protected BazelProject provisionPackageProject(BazelPackage bazelPackage, List<BazelTarget> targets,
            IProgressMonitor progress) throws CoreException {
        if (bazelPackage.hasBazelProject()) {
            return bazelPackage.getBazelProject();
        }

        var label = bazelPackage.getLabel();
        var projectName = label.getPackagePath().replace('/', '.');

        // create the project directly within the package (note, there can be at most one project per package with this strategy anyway)
        var projectLocation = bazelPackage.getLocation();

        createProjectForElement(projectName, projectLocation, bazelPackage, progress);

        // this call is no longer expected to fail now (unless we need to poke the element info cache manually here)
        return bazelPackage.getBazelProject();
    }

}
