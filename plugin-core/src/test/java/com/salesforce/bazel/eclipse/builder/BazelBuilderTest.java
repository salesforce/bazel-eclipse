package com.salesforce.bazel.eclipse.builder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;
import org.junit.Test;

public class BazelBuilderTest {

    @Test
    public void testGetDownstreamProjectsOf() throws Exception {
        // A -> (B, C)
        // B -> C
        // C -> D
        // starting with D, find all projects that depend on D, including transitives
        IJavaProject A = getMockedProject("A", new String[] { "B", "C" });
        IJavaProject B = getMockedProject("B", new String[] { "C" });
        IJavaProject C = getMockedProject("C", new String[] { "D" });
        IJavaProject D = getMockedProject("D", new String[] {});
        IJavaProject unrelated = getMockedProject("unrelated", new String[] { "Z" });

        Set<IProject> downstreams =
                BazelBuilder.getDownstreamProjectsOf(D.getProject(), new IJavaProject[] { A, B, C, D, unrelated });

        assertEquals(3, downstreams.size());
        assertTrue(downstreams.contains(A.getProject()));
        assertTrue(downstreams.contains(B.getProject()));
        assertTrue(downstreams.contains(C.getProject()));
    }

    // production breaks if the impl is a TreeSet, so we test for that explicitly here
    // (IProject's impl doesn't implement Comparable)
    @Test
    public void testDownstreamSetImpl() throws Exception {
        IJavaProject A = getMockedProject("A", new String[] {});

        Set<IProject> downstreams = BazelBuilder.getDownstreamProjectsOf(A.getProject(), new IJavaProject[] {});

        assertFalse("Do not use a TreeSet", downstreams instanceof TreeSet);
    }

    private IJavaProject getMockedProject(String projectName, String[] requiredProjectNames) throws Exception {
        IJavaProject javaProject = mock(IJavaProject.class);
        when(javaProject.getRequiredProjectNames()).thenReturn(requiredProjectNames);
        IProject project = mock(IProject.class);
        when(javaProject.getProject()).thenReturn(project);
        when(project.getName()).thenReturn(projectName);
        return javaProject;
    }

}
