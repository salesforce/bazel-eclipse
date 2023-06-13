package com.intellij.openapi.util.io;

import static org.eclipse.core.runtime.IPath.SEPARATOR;
import static org.eclipse.core.runtime.IPath.forPosix;

import java.io.File;

/**
 * This is not a copy but re-implemented to avoid changing too much of Bazel IJ plug-in code.
 */
public class FileUtil {

	private static final boolean notPosix = File.separatorChar != SEPARATOR;

	/**
	 * Indicates if the given path is within the ancestor.
	 * <p>
	 * Note, comparison will be done entirely based on Posix
	 * </p>
	 * @param ancestor
	 * @param path
	 * @param strict
	 * @return
	 */
	public static boolean isAncestor(String ancestor, String path, boolean strict) {
		// ignore strict
		if(notPosix) {
			ancestor = ancestor.replace(File.separatorChar, SEPARATOR);
			path = path.replace(File.separatorChar, SEPARATOR);
		}
		return forPosix(ancestor).isPrefixOf(forPosix(path));
	}

}
