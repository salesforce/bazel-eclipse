package com.salesforce.bazel.sdk.command;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import com.salesforce.bazel.sdk.command.ArgumentSplitter;

public class ArgumentSplitterTest {
    
    @Test
    public void testSplit_newline() {
        ArgumentSplitter as = new ArgumentSplitter();

        List<String> args = as.split("a1\na2=v2\na3\n");

        assertEquals(3, args.size());
        assertEquals("a1", args.get(0));
        assertEquals("a2=v2", args.get(1));
        assertEquals("a3", args.get(2));        
    }

    @Test
    public void testSplit_spaces() {
        ArgumentSplitter as = new ArgumentSplitter();

        List<String> args = as.split("  a1 a2=v2   a3  ");

        assertEquals(3, args.size());
        assertEquals("a1", args.get(0));
        assertEquals("a2=v2", args.get(1));
        assertEquals("a3", args.get(2));
    }
}