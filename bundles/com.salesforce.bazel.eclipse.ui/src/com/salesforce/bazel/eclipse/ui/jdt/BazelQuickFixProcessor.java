/*-
 *
 */
package com.salesforce.bazel.eclipse.ui.jdt;

import static com.salesforce.bazel.eclipse.core.model.BazelProject.isBazelProject;
import static com.salesforce.bazel.eclipse.ui.jdt.JavaResolutionFactory.ProposalType.JAVA_COMPLETION;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.IQuickFixProcessor;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.ui.jdt.FindClassResolutionsOperation.ClassResolutionCollector;

/**
 * A quick fix processor for updating BUILD.bazel dependencies
 */
public class BazelQuickFixProcessor implements IQuickFixProcessor {

    /*
     * Copied from org.eclipse.jdt.internal.coreext.dom.ASTNodes.getParent.
     */
    private static ASTNode getParent(ASTNode node, int nodeType) {
        do {
            node = node.getParent();
        } while ((node != null) && (node.getNodeType() != nodeType));
        return node;
    }

    private ClassResolutionCollector createCollector(Collection<Object> result) {
        return new ClassResolutionCollector(result, JAVA_COMPLETION, 100);
    }

    @Override
    public IJavaCompletionProposal[] getCorrections(IInvocationContext context, IProblemLocation[] locations)
            throws CoreException {
        var results = new ArrayList<>();

        var collector = createCollector(results);

        for (IProblemLocation location : locations) {
            var id = location.getProblemId();
            switch (id) {
                case IProblem.ForbiddenReference: // fall through
                case IProblem.ImportNotFound: // fall through
                case IProblem.UndefinedName: // fall through
                case IProblem.UndefinedType: // fall through
                case IProblem.UnresolvedVariable: // fall through
                case IProblem.MissingTypeInMethod: // fall through
                case IProblem.MissingTypeInConstructor:
                case IProblem.MissingTypeInLambda:
                    handleImportNotFound(context, location, collector);
                    break;
                case IProblem.IsClassPathCorrect:
                    handleMissingTransitive(context, location, collector);
                    break;
            }
        }
        return results.toArray(new IJavaCompletionProposal[results.size()]);
    }

    private void handleImportNotFound(IInvocationContext context, IProblemLocation problemLocation,
            ClassResolutionCollector collector) throws CoreException {
        var cu = context.getASTRoot();
        var selectedNode = problemLocation.getCoveringNode(cu);
        if (selectedNode != null) {
            String className = null;

            var importDeclaration = getParent(selectedNode, ASTNode.IMPORT_DECLARATION);
            if (importDeclaration instanceof ImportDeclaration importDeclarationNode) {
                // class name only searchable for non '.*' imports
                if (!importDeclarationNode.isOnDemand()) {
                    className = importDeclarationNode.getName().getFullyQualifiedName();
                }
            } else if (selectedNode instanceof Name nameNode) {
                var typeBinding = nameNode.resolveTypeBinding();
                if (typeBinding != null) {
                    className = typeBinding.getBinaryName();
                }
                if ((className == null) && (selectedNode instanceof SimpleName simpleNameNode)) { // fallback if the type cannot be
                    // resolved
                    className = simpleNameNode.getIdentifier();
                }
            } else {
                ITypeBinding referencedElement = null;
                if (selectedNode instanceof Type type) {
                    referencedElement = type.resolveBinding();
                } else if (selectedNode instanceof MethodInvocation methodInvocation) {
                    var tempMethod = methodInvocation.resolveMethodBinding();
                    if (tempMethod != null) {
                        referencedElement = tempMethod.getDeclaringClass();
                    }
                } else if (selectedNode instanceof FieldAccess fieldAccess) {
                    var tempVariable = fieldAccess.resolveFieldBinding();
                    if (tempVariable != null) {
                        referencedElement = tempVariable.getDeclaringClass();
                    }
                }
                if (referencedElement != null) {
                    className = referencedElement.getBinaryName();
                }
            }

            if (className != null) {
                var project = cu.getJavaElement().getJavaProject().getProject();

                // only try to find proposals in Bazel projects
                if (!isBazelProject(project)) {
                    return;
                }

                // find the class
                new FindClassResolutionsOperation(BazelCore.create(project.getProject()), className, collector)
                        .run(new NullProgressMonitor());
            }
        }
    }

    private void handleMissingTransitive(IInvocationContext context, IProblemLocation problemLocation,
            ClassResolutionCollector collector) throws CoreException {
        var cu = context.getASTRoot();
        var problemArguments = problemLocation.getProblemArguments();
        if (problemArguments.length == 1) {
            var className = problemArguments[0];
            if (className != null) {
                var project = cu.getJavaElement().getJavaProject().getProject();

                // only try to find proposals in Bazel projects
                if (!isBazelProject(project)) {
                    return;
                }

                // find the class
                new FindClassResolutionsOperation(BazelCore.create(project.getProject()), className, collector)
                        .run(new NullProgressMonitor());
            }
        }
    }

    @Override
    public boolean hasCorrections(ICompilationUnit unit, int problemId) {
        switch (problemId) {
            case IProblem.ForbiddenReference:
            case IProblem.UndefinedName: // fall through
            case IProblem.ImportNotFound: // fall through
            case IProblem.UndefinedType: // fall through
            case IProblem.UnresolvedVariable: // fall through
            case IProblem.MissingTypeInMethod: // fall through
            case IProblem.MissingTypeInConstructor:
            case IProblem.MissingTypeInLambda:
            case IProblem.IsClassPathCorrect:
                var parent = unit.getParent();
                if (parent != null) {
                    var project = parent.getJavaProject();
                    if (project != null) {
                        try {
                            return isBazelProject(project.getProject());
                        } catch (Exception e) {
                            return false;
                        }
                    }
                }
        }
        return false;

    }
}
