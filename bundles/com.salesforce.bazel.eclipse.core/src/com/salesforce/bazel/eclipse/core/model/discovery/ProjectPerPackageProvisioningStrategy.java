package com.salesforce.bazel.eclipse.core.model.discovery;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.classpath.BazelClasspathScope;
import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * Legacy implementation of {@link TargetProvisioningStrategy} which provisions a project for all targets in the same
 * package.
 * <p>
 * This strategy implements the BEF behavior in versions 1.x.
 * </p>
 */
public class ProjectPerPackageProvisioningStrategy extends BaseProvisioningStrategy {

    private static Logger LOG = LoggerFactory.getLogger(ProjectPerPackageProvisioningStrategy.class);

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

            var monitor = SubMonitor.convert(progress, "Provisioning projects", targetsByPackage.size() * 2);

            var result = new ArrayList<BazelProject>();
            for (Entry<BazelPackage, List<BazelTarget>> entry : targetsByPackage.entrySet()) {
                var bazelPackage = entry.getKey();
                var packageTargets = entry.getValue();

                // create the project for the package
                var project = provisionPackageProject(bazelPackage, packageTargets, monitor.newChild(1));

                // build the Java information
                var javaInfo = collectJavaInfo(packageTargets, project, monitor.newChild(1));

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
            var project = bazelPackage.getBazelProject();

            // update the list of targets
            project.getProject().setPersistentProperty(BazelProject.PROJECT_PROPERTY_TARGETS,
                targets.stream().map(BazelTarget::getLabel).map(BazelLabel::getLabelPath).collect(joining(",")));

            return project;
        }

        var label = bazelPackage.getLabel();
        var projectName = label.getPackagePath().replace('/', '.');

        var project = createProjectForElement(projectName, bazelPackage, progress);
        project.setPersistentProperty(BazelProject.PROJECT_PROPERTY_TARGETS,
            targets.stream().map(BazelTarget::getLabel).map(BazelLabel::getLabelPath).collect(joining(",")));

        // this call is no longer expected to fail now (unless we need to poke the element info cache manually here)
        return bazelPackage.getBazelProject();
    }

}
