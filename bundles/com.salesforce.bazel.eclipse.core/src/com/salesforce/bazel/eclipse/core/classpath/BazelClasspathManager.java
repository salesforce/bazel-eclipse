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
package com.salesforce.bazel.eclipse.core.classpath;

import static com.salesforce.bazel.eclipse.core.classpath.BazelClasspathScope.DEFAULT_CLASSPATH;
import static java.util.Objects.requireNonNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants;
import com.salesforce.bazel.eclipse.core.model.BazelModelManager;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.discovery.TargetDiscoveryAndProvisioningExtensionLookup;
import com.salesforce.bazel.eclipse.core.model.discovery.TargetProvisioningStrategy;
import com.salesforce.bazel.eclipse.core.model.discovery.WorkspaceClasspathStrategy;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;

/**
 * The central point for for mapping classpath between Bazel and JDT.
 * <p>
 * It implements persisting and reading calculated containers and calculating classpath for Bazel projects.
 * </p>
 */
public class BazelClasspathManager {

    private static Logger LOG = LoggerFactory.getLogger(BazelClasspathManager.class);

    private static final String PROPERTY_SRC_ROOT = ".srcRoot"; //$NON-NLS-1$
    private static final String PROPERTY_SRC_ENCODING = ".srcEncoding"; //$NON-NLS-1$
    private static final String PROPERTY_SRC_PATH = ".srcPath"; //$NON-NLS-1$
    private static final String PROPERTY_JAVADOC_URL = ".javadoc"; //$NON-NLS-1$

    private final File stateLocationDirectory;
    private final BazelModelManager bazelModelManager;

    public BazelClasspathManager(File stateLocationDirectory, BazelModelManager bazelModelManager) {
        this.bazelModelManager = bazelModelManager;
        this.stateLocationDirectory = requireNonNull(stateLocationDirectory);
    }

    IClasspathEntry[] computeClasspath(BazelProject bazelProject, BazelClasspathScope scope, Properties props,
            IProgressMonitor monitor) throws CoreException {
        try {
            var strategy = getTargetProvisioningStrategy(bazelProject);
            // compute classpath from Bazel
            var classpathResult = strategy.computeClasspaths(List.of(bazelProject), bazelProject.getBazelWorkspace(),
                scope, requireNonNull(monitor, "missing IProgressMonitor"));
            var projectClasspath =
                    requireNonNull(classpathResult.get(bazelProject), "no classpath returned, this is unexpected here");

            // eliminate duplicates
            Map<IPath, ClasspathEntry> paths = new LinkedHashMap<>();
            for (ClasspathEntry entry : projectClasspath) {
                if (!paths.containsKey(entry.getPath())) {
                    // add manually configured properties
                    configureAttachedSourcesAndJavadoc(entry, props);

                    // put into map
                    paths.put(entry.getPath(), entry);
                }
            }
            return paths.values().stream().map(ClasspathEntry::build).toArray(IClasspathEntry[]::new);
        } finally {
            monitor.done();
        }
    }

    private void configureAttachedSourcesAndJavadoc(ClasspathEntry entry, Properties sourceAttachment) {
        if ((IClasspathEntry.CPE_LIBRARY == entry.getEntryKind()) && (entry.getSourceAttachmentPath() == null)) {
            var path = entry.getPath().toPortableString();
            if (sourceAttachment != null) {
                var srcPath = sourceAttachment.getProperty(path + PROPERTY_SRC_PATH);
                if (srcPath != null) {
                    entry.setSourceAttachmentPath(Path.fromPortableString(srcPath));
                }
                var srcRootPath = sourceAttachment.getProperty(path + PROPERTY_SRC_ROOT);
                if (srcRootPath != null) {
                    entry.setSourceAttachmentRootPath(Path.fromPortableString(srcRootPath));
                }
                var srcEncoding = sourceAttachment.getProperty(path + PROPERTY_SRC_ENCODING);
                if (srcEncoding != null) {
                    entry.getExtraAttributes().put(IClasspathAttribute.SOURCE_ATTACHMENT_ENCODING, srcEncoding);
                }
            }

            // configure javadocs if available
            if ((entry.getExtraAttributes().get(IClasspathAttribute.JAVADOC_LOCATION_ATTRIBUTE_NAME) == null)
                    && (sourceAttachment != null) && sourceAttachment.containsKey(path + PROPERTY_JAVADOC_URL)) {
                var javaDocUrl = sourceAttachment.getProperty(path + PROPERTY_JAVADOC_URL);
                if (javaDocUrl != null) {
                    entry.getExtraAttributes().put(IClasspathAttribute.JAVADOC_LOCATION_ATTRIBUTE_NAME, javaDocUrl);
                }
            }
        }
    }

    IClasspathEntry getBazelContainerEntry(IJavaProject project) {
        return BazelClasspathHelpers.getBazelContainerEntry(project);
    }

    BazelModelManager getBazelModelManager() {
        return bazelModelManager;
    }

    BazelProject getBazelProject(IJavaProject project) {
        return getBazelModelManager().getBazelProject(project.getProject());
    }

    public IClasspathEntry[] getClasspath(IJavaProject project, BazelClasspathScope scope, IProgressMonitor monitor)
            throws CoreException {
        var bazelProject = getBazelProject(project);
        if (bazelProject == null) {
            return new IClasspathEntry[0];
        }
        try {
            var props = new Properties();
            var file = getSourceAttachmentPropertiesFile(project.getProject());
            if (file.canRead()) {
                try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                    props.load(is);
                }
            }
            return computeClasspath(bazelProject, scope, props, monitor);
        } catch (IOException e) {
            throw new CoreException(Status.error("Can't read classpath container data", e));
        }
    }

    public IClasspathEntry[] getClasspath(IJavaProject project, IProgressMonitor monitor) throws CoreException {
        return getClasspath(project, DEFAULT_CLASSPATH, monitor);
    }

    File getContainerStateFile(IProject project) {
        return new File(stateLocationDirectory, project.getName() + ".container"); //$NON-NLS-1$
    }

    String getJavadocLocation(IClasspathEntry entry) {
        return BazelClasspathHelpers.getAttribute(entry, IClasspathAttribute.JAVADOC_LOCATION_ATTRIBUTE_NAME);
    }

    /**
     * Loads a saved container if available.
     *
     * @param project
     *            a project to obtain the container for
     * @return a saved classpath container for the specified project (may be <code>null</code>)
     * @throws CoreException
     */
    public IClasspathContainer getSavedContainer(IProject project) throws CoreException {
        var containerStateFile = getContainerStateFile(project);
        if (!containerStateFile.exists()) {
            return null;
        }

        try (var is = new FileInputStream(containerStateFile)) {
            return new BazelClasspathContainerSaveHelper().readContainer(is);
        } catch (ObjectStreamException | ClassNotFoundException ex) {
            LOG.warn(
                "Discarding classpath container state for project '{}' due to de-serialization incompatibilities. {}",
                project.getName(), ex.getMessage(), ex);
            return null;
        } catch (IOException ex) {
            throw new CoreException(Status.error("Can't read classpath container state for " + project.getName(), ex));
        }
    }

    String getSourceAttachmentEncoding(IClasspathEntry entry) {
        return BazelClasspathHelpers.getAttribute(entry, IClasspathAttribute.SOURCE_ATTACHMENT_ENCODING);
    }

    File getSourceAttachmentPropertiesFile(IProject project) {
        return new File(stateLocationDirectory, project.getName() + ".sources"); //$NON-NLS-1$
    }

    TargetProvisioningStrategy getTargetProvisioningStrategy(BazelProject bazelProject) throws CoreException {
        if (bazelProject.isWorkspaceProject()) {
            return new WorkspaceClasspathStrategy();
        }
        return new TargetDiscoveryAndProvisioningExtensionLookup()
                .createTargetProvisioningStrategy(bazelProject.getBazelWorkspace().getBazelProjectView());
    }

    /**
     * Persists customizations to the classpath container and also refreshes the container for the specified project.
     *
     * @param project
     *            project owning the container
     * @param containerSuggestion
     *            the suggested updates
     * @param monitor
     *            progress monitor
     */
    public void persistAttachedSourcesAndJavadoc(IJavaProject project, IClasspathContainer containerSuggestion,
            IProgressMonitor monitor) throws CoreException {
        var facade = getBazelProject(project);
        if (facade == null) {
            return;
        }

        // collect all source/javadoc attachement
        var props = new Properties();
        var entries = containerSuggestion.getClasspathEntries();
        for (IClasspathEntry entry : entries) {
            if (IClasspathEntry.CPE_LIBRARY == entry.getEntryKind()) {
                var path = entry.getPath().toPortableString();
                if (entry.getSourceAttachmentPath() != null) {
                    props.put(path + PROPERTY_SRC_PATH, entry.getSourceAttachmentPath().toPortableString());
                }
                if (entry.getSourceAttachmentRootPath() != null) {
                    props.put(path + PROPERTY_SRC_ROOT, entry.getSourceAttachmentRootPath().toPortableString());
                }
                var sourceAttachmentEncoding = getSourceAttachmentEncoding(entry);
                if (sourceAttachmentEncoding != null) {
                    props.put(path + PROPERTY_SRC_ENCODING, sourceAttachmentEncoding);
                }
                var javadocUrl = getJavadocLocation(entry);
                if (javadocUrl != null) {
                    props.put(path + PROPERTY_JAVADOC_URL, javadocUrl);
                }
            }
        }

        // eliminate all "standard" source/javadoc attachement we get from local repo
        entries = computeClasspath(facade, DEFAULT_CLASSPATH, null /* no props */, monitor);
        for (IClasspathEntry entry : entries) {
            if (IClasspathEntry.CPE_LIBRARY == entry.getEntryKind()) {
                var path = entry.getPath().toPortableString();
                var value = (String) props.get(path + PROPERTY_SRC_PATH);
                if ((value != null) && (entry.getSourceAttachmentPath() != null)
                        && value.equals(entry.getSourceAttachmentPath().toPortableString())) {
                    props.remove(path + PROPERTY_SRC_PATH);
                }
                value = (String) props.get(path + PROPERTY_SRC_ROOT);
                if ((value != null) && (entry.getSourceAttachmentRootPath() != null)
                        && value.equals(entry.getSourceAttachmentRootPath().toPortableString())) {
                    props.remove(path + PROPERTY_SRC_ROOT);
                }
            }
        }

        // persist custom source/javadoc attachement info
        var file = getSourceAttachmentPropertiesFile(project.getProject());
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(file))) {
            props.store(os, null);
        } catch (IOException e) {
            throw new CoreException(Status.error("Can't save classpath container changes", e));
        }

        // update classpath container (this will re-set classpath on JavaProject)
        updateClasspath(project, monitor);
    }

    void saveContainerState(IProject project, IClasspathContainer container) throws CoreException {
        var containerStateFile = getContainerStateFile(project);
        try (var is = new FileOutputStream(containerStateFile)) {
            new BazelClasspathContainerSaveHelper().writeContainer(container, is);
        } catch (IOException ex) {
            throw new CoreException(Status.error("Can't save classpath container state for " + project.getName(), ex));
        }
    }

    public void updateClasspath(IJavaProject project, IProgressMonitor monitor) throws CoreException {
        try {
            var subMonitor = SubMonitor.convert(monitor, 2);
            var containerEntry = getBazelContainerEntry(project);
            var path = containerEntry != null ? containerEntry.getPath()
                    : new Path(BazelCoreSharedContstants.CLASSPATH_CONTAINER_ID);
            var classpath = getClasspath(project, subMonitor.newChild(1));
            IClasspathContainer container = new BazelClasspathContainer(path, classpath);
            JavaCore.setClasspathContainer(container.getPath(), new IJavaProject[] { project },
                new IClasspathContainer[] { container }, subMonitor.newChild(1));
            saveContainerState(project.getProject(), container);
        } finally {
            if (monitor != null) {
                monitor.done();
            }
        }
    }

}
