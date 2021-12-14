/*******************************************************************************
 * Copyright (c) 2008-2018 Sonatype, Inc. and others. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors: Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
// copied from the M2Eclipse project

package com.salesforce.bazel.eclipse.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkingSet;

import com.salesforce.bazel.eclipse.BazelNature;
import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.util.BazelConstants;

/**
 * Helper methods to deal with workspace resources passed as navigator selection to actions and wizards.
 *
 * Adapted from m2e org.eclipse.m2e.core.ui.internal.actions.SelectionUtil
 */
public class SelectionUtil {
    static final LogHelper LOG = LogHelper.log(SelectionUtil.class);

    public static final int UNSUPPORTED = 0;

    public static final int PROJECT_WITH_NATURE = 1;

    public static final int PROJECT_WITHOUT_NATURE = 2;

    public static final int POM_FILE = 4;

    public static final int JAR_FILE = 8;

    public static final int WORKING_SET = 16;

    /** Checks which type the given selection belongs to. */
    public static int getSelectionType(IStructuredSelection selection) {
        int type = UNSUPPORTED;
        if (selection != null) {
            for (Iterator<?> it = selection.iterator(); it.hasNext();) {
                int elementType = getElementType(it.next());
                if (elementType == UNSUPPORTED) {
                    return UNSUPPORTED;
                }
                type |= elementType;
            }
        }
        return type;
    }

    /** Checks which type the given element belongs to. */
    public static int getElementType(Object element) {
        IProject project = getType(element, IProject.class);
        if (project != null) {
            try {
                if (project.hasNature(BazelNature.BAZEL_NATURE_ID)) {
                    return PROJECT_WITH_NATURE;
                }
                return PROJECT_WITHOUT_NATURE;
            } catch (CoreException e) {
                // ignored
            }
        }

        IFile file = getType(element, IFile.class);
        if (file != null) {
            String lastSegment = file.getFullPath().lastSegment();
            if (BazelConstants.BUILD_FILE_NAMES.contains(lastSegment)) {
                return POM_FILE;
            }
        }

        IWorkingSet workingSet = getType(element, IWorkingSet.class);
        if (workingSet != null) {
            return WORKING_SET;
        }

        return UNSUPPORTED;
    }

    /**
     * Checks if the object belongs to a given type and returns it or a suitable adapter.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getType(Object element, Class<T> type) {
        if (element == null) {
            return null;
        }
        if (type.isInstance(element)) {
            return (T) element;
        }
        if (element instanceof IAdaptable) {
            T adapter = ((IAdaptable) element).getAdapter(type);
            if (adapter != null) {
                return adapter;
            }
        }
        return Platform.getAdapterManager().getAdapter(element, type);
    }

    public static IPath getSelectedLocation(IStructuredSelection selection) {
        Object element = selection == null ? null : selection.getFirstElement();

        IPath path = getType(element, IPath.class);
        if (path != null) {
            return path;
        }

        IResource resource = getType(element, IResource.class);
        if (resource != null) {
            return resource.getLocation();
        }

        return null;
    }

    public static IWorkingSet getSelectedWorkingSet(IStructuredSelection selection) {
        Object element = selection == null ? null : selection.getFirstElement();
        if (element == null) {
            return null;
        }

        IWorkingSet workingSet = getType(element, IWorkingSet.class);
        if (workingSet != null) {
            return workingSet;
        }

        return null;
    }

    /**
     * Returns all the Bazel projects found in the given selection. If no projects are found in the selection and
     * <code>includeAll</code> is true, all workspace projects are returned.
     *
     * @param selection
     * @param includeAll
     *            flag to return all workspace projects if selection doesn't contain any Maven projects.
     * @return an array of {@link IProject} containing all the Maven projects found in the given selection, or all the
     *         workspace projects if no Bazel project was found and <code>includeAll</code> is true.
     * @since 1.4.0
     */
    public static IProject[] getProjects(ISelection selection, boolean includeAll) {
        ArrayList<IProject> projectList = new ArrayList<IProject>();
        if (selection instanceof IStructuredSelection) {
            for (Iterator<?> it = ((IStructuredSelection) selection).iterator(); it.hasNext();) {
                Object o = it.next();
                if (o instanceof IProject) {
                    safeAdd((IProject) o, projectList);
                } else if (o instanceof IWorkingSet) {
                    IWorkingSet workingSet = (IWorkingSet) o;
                    for (IAdaptable adaptable : workingSet.getElements()) {
                        IProject project = adaptable.getAdapter(IProject.class);
                        safeAdd(project, projectList);
                    }
                } else if (o instanceof IResource) {
                    safeAdd(((IResource) o).getProject(), projectList);
                } else if (o instanceof IAdaptable) {
                    IAdaptable adaptable = (IAdaptable) o;
                    IProject project = adaptable.getAdapter(IProject.class);
                    safeAdd(project, projectList);
                }
            }
        }

        if (projectList.isEmpty() && includeAll) {
            return ComponentContext.getInstance().getResourceHelper().getEclipseWorkspaceRoot().getProjects();
        }
        return projectList.toArray(new IProject[projectList.size()]);
    }

    private static void safeAdd(IProject project, List<IProject> projectList) {
        try {
            if (project != null && project.isAccessible() && project.hasNature(BazelNature.BAZEL_NATURE_ID)
                    && !projectList.contains(project)) {
                projectList.add(project);
            }
        } catch (CoreException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

}
