/*-
 * Copyright (c) 2024 Salesforce and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Salesforce - adapted from M2E, JDT or other Eclipse project
 */
package com.salesforce.bazel.eclipse.core.model;

import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * A synchronization participant is called during synchronization to allow participation in the Bazel & IDE
 * synchronization process.
 */
public interface SynchronizationParticipant {

    /**
     * Called at the end of the synchronization process when the workspace has been synchronized with the Bazel model.
     * <p>
     * This method is called when the synchronization process still holds the workspace lock. Thus, the user is blocked
     * from any resource modifications. Implementors should not try to obtain additional locks.
     * </p>
     *
     * @param bazelWorkspace
     *            the Bazel workspace the was synchronized
     * @param bazelProjects
     *            the set of Bazel projects provisioned/updated during synchronization
     * @param monitor
     *            progress monitor for reporting progress and checking for cancellation
     */
    void afterSynchronizationCompleted(BazelWorkspace bazelWorkspace, Set<BazelProject> bazelProjects,
            IProgressMonitor monitor) throws CoreException;

}
