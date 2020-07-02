package com.salesforce.bazel.sdk.lang.jvm;

import com.salesforce.bazel.sdk.project.BazelProject;

/**
 * Entry in a classpath for the JVM that points to a Jar with bytecode.
 */
public class JvmClasspathEntry implements Comparable<JvmClasspathEntry> {
	
	// TODO make classpath entries better typed (jar, project) 
	
	// Jar Entry
	public String pathToJar;
	public String pathToSourceJar;
	public boolean isTestJar;
	
	// Project Entry
	public BazelProject bazelProject;

	public JvmClasspathEntry(String pathToJar, boolean isTestJar) {
		this.pathToJar = pathToJar;
		this.isTestJar = isTestJar;
	}

	public JvmClasspathEntry(String pathToJar, String pathToSourceJar, boolean isTestJar) {
		this.pathToJar = pathToJar;
		this.pathToSourceJar = pathToSourceJar;
		this.isTestJar = isTestJar;
	}
	
	public JvmClasspathEntry(BazelProject bazelProject) {
		this.bazelProject = bazelProject;
	}

	@Override
	public int compareTo(JvmClasspathEntry otherEntry) {
		return this.pathToJar.compareTo(otherEntry.pathToJar);
	}
}
