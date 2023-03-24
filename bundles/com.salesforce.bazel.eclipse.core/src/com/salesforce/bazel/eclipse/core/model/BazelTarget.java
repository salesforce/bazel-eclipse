package com.salesforce.bazel.eclipse.core.model;

import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * This class represents a target within a package (BUILD file).
 * <p>
 * See <a href="https://bazel.build/concepts/build-ref">Workspaces, packages, and targets</a> in the Bazel documentation
 * for further details.
 * </p>
 */
public final class BazelTarget extends BazelElement<BazelTargetInfo, BazelPackage> {

    private final BazelPackage bazelPackage;
    private final String targetName;
    private final BazelLabel label;

    public BazelTarget(BazelPackage bazelPackage, String targetName) {
        this.bazelPackage = bazelPackage;
        this.targetName = targetName;
        this.label = new BazelLabel(bazelPackage.getLabel().getPackagePath(true), targetName);
    }

    @Override
    protected BazelTargetInfo createInfo() throws CoreException {
        var info = new BazelTargetInfo(getTargetName(), this);
        info.load(getBazelPackage().getInfo());
        return info;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        var other = (BazelTarget) obj;
        return Objects.equals(bazelPackage, other.bazelPackage) && Objects.equals(targetName, other.targetName);
    }

    @Override
    public boolean exists() throws CoreException {
        return getParent().exists() && getBazelPackage().getInfo().getTargets().contains(targetName);
    }

    public BazelPackage getBazelPackage() {
        return bazelPackage;
    }

    /**
     * The {@link BazelProject Bazel project} for this target
     * <p>
     * This method performs a search in the Eclipse workspace for a matching project representing this target. The
     * returned project may represent multiple targets.
     * </p>
     *
     * @return the Bazel target project
     * @throws CoreException
     *             if the project cannot be found in the Eclipse workspace
     */
    public BazelProject getBazelProject() throws CoreException {
        return getInfo().getBazelProject();
    }

    @Override
    public BazelWorkspace getBazelWorkspace() {
        return bazelPackage.getBazelWorkspace();
    }

    @Override
    public BazelLabel getLabel() {
        return label;
    }

    @Override
    public IPath getLocation() {
        try {
            return new Path(getBazelPackage().getInfo().getBuildFile().toString());
        } catch (CoreException e) {
            return null; // assume it does not exist
        }
    }

    @Override
    public BazelPackage getParent() {
        return bazelPackage;
    }

    /**
     * {@return the attributes of the rule}
     *
     * @throws CoreException
     *             in case of errors loading the rules info
     */
    public BazelRuleAttributes getRuleAttributes() throws CoreException {
        return getInfo().getRuleAttributes();
    }

    /**
     * {@return the rule class (e.g., java_library)}
     *
     * @throws CoreException
     *             in case of errors loading the rules info
     */
    public String getRuleClass() throws CoreException {
        return getInfo().getTarget().getRule().getRuleClass();
    }

    /**
     * @return the short, unqualified target name
     */
    public String getTargetName() {
        return targetName;
    }

    public boolean hasBazelProject() throws CoreException {
        return getInfo().findProject() != null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bazelPackage, targetName);
    }
}
