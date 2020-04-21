package com.salesforce.bazel.eclipse.builder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;
import org.junit.Test;

import com.google.common.collect.Multimap;
import com.salesforce.bazel.eclipse.model.BazelLabel;
import com.salesforce.bazel.eclipse.model.BazelMarkerDetails;

public class BazelBuilderTest {

    @Test
    public void testGetDownstreamProjectsOf() throws Exception {
        // A -> (B, C)
        // B -> C
        // C -> D
        // starting with D, find all projects that depend on D, including transitives
        IJavaProject A = getMockedProject("A", new String[]{"B", "C"});
        IJavaProject B = getMockedProject("B", new String[]{"C"});
        IJavaProject C = getMockedProject("C", new String[]{"D"});
        IJavaProject D = getMockedProject("D", new String[]{});
        IJavaProject unrelated = getMockedProject("unrelated", new String[]{"Z"});

        Set<IProject> downstreams = BazelBuilder.getDownstreamProjectsOf(D.getProject(),
                new IJavaProject[]{A, B, C, D, unrelated});

        assertEquals(3, downstreams.size());
        assertTrue(downstreams.contains(A.getProject()));
        assertTrue(downstreams.contains(B.getProject()));
        assertTrue(downstreams.contains(C.getProject()));
    }

    // production breaks if the impl is a TreeSet, so we test for that explicitly here
    // (IProject's impl doesn't implement Comparable)
    @Test
    public void testDownstreamSetImpl() throws Exception {
        IJavaProject A = getMockedProject("A", new String[]{});

        Set<IProject> downstreams = BazelBuilder.getDownstreamProjectsOf(A.getProject(), new IJavaProject[]{});

        assertFalse("Do not use a TreeSet", downstreams instanceof TreeSet);
    }

    @Test
    public void testAssignErrorsToOwningProject() throws Exception {
        IProject project1 = getMockedProject("P1").getProject();
        BazelLabel l1 = new BazelLabel("projects/libs/lib1:*");
        BazelMarkerDetails error1 = new BazelMarkerDetails("projects/libs/lib1/src/Test.java", 21, "foo");
        IProject project2 = getMockedProject("P2").getProject();
        BazelLabel l2 = new BazelLabel("projects/libs/lib2:*");
        BazelMarkerDetails error2 = new BazelMarkerDetails("projects/libs/lib2/src/Test2.java", 22, "blah");
        Map<BazelLabel, IProject> labelToProject = new HashMap<>();
        labelToProject.put(l1, project1);
        labelToProject.put(l2, project2);
        IProject rootProject = getMockedProject("ROOT").getProject();

        Multimap<IProject, BazelMarkerDetails> projectToErrors =
                BazelBuilder.assignErrorsToOwningProject(Arrays.asList(error1, error2), labelToProject, Optional.of(rootProject));

        assertEquals(2, projectToErrors.size());
        Collection<BazelMarkerDetails> p1Errors = projectToErrors.get(project1);
        assertEquals(1, p1Errors.size());
        BazelMarkerDetails p1Error = p1Errors.iterator().next();
        assertEquals("/src/Test.java", p1Error.getResourcePath());
        assertEquals(21, p1Error.getLineNumber());
        assertEquals("foo", p1Error.getDescription());
        Collection<BazelMarkerDetails> p2Errors = projectToErrors.get(project2);
        assertEquals(1, p2Errors.size());
        BazelMarkerDetails p2Error = p2Errors.iterator().next();
        assertEquals("/src/Test2.java", p2Error.getResourcePath());
        assertEquals(22, p2Error.getLineNumber());
        assertEquals("blah", p2Error.getDescription());
    }

    @Test
    public void testUnassignedErrors() throws Exception {
        IProject project1 = getMockedProject("P1").getProject();
        BazelLabel l1 = new BazelLabel("projects/libs/lib1:*");
        BazelMarkerDetails error1 = new BazelMarkerDetails("projects/libs/lib1/src/Test.java", 21, "foo");
        Map<BazelLabel, IProject> labelToProject = Collections.singletonMap(l1, project1);
        BazelMarkerDetails error2 = new BazelMarkerDetails("projects/libs/lib2/src/Test2.java", 22, "blah");
        IProject rootProject = getMockedProject("ROOT").getProject();

        Multimap<IProject, BazelMarkerDetails> projectToErrors =
                BazelBuilder.assignErrorsToOwningProject(Arrays.asList(error1, error2), labelToProject, Optional.of(rootProject));

        assertEquals(2, projectToErrors.size());
        Collection<BazelMarkerDetails> rootLevelErrors = projectToErrors.get(rootProject);
        BazelMarkerDetails rootError = rootLevelErrors.iterator().next();
        assertEquals("/WORKSPACE", rootError.getResourcePath());
        assertEquals(0, rootError.getLineNumber());
        assertTrue(rootError.getDescription().startsWith(BazelBuilder.UNKNOWN_PROJECT_ERROR_MSG_PREFIX));
        assertTrue(rootError.getDescription().contains("projects/libs/lib2/src/Test2.java"));
        assertTrue(rootError.getDescription().contains("blah"));
    }

    private IJavaProject getMockedProject(String projectName) throws Exception {
        return getMockedProject(projectName, new String[]{});
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
