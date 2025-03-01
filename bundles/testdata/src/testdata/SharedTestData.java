package testdata;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

/**
 * Convenience access to shared test data
 */
public interface SharedTestData {

    String WORKSPACE_001 = "/workspaces/001";

    String WORKSPACE_002 = "/workspaces/001";

    IPath BAZELPROJECT_FILE = new Path(".bazelproject");

}
