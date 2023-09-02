package com.salesforce.bazel.eclipse.ui.jdt;

import static com.salesforce.bazel.eclipse.ui.jdt.JavaResolutionFactory.ProposalType.CLASSPATH_FIX;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ui.text.java.ClasspathFixProcessor;
import org.eclipse.jface.operation.IRunnableWithProgress;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.ui.jdt.FindClassResolutionsOperation.ClassResolutionCollector;

/**
 * Offers a classpath fix proposal if the broken import statement can be fixed by adding a plugin dependency (required
 * bundle or package import).
 *
 * @since 3.4
 */
public class UnresolvedImportFixProcessor extends ClasspathFixProcessor {

    @Override
    public ClasspathFixProposal[] getFixImportProposals(IJavaProject project, String name) throws CoreException {
        if (!BazelProject.isBazelProject(project.getProject())) {
            return new ClasspathFixProposal[0];
        }

        var result = new ArrayList<>();
        var collector = new ClassResolutionCollector(result, CLASSPATH_FIX, 16);

        IRunnableWithProgress findOperation =
                new FindClassResolutionsOperation(BazelCore.create(project.getProject()), name, collector);
        try {
            findOperation.run(new NullProgressMonitor());
        } catch (InvocationTargetException | InterruptedException e) {
            // ignore
        }
        return result.toArray(new ClasspathFixProposal[result.size()]);
    }

}
