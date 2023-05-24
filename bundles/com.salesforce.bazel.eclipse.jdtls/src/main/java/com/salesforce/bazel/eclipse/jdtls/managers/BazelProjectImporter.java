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
 *      Salesforce - initial implementation similar to JDT LS importers
*/

package com.salesforce.bazel.eclipse.jdtls.managers;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BAZEL_NATURE_ID;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_WORKSPACE;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_WORKSPACE_BAZEL;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_WORKSPACE_BZLMOD;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.ls.core.internal.AbstractProjectImporter;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.managers.BasicFileDetector;

/**
 * Importer for Bazel projects.
 * <p>
 * The importer is registered with a priority to get triggered before Gradel, Maven, Eclipse and others. This is
 * important so we can handle Bazel projects.
 * </p>
 */
@SuppressWarnings("restriction")
public final class BazelProjectImporter extends AbstractProjectImporter {

    @Override
    public boolean applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
        if (directories == null) {
            //@formatter:off
            var bazelDetector = new BasicFileDetector(rootFolder.toPath(), FILE_NAME_WORKSPACE_BAZEL, FILE_NAME_WORKSPACE, FILE_NAME_WORKSPACE_BZLMOD)
                    .includeNested(false)
                    .addExclusions("**/bazel-*"); //default Bazel symlinks (this is only a guess)
            //@formatter:on

            // exclude all existing non Bazel projects
            for (IProject project : ProjectUtils.getAllProjects()) {
                if (!ProjectUtils.hasNature(project, BAZEL_NATURE_ID)) {
                    var path = project.getLocation().toOSString();
                    bazelDetector.addExclusions(path);
                }
            }

            directories = bazelDetector.scan(monitor);
        }
        return !directories.isEmpty();
    }

    @Override
    public void importToWorkspace(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
        // scan for workspace and project view
    }

    @Override
    public void reset() {

    }
}
