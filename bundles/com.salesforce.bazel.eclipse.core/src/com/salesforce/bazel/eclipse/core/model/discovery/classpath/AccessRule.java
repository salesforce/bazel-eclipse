package com.salesforce.bazel.eclipse.core.model.discovery.classpath;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IAccessRule;

/**
 * Modifiable version of {@link IAccessRule}
 */
public record AccessRule(IPath pattern, int kind) {

}
