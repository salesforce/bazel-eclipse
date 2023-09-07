/*-
 * Copyright (c) 2023 Salesforce and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Salesforce - Adapted from M2E
*/
package com.salesforce.bazel.eclipse.core.model.discovery.classpath;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;

import com.google.idea.blaze.base.model.primitives.Label;

/**
 * Mutable version of {@link IClasspathEntry}.
 * <p>
 * Use convenience static methods where possible. Add new one when you need one.
 * </p>
 */
public final class ClasspathEntry {

    private static final String ATTRIBUTE_BAZEL_TARGET_NAME = "bazel-target-name";

    public static ClasspathEntry fromExisting(IClasspathEntry entry) {
        var classpathEntry = new ClasspathEntry(entry.getPath(), entry.getEntryKind());
        for (IAccessRule rule : entry.getAccessRules()) {
            classpathEntry.getAccessRules().add(new AccessRule(rule.getPattern(), rule.getKind()));
        }
        for (IClasspathAttribute attribute : entry.getExtraAttributes()) {
            classpathEntry.getExtraAttributes().put(attribute.getName(), attribute.getValue());
        }
        return classpathEntry;
    }

    static IAccessRule newAccessRule(AccessRule rule) {
        return JavaCore.newAccessRule(rule.pattern(), rule.kind());
    }

    static IClasspathAttribute newAttribute(Entry<String, String> entry) {
        return JavaCore.newClasspathAttribute(entry.getKey(), entry.getValue());
    }

    public static ClasspathEntry newLibraryEntry(IPath jarPath, IPath srcJarPath, IPath srcJarRootPath,
            boolean isTestOnlyJar) {
        var entry = new ClasspathEntry(jarPath, IClasspathEntry.CPE_LIBRARY);
        entry.setSourceAttachmentPath(srcJarPath);
        entry.setSourceAttachmentRootPath(srcJarRootPath);
        if (isTestOnlyJar) {
            entry.getExtraAttributes().put(IClasspathAttribute.TEST, Boolean.toString(true));
        }
        return entry;
    }

    public static ClasspathEntry newProjectEntry(IProject project) {
        return new ClasspathEntry(project.getFullPath(), IClasspathEntry.CPE_PROJECT);
    }

    private final IPath path;
    private final int entryKind;
    private IPath sourceAttachmentPath;
    private IPath sourceAttachmentRootPath;
    private boolean exported;

    private final Map<String, String> extraAttributes = new TreeMap<>();
    private final List<AccessRule> accessRules = new ArrayList<>();

    public ClasspathEntry(IPath path, int entryKind) {
        this.path = requireNonNull(path);
        this.entryKind = entryKind;
    }

    /**
     * @return the JDT {@link IClasspathEntry}
     */
    public IClasspathEntry build() {
        var accessRules = this.accessRules.stream().map(ClasspathEntry::newAccessRule).toArray(IAccessRule[]::new);
        var extraAttributes = this.extraAttributes.entrySet()
                .stream()
                .map(ClasspathEntry::newAttribute)
                .toArray(IClasspathAttribute[]::new);

        return switch (entryKind) {
            case IClasspathEntry.CPE_LIBRARY -> JavaCore.newLibraryEntry(
                path,
                sourceAttachmentPath,
                sourceAttachmentRootPath,
                accessRules,
                extraAttributes,
                exported);
            case IClasspathEntry.CPE_PROJECT -> JavaCore
                    .newProjectEntry(path, accessRules, true, extraAttributes, exported);

            default -> throw new IllegalArgumentException(
                    "Unsupported entry kind! Only CPE_LIBRARY or CPE_PROJECT is supported currently!");
        };
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
        var other = (ClasspathEntry) obj;
        return Objects.equals(accessRules, other.accessRules) && (entryKind == other.entryKind)
                && (exported == other.exported) && Objects.equals(extraAttributes, other.extraAttributes)
                && Objects.equals(path, other.path) && Objects.equals(sourceAttachmentPath, other.sourceAttachmentPath)
                && Objects.equals(sourceAttachmentRootPath, other.sourceAttachmentRootPath);
    }

    public List<AccessRule> getAccessRules() {
        return accessRules;
    }

    public Label getBazelTargetOrigin() {
        var origin = getExtraAttributes().get(ATTRIBUTE_BAZEL_TARGET_NAME);
        return origin != null ? Label.createIfValid(origin) : null;
    }

    public int getEntryKind() {
        return entryKind;
    }

    public Map<String, String> getExtraAttributes() {
        return extraAttributes;
    }

    public IPath getPath() {
        return path;
    }

    public IPath getSourceAttachmentPath() {
        return sourceAttachmentPath;
    }

    public IPath getSourceAttachmentRootPath() {
        return sourceAttachmentRootPath;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            accessRules,
            entryKind,
            exported,
            extraAttributes,
            path,
            sourceAttachmentPath,
            sourceAttachmentRootPath);
    }

    public boolean isExported() {
        return exported;
    }

    public void setBazelTargetOrigin(Label origin) {
        getExtraAttributes().put(ATTRIBUTE_BAZEL_TARGET_NAME, origin.toString());
    }

    public void setExported(boolean exported) {
        this.exported = exported;
    }

    public void setSourceAttachmentPath(IPath sourceAttachmentPath) {
        this.sourceAttachmentPath = sourceAttachmentPath;
    }

    public void setSourceAttachmentRootPath(IPath sourceAttachmentRootPath) {
        this.sourceAttachmentRootPath = sourceAttachmentRootPath;
    }
}
