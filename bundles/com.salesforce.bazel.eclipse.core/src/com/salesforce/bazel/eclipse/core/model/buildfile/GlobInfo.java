package com.salesforce.bazel.eclipse.core.model.buildfile;

import java.util.List;

/**
 * <code>glob</code> information extracted from a macro call
 */
public record GlobInfo(List<String> include, List<String> exclude) {

}
