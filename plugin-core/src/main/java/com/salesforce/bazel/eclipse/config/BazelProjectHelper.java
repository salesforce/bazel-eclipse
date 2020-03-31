package com.salesforce.bazel.eclipse.config;

import java.io.File;
import java.io.IOException;

/**
 * Static utilities.
 */
public class BazelProjectHelper {
  
  /**
   * Resolve softlinks and other abstractions in the workspace paths.
   */
  static File getCanonicalFileSafely(File directory) {
      if (directory == null) {
          return null;
      }
      try {
          directory = directory.getCanonicalFile();
      } catch (IOException ioe) {
          ioe.printStackTrace();
      }
      return directory;
  }
}
