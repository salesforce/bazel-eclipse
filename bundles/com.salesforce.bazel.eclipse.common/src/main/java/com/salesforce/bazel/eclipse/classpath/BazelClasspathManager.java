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

package com.salesforce.bazel.eclipse.classpath;

import static com.salesforce.bazel.eclipse.classpath.BazelClasspathScope.DEFAULT_CLASSPATH;
import static com.salesforce.bazel.eclipse.classpath.IClasspathContainerConstants.CONTAINER_ID;
import static java.util.Objects.requireNonNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
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
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.component.EclipseBazelWorkspaceContext;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseWorkProgressMonitor;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathData;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathEntry;
import com.salesforce.bazel.sdk.lang.jvm.classpath.impl.JvmUnionClasspath;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;

/**
 * The central point for for mapping classpath between Bazel and JDT.
 * <p>
 * It implements persisting and reading calculated containers and calculating classpath for Bazel projects.
 * </p>
 */
public class BazelClasspathManager {

    private static final String PROPERTY_SRC_ROOT = ".srcRoot"; //$NON-NLS-1$
    private static final String PROPERTY_SRC_ENCODING = ".srcEncoding"; //$NON-NLS-1$
    private static final String PROPERTY_SRC_PATH = ".srcPath"; //$NON-NLS-1$
    private static final String PROPERTY_JAVADOC_URL = ".javadoc"; //$NON-NLS-1$

    private final File stateLocationDirectory;

    public BazelClasspathManager(File stateLocationDirectory) {
        this.stateLocationDirectory = requireNonNull(stateLocationDirectory);
    }

    IPath getAbsoluteLocation(final String pathInWorkspace) {
        if (pathInWorkspace == null) {
            return null;
        }

        File filePathFile = new File(pathInWorkspace);
        java.nio.file.Path absolutePath;
        String searchedLocations = "";
        if (!filePathFile.isAbsolute()) {
            // need to figure out where this relative path is on disk
            // TODO this hunting around for the right root of the relative path indicates we need to rework this
            File bazelExecRootDir = getBazelWorkspace().getBazelExecRootDirectory();
            filePathFile = new File(bazelExecRootDir, pathInWorkspace);
            if (!filePathFile.exists()) {
                searchedLocations = filePathFile.getAbsolutePath();
                File bazelOutputBase = getBazelWorkspace().getBazelOutputBaseDirectory();
                filePathFile = new File(bazelOutputBase, pathInWorkspace);
                if (!filePathFile.exists()) {
                    searchedLocations = searchedLocations + ", " + filePathFile.getAbsolutePath();
                    // java_import locations are resolved here
                    File bazelWorkspaceDir = getBazelWorkspace().getBazelWorkspaceRootDirectory();
                    filePathFile = new File(bazelWorkspaceDir, pathInWorkspace);
                    if (!filePathFile.exists()) {
                        searchedLocations = searchedLocations + ", " + filePathFile.getAbsolutePath();
                        // give up
                        filePathFile = null;
                    }
                }
            }
        }

        if (filePathFile == null) {
            // this can happen if someone does a 'bazel clean' using the command line #113
            // https://github.com/salesforce/bazel-eclipse/issues/113 $SLASH_OK url

            // let's assume this should exist in the output base
            // use and go on so Eclipse can report an error
            File bazelOutputBase = getBazelWorkspace().getBazelOutputBaseDirectory();
            filePathFile = new File(bazelOutputBase, pathInWorkspace);
        }

        // we now can derive our absolute path
        absolutePath = filePathFile.toPath();

        // We have had issues with Eclipse complaining about symlinks in the Bazel output directories not being real,
        // so we resolve them before handing them back to Eclipse.
        if (Files.isSymbolicLink(absolutePath)) {
            try {
                // resolving the link will fail if the symlink does not a point to a real file
                absolutePath = Files.readSymbolicLink(absolutePath);
            } catch (IOException ex) {
                // give up, let Eclipse report an error
            }
        }

        return Path.fromOSString(absolutePath.toString());
    }

    IClasspathEntry getBazelContainerEntry(IJavaProject project) {
        return BazelClasspathHelpers.getBazelContainerEntry(project);
    }

    BazelProject getBazelProject(IJavaProject project) {
        return getProjectManager().create(project.getElementName(), project.getProject());
    }

    BazelWorkspace getBazelWorkspace() {
        return EclipseBazelWorkspaceContext.getInstance().getBazelWorkspace();
    }

    IClasspathEntry[] getClasspath(BazelProject bazelProject, BazelClasspathScope scope, Properties props,
            boolean eliminateDuplicateEntries, IProgressMonitor monitor) {

        // create the classpath computation engine
        JvmClasspathData jcmClasspathData =
                new JvmUnionClasspath(bazelProject).getClasspathEntries(new EclipseWorkProgressMonitor(monitor));

        // convert the logical entries into concrete Eclipse entries
        List<IClasspathEntry> entries = new ArrayList<>();
        for (JvmClasspathEntry entry : jcmClasspathData.jvmClasspathEntries) {
            if (entry.pathToJar != null) {
                IPath jarPath = getAbsoluteLocation(entry.pathToJar);
                if (jarPath != null) {
                    // srcJarPath must be relative to the workspace, by order of Eclipse
                    IPath srcJarPath = getAbsoluteLocation(entry.pathToSourceJar);
                    IPath srcJarRootPath = null;
                    entries.add(newLibraryEntry(jarPath, srcJarPath, srcJarRootPath, entry.isTestJar));
                }
            } else {
                entries.add(newProjectEntry(entry.bazelProject));
            }
        }

        if (eliminateDuplicateEntries) {
            Map<IPath, IClasspathEntry> paths = new LinkedHashMap<>();
            for (IClasspathEntry entry : entries) {
                if (!paths.containsKey(entry.getPath())) {
                    paths.put(entry.getPath(), entry);
                }
            }
            return paths.values().toArray(new IClasspathEntry[paths.size()]);
        }

        return entries.toArray(new IClasspathEntry[entries.size()]);
    }

    public IClasspathEntry[] getClasspath(IJavaProject project, BazelClasspathScope scope,
            boolean eliminateDuplicateEntries, IProgressMonitor monitor) throws CoreException {
        BazelProject facade = getBazelProject(project);
        if (facade == null) {
            return new IClasspathEntry[0];
        }
        try {
            Properties props = new Properties();
            File file = getSourceAttachmentPropertiesFile(project.getProject());
            if (file.canRead()) {
                try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                    props.load(is);
                }
            }
            return getClasspath(facade, scope, props, eliminateDuplicateEntries, monitor);
        } catch (IOException e) {
            throw new CoreException(Status.error("Can't read classpath container data", e));
        }
    }

    public IClasspathEntry[] getClasspath(IJavaProject project, BazelClasspathScope scope, IProgressMonitor monitor)
            throws CoreException {
        return getClasspath(project, scope, true, monitor);
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

    BazelProjectManager getProjectManager() {
        return ComponentContext.getInstanceCheckInitialized().getProjectManager();
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
        File containerStateFile = getContainerStateFile(project);
        if (!containerStateFile.exists()) {
            return null;
        }

        try (FileInputStream is = new FileInputStream(containerStateFile)) {
            return new BazelClasspathContainerSaveHelper().readContainer(is);
        } catch (IOException | ClassNotFoundException ex) {
            throw new CoreException(Status.error("Can't read classpath container state for " + project.getName(), ex));
        }
    }

    String getSourceAttachmentEncoding(IClasspathEntry entry) {
        return BazelClasspathHelpers.getAttribute(entry, IClasspathAttribute.SOURCE_ATTACHMENT_ENCODING);
    }

    File getSourceAttachmentPropertiesFile(IProject project) {
        return new File(stateLocationDirectory, project.getName() + ".sources"); //$NON-NLS-1$
    }

    IClasspathEntry newLibraryEntry(IPath jarPath, IPath srcJarPath, IPath srcJarRootPath, boolean isTestJar) {
        return JavaCore.newLibraryEntry(jarPath, srcJarPath, srcJarRootPath, isTestJar);
    }

    IClasspathEntry newProjectEntry(BazelProject bazelProject) {
        return JavaCore.newProjectEntry(((IProject) bazelProject.projectImpl).getFullPath());
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
        BazelProject facade = getBazelProject(project);
        if (facade == null) {
            return;
        }

        // collect all source/javadoc attachement
        Properties props = new Properties();
        IClasspathEntry[] entries = containerSuggestion.getClasspathEntries();
        for (IClasspathEntry entry : entries) {
            if (IClasspathEntry.CPE_LIBRARY == entry.getEntryKind()) {
                String path = entry.getPath().toPortableString();
                if (entry.getSourceAttachmentPath() != null) {
                    props.put(path + PROPERTY_SRC_PATH, entry.getSourceAttachmentPath().toPortableString());
                }
                if (entry.getSourceAttachmentRootPath() != null) {
                    props.put(path + PROPERTY_SRC_ROOT, entry.getSourceAttachmentRootPath().toPortableString());
                }
                String sourceAttachmentEncoding = getSourceAttachmentEncoding(entry);
                if (sourceAttachmentEncoding != null) {
                    props.put(path + PROPERTY_SRC_ENCODING, sourceAttachmentEncoding);
                }
                String javadocUrl = getJavadocLocation(entry);
                if (javadocUrl != null) {
                    props.put(path + PROPERTY_JAVADOC_URL, javadocUrl);
                }
            }
        }

        // eliminate all "standard" source/javadoc attachement we get from local repo
        entries = getClasspath(facade, DEFAULT_CLASSPATH, null, true, monitor);
        for (IClasspathEntry entry : entries) {
            if (IClasspathEntry.CPE_LIBRARY == entry.getEntryKind()) {
                String path = entry.getPath().toPortableString();
                String value = (String) props.get(path + PROPERTY_SRC_PATH);
                if (value != null && entry.getSourceAttachmentPath() != null
                        && value.equals(entry.getSourceAttachmentPath().toPortableString())) {
                    props.remove(path + PROPERTY_SRC_PATH);
                }
                value = (String) props.get(path + PROPERTY_SRC_ROOT);
                if (value != null && entry.getSourceAttachmentRootPath() != null
                        && value.equals(entry.getSourceAttachmentRootPath().toPortableString())) {
                    props.remove(path + PROPERTY_SRC_ROOT);
                }
            }
        }

        // persist custom source/javadoc attachement info
        File file = getSourceAttachmentPropertiesFile(project.getProject());
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(file))) {
            props.store(os, null);
        } catch (IOException e) {
            throw new CoreException(Status.error("Can't save classpath container changes", e));
        }

        // update classpath container (this will re-set classpath on JavaProject)
        updateClasspath(project, monitor);
    }

    void saveContainerState(IProject project, IClasspathContainer container) throws CoreException {
        File containerStateFile = getContainerStateFile(project);
        try (FileOutputStream is = new FileOutputStream(containerStateFile)) {
            new BazelClasspathContainerSaveHelper().writeContainer(container, is);
        } catch (IOException ex) {
            throw new CoreException(Status.error("Can't save classpath container state for " + project.getName(), ex));
        }
    }

    public void updateClasspath(IJavaProject project, IProgressMonitor monitor) throws CoreException {
        IClasspathEntry containerEntry = getBazelContainerEntry(project);
        IPath path = containerEntry != null ? containerEntry.getPath() : new Path(CONTAINER_ID);
        IClasspathEntry[] classpath = getClasspath(project, monitor);
        IClasspathContainer container = new BazelClasspathContainer(path, classpath);
        JavaCore.setClasspathContainer(container.getPath(), new IJavaProject[] { project },
            new IClasspathContainer[] { container }, monitor);
        saveContainerState(project.getProject(), container);
    }

}
