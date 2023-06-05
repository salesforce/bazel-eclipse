/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.google.idea.blaze.base.model.primitives;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import com.google.idea.blaze.base.ideinfo.ProjectDataInterner;

/** Wrapper around a string for a blaze label ([@external_workspace]//package:rule). */
@Immutable
public final class Label extends TargetExpression {
    // still Serializable as part of ProjectViewSet
    public static final long serialVersionUID = 2L;

    public static Label create(String label) {
        var error = validate(label);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        return ProjectDataInterner.intern(new Label(label));
    }

    public static Label create(@Nullable String externalWorkspaceName, WorkspacePath packagePath,
            TargetName targetName) {
        var fullLabel = String.format("%s//%s:%s", externalWorkspaceName != null ? "@" + externalWorkspaceName : "",
            packagePath, targetName);
        return ProjectDataInterner.intern(new Label(fullLabel));
    }

    public static Label create(WorkspacePath packageName, TargetName newTargetName) {
        return create(null, packageName, newTargetName);
    }

    /** Silently returns null if this is not a valid Label */
    @Nullable
    public static Label createIfValid(String label) {
        return validate(label) == null ? ProjectDataInterner.intern(new Label(label)) : null;
    }

    public static Label fromProto(String proto) {
        return ProjectDataInterner.intern(new Label(proto));
    }

    /** Validate the given target label. Returns null on success or an error message otherwise. */
    @Nullable
    public static String validate(String label) {
        var colonIndex = label.indexOf(':');
        if (label.startsWith("//") && (colonIndex >= 0)) {
            var packageName = label.substring("//".length(), colonIndex);
            var error = validatePackagePath(packageName);
            if (error != null) {
                return error;
            }
            var ruleName = label.substring(colonIndex + 1);
            error = TargetName.validate(ruleName);
            if (error != null) {
                return error;
            }
            return null;
        }
        if (label.startsWith("@") && (colonIndex >= 0)) {
            // a bazel-specific label pointing to a different repository
            var slashIndex = label.indexOf("//");
            if (slashIndex >= 0) {
                return validate(label.substring(slashIndex));
            }
        }
        return "Not a valid label, no target name found: " + label;
    }

    @Nullable
    public static String validatePackagePath(String path) {
        return PackagePathValidator.validatePackageName(path);
    }

    private Label(String label) {
        super(label);
    }

    /**
     * Return the workspace path for the package label for the given label. For example, if the package is
     * //j/c/g/a/apps/docs:release, it returns j/c/g/a/apps/docs.
     */
    public WorkspacePath blazePackage() {
        var labelStr = toString();
        var startIndex = labelStr.indexOf("//") + "//".length();
        var colonIndex = labelStr.lastIndexOf(':');
        return new WorkspacePath(labelStr.substring(startIndex, colonIndex));
    }

    /**
     * Returns the external workspace referenced by this label, or null if it's a main workspace label.
     */
    @Nullable
    public String externalWorkspaceName() {
        var label = toString();
        if (!label.startsWith("@")) {
            return null;
        }
        var slashesIndex = label.indexOf("//");
        return label.substring(1, slashesIndex);
    }

    public boolean isExternal() {
        return toString().startsWith("@");
    }

    /**
     * Extract the target name from a label. The target name follows a colon at the end of the label.
     *
     * @return the target name
     */
    public TargetName targetName() {
        var labelStr = toString();
        var colonLocation = labelStr.lastIndexOf(':');
        var targetNameStart = colonLocation + 1;
        var targetNameStr = labelStr.substring(targetNameStart);
        return TargetName.create(targetNameStr);
    }

    /** A new label with the same workspace and package paths, but a different target name. */
    @Nullable
    public Label withTargetName(@Nullable String targetName) {
        if (targetName == null) {
            return null;
        }
        var target = TargetName.createIfValid(targetName);
        return target != null ? Label.create(externalWorkspaceName(), blazePackage(), target) : null;
    }
}
