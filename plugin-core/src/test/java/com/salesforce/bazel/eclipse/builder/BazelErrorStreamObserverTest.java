package com.salesforce.bazel.eclipse.builder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;
import org.junit.Test;

import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelProblem;
import com.salesforce.bazel.sdk.project.BazelProject;

public class BazelErrorStreamObserverTest {
    
    @Test
    public void testAssignErrorsToOwningProject() throws Exception {
        IProject project1 = getMockedProject("P1").getProject();
        BazelLabel l1 = new BazelLabel("projects/libs/lib1:*");
        BazelProblem error1 = BazelProblem.createError("projects/libs/lib1/src/Test.java", 21, "foo");
        IProject project2 = getMockedProject("P2").getProject();
        BazelLabel l2 = new BazelLabel("projects/libs/lib2:*");
        BazelProblem error2 = BazelProblem.createError("projects/libs/lib2/src/Test2.java", 22, "blah");
        Map<BazelLabel, BazelProject> labelToProject = new HashMap<>();
        labelToProject.put(l1, new BazelProject("P1", project1));
        labelToProject.put(l2, new BazelProject("P2", project2));
        IProject rootProject = getMockedProject("ROOT").getProject();

        Map<IProject, List<BazelProblem>> projectToErrors =
                BazelErrorStreamObserver.assignErrorsToOwningProject(Arrays.asList(error1, error2), labelToProject, rootProject);

        assertEquals(2, projectToErrors.size());
        Collection<BazelProblem> p1Errors = projectToErrors.get(project1);
        assertEquals(1, p1Errors.size());
        BazelProblem p1Error = p1Errors.iterator().next();
        assertEquals("/src/Test.java", p1Error.getResourcePath());
        assertEquals(21, p1Error.getLineNumber());
        assertEquals("foo", p1Error.getDescription());
        Collection<BazelProblem> p2Errors = projectToErrors.get(project2);
        assertEquals(1, p2Errors.size());
        BazelProblem p2Error = p2Errors.iterator().next();
        assertEquals("/src/Test2.java", p2Error.getResourcePath());
        assertEquals(22, p2Error.getLineNumber());
        assertEquals("blah", p2Error.getDescription());
    }
    
    @Test
    public void testUnassignedErrors() throws Exception {
        IProject project1 = getMockedProject("P1").getProject();
        BazelLabel l1 = new BazelLabel("projects/libs/lib1:*");
        BazelProblem error1 = BazelProblem.createError("projects/libs/lib1/src/Test.java", 21, "foo");
        Map<BazelLabel, BazelProject> labelToProject = Collections.singletonMap(l1, new BazelProject("P1", project1));
        BazelProblem error2 = BazelProblem.createError("projects/libs/lib2/src/Test2.java", 22, "blah");
        IProject rootProject = getMockedProject("ROOT").getProject();

        Map<IProject, List<BazelProblem>> projectToErrors =
                BazelErrorStreamObserver.assignErrorsToOwningProject(Arrays.asList(error1, error2), labelToProject, rootProject);

        assertEquals(2, projectToErrors.size());
        Collection<BazelProblem> rootLevelErrors = projectToErrors.get(rootProject);
        BazelProblem rootError = rootLevelErrors.iterator().next();
        assertEquals("/WORKSPACE", rootError.getResourcePath());
        assertEquals(0, rootError.getLineNumber());
        assertTrue(rootError.getDescription().startsWith(BazelErrorStreamObserver.UNKNOWN_PROJECT_ERROR_MSG_PREFIX));
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
