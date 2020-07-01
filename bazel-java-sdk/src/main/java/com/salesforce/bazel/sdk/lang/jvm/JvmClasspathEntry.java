package com.salesforce.bazel.sdk.lang.jvm;

/**
 * Entry in a classpath for the JVM that points to a Jar with bytecode.
 */
public class JvmClasspathEntry implements Comparable<JvmClasspathEntry> {
	public String pathToJar;
	public boolean isTestJar;

	public JvmClasspathEntry(String pathToJar, boolean isTestJar) {
		this.pathToJar = pathToJar;
		this.isTestJar = isTestJar;
	}

	@Override
	public int compareTo(JvmClasspathEntry otherEntry) {
		return this.pathToJar.compareTo(otherEntry.pathToJar);
	}
}
