package com.salesforce.bazel.eclipse.classpath;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class JavaLanguageLevelHelperTest {

    @Test
    public void testHappyPath_Jdk8() {
        String javacoptString = "-source 8 -target 8";
        
        String level = JavaLanguageLevelHelper.getSourceLevel(javacoptString);
        assertEquals("8", level);
        int levelInt = JavaLanguageLevelHelper.getSourceLevelAsInt(javacoptString);
        assertEquals(8, levelInt);
        level = JavaLanguageLevelHelper.getTargetLevel(javacoptString);
        assertEquals("8", level);
        levelInt = JavaLanguageLevelHelper.getTargetLevelAsInt(javacoptString);
        assertEquals(8, levelInt);
    }
    
    @Test
    public void testHappyPath_Jdk11() {
        String javacoptString = "-source 11 -target 11";
        
        String level = JavaLanguageLevelHelper.getSourceLevel(javacoptString);
        assertEquals("11", level);
        int levelInt = JavaLanguageLevelHelper.getSourceLevelAsInt(javacoptString);
        assertEquals(11, levelInt);
        level = JavaLanguageLevelHelper.getTargetLevel(javacoptString);
        assertEquals("11", level);
        levelInt = JavaLanguageLevelHelper.getTargetLevelAsInt(javacoptString);
        assertEquals(11, levelInt);
    }

    @Test
    public void testHappyPath_Mixed() {
        String javacoptString = "-source 8 -target 11";
        
        String level = JavaLanguageLevelHelper.getSourceLevel(javacoptString);
        assertEquals("8", level);
        level = JavaLanguageLevelHelper.getTargetLevel(javacoptString);
        assertEquals("11", level);
    }

    // EDGE CASES
    
    @Test
    public void testEdge_ExtraSpaces() {
        String javacoptString = "-source   8     -target      11";
        
        String level = JavaLanguageLevelHelper.getSourceLevel(javacoptString);
        assertEquals("8", level);
        level = JavaLanguageLevelHelper.getTargetLevel(javacoptString);
        assertEquals("11", level);
    }

    @Test
    public void testEdge_Unparsed() {
        String javacoptString = "javacopt=\"-source 8 -target 11\"";
        
        String level = JavaLanguageLevelHelper.getSourceLevel(javacoptString);
        assertEquals("8", level);
        level = JavaLanguageLevelHelper.getTargetLevel(javacoptString);
        assertEquals("11", level);
    }

    @Test
    public void testEdge_Swapped() {
        String javacoptString = "-target 11 -source 8";
        
        String level = JavaLanguageLevelHelper.getSourceLevel(javacoptString);
        assertEquals("8", level);
        level = JavaLanguageLevelHelper.getTargetLevel(javacoptString);
        assertEquals("11", level);
    }

    @Test
    public void testEdge_OnlySource() {
        String javacoptString = "-source 8";
        
        String level = JavaLanguageLevelHelper.getSourceLevel(javacoptString);
        assertEquals("8", level);
        level = JavaLanguageLevelHelper.getTargetLevel(javacoptString);
        assertEquals("11", level);
    }

    @Test
    public void testEdge_OnlyTarget() {
        String javacoptString = "-target 8";
        
        String level = JavaLanguageLevelHelper.getSourceLevel(javacoptString);
        assertEquals("11", level);
        level = JavaLanguageLevelHelper.getTargetLevel(javacoptString);
        assertEquals("8", level);
    }

    @Test
    public void testEdge_Garbage() {
        String javacoptString = "-src 8 -tar=9";
        
        String level = JavaLanguageLevelHelper.getSourceLevel(javacoptString);
        assertEquals("11", level);
        level = JavaLanguageLevelHelper.getTargetLevel(javacoptString);
        assertEquals("11", level);
    }
}
