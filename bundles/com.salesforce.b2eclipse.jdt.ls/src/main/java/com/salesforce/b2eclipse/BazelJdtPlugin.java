/**
 * Copyright (c) 2020, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.salesforce.b2eclipse;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.NoSuchElementException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.framework.BundleContext;
import org.osgi.service.prefs.Preferences;

import com.google.common.base.Throwables;
import com.salesforce.b2eclipse.config.DefaultEclipseAspectLocationImpl;
import com.salesforce.b2eclipse.config.EclipseBazelProjectManager;
import com.salesforce.b2eclipse.runtime.api.ResourceHelper;
import com.salesforce.b2eclipse.runtime.impl.EclipseJavaCoreHelper;
import com.salesforce.b2eclipse.runtime.impl.EclipseResourceHelper;
import com.salesforce.bazel.eclipse.BazelNature;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.sdk.aspect.BazelAspectLocation;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.command.CommandBuilder;
import com.salesforce.bazel.sdk.command.shell.ShellCommandBuilder;
import com.salesforce.bazel.sdk.console.CommandConsoleFactory;
import com.salesforce.bazel.sdk.console.StandardCommandConsoleFactory;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.sdk.workspace.RealOperatingEnvironmentDetectionStrategy;

/**
 * The activator class controls the Bazel Eclipse plugin life cycle
 */
public class BazelJdtPlugin extends Plugin {
	private static BundleContext context;

	// The plug-in ID
	public static final String PLUGIN_ID = "com.salesforce.b2eclipse.jdt.ls"; //$NON-NLS-1$

	// The preference key for the bazel workspace root path
	public static final String BAZEL_WORKSPACE_PATH_PREF_NAME = "bazel.workspace.root";

	// GLOBAL COLLABORATORS
	// TODO move the collaborators to some other place, perhaps a dedicated static context object

	public static final String PREFERENCE_WORKSPACE_ROOT_DIRECTORY = "bazelWorkspaceRootDirectory";

	private static Preferences pluginPreferences =
			Platform.getPreferencesService().getRootNode().node(InstanceScope.SCOPE).node(PLUGIN_ID);

	/**
	 * The location on disk that stores the Bazel workspace associated with the Eclipse workspace. Currently, we only
	 * support one Bazel workspace in an Eclipse workspace so this is a static singleton.
	 */
	private static File bazelWorkspaceRootDirectory = getBazelWorkspaceRootDirectoryOnStart();

	/**
	 * Facade that enables the plugin to execute the bazel command line tool outside of a workspace
	 */
	private static BazelCommandManager bazelCommandManager;

	/**
	 * Runs bazel commands in the loaded workspace.
	 */
	private static BazelWorkspaceCommandRunner bazelWorkspaceCommandRunner;

	/**
	 * ResourceHelper is a useful singleton for looking up workspace/projects from the Eclipse environment
	 */
	private static ResourceHelper resourceHelper;

	/**
	 * JavaCoreHelper is a useful singleton for working with Java projects in the Eclipse workspace
	 */
	private static JavaCoreHelper javaCoreHelper;
	
    
    /**
     * ProjectManager manages all of the imported projects
     */
    private static BazelProjectManager bazelProjectManager;

	/**
	 * Looks up the operating environment (e.g. OS type)
	 */
	private static OperatingEnvironmentDetectionStrategy osEnvStrategy;

	/**
	 * The Bazel workspace that is in scope. Currently, we only support one Bazel workspace in an Eclipse workspace so
	 * this is a static singleton.
	 */
	private static BazelWorkspace bazelWorkspace = null;

	// Command to find bazel path on windows
	public static final String WIN_BAZEL_FINDE_COMMAND = "where bazel";

	// Command to find bazel path on linux or mac
	public static final String LINUX_BAZEL_FINDE_COMMAND = "which bazel";

	public static final String BAZEL_EXECUTABLE_ENV_VAR = "BAZEL_EXECUTABLE";

	public static final String BAZEL_EXECUTABLE_DEFAULT_PATH = "/usr/local/bin/bazel";

	// LIFECYCLE

	/**
	 * The constructor
	 */
	public BazelJdtPlugin() {

	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	@Override
	public void start(BundleContext bundleContext) throws Exception {
		super.start(bundleContext);

		BazelJdtPlugin.context = bundleContext;

		BazelAspectLocation aspectLocation = new DefaultEclipseAspectLocationImpl();

		CommandConsoleFactory consoleFactory = new StandardCommandConsoleFactory();
		CommandBuilder commandBuilder = new ShellCommandBuilder(consoleFactory);

		ResourceHelper eclipseResourceHelper = new EclipseResourceHelper();
		JavaCoreHelper eclipseJavaCoreHelper = new EclipseJavaCoreHelper();
		OperatingEnvironmentDetectionStrategy osEnvStrategy = new RealOperatingEnvironmentDetectionStrategy();
		
        BazelProjectManager projectMgr = new EclipseBazelProjectManager(eclipseResourceHelper, eclipseJavaCoreHelper);

		startInternal(aspectLocation, commandBuilder, consoleFactory, eclipseResourceHelper, eclipseJavaCoreHelper, osEnvStrategy, projectMgr);
		reloadExistingProjects();
	}

	/**
	 * This is the inner entrypoint where the initialization really begins. Both the real activation entrypoint (when
	 * running in Eclipse, seen above) and the mocking framework call in here. When running for real, the passed
	 * collaborators are all the real ones, when running mock tests the collaborators are mocks.
	 */
	public static void startInternal(BazelAspectLocation aspectLocation, CommandBuilder commandBuilder, CommandConsoleFactory consoleFactory,
			ResourceHelper rh, JavaCoreHelper javac, OperatingEnvironmentDetectionStrategy osEnv, BazelProjectManager projectMgr) {
		// reset internal state (this is so tests run in a clean env)
		bazelWorkspace = null;
		// global collaborators
		resourceHelper = rh;
		javaCoreHelper = javac;
		osEnvStrategy = osEnv;
		bazelProjectManager = projectMgr;

		File bazelPathFile = new File(getBazelPath());
		bazelCommandManager = new BazelCommandManager(aspectLocation, commandBuilder, consoleFactory, bazelPathFile);

	}

	public static String getBazelPath() {
		String path = getEnvBazelPath();

		if (path == null) {
			path = getOSBazelPath();
		}

		if (path == null) {
			path = BAZEL_EXECUTABLE_DEFAULT_PATH;
			BazelJdtPlugin.logWarning("BazelJDTPlugin could not find Bazel path, was used standart path " + path);
		}

		return path;
	}

	public static String getEnvBazelPath() {
		return System.getenv(BAZEL_EXECUTABLE_ENV_VAR);
	}

    /**
     * Provides details of the operating environment (OS, real vs. tests, etc)
     */
    public static OperatingEnvironmentDetectionStrategy getOperatingEnvironmentDetectionStrategy() {
        return osEnvStrategy;
    }
    
	public static String getOSBazelPath() {
		String path = null;
		String command = null;
		if (Platform.getOS().equals(Platform.OS_WIN32)) {
			command = WIN_BAZEL_FINDE_COMMAND;
		}
		if (Platform.getOS().equals(Platform.OS_LINUX) || Platform.getOS().equals(Platform.OS_MACOSX)) {
			command = LINUX_BAZEL_FINDE_COMMAND;
		}

		try (BufferedReader reader =
				new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec(command).getInputStream()))) {
			path = reader.lines().findFirst().get();
		} catch (IOException | NoSuchElementException e) {
			path = null;
		}

		return path;
	}

	// COLLABORATORS
	// TODO move the collaborators to some other place, perhaps a dedicated static context object

	/**
	 * Returns the model abstraction for the Bazel workspace
	 */
	public static BazelWorkspace getBazelWorkspace() {
		return bazelWorkspace;
	}

	/**
	 * Has the Bazel workspace location been imported/loaded? This is a good sanity check before doing any operation
	 * related to Bazel or Bazel Java projects.
	 */
	public static boolean hasBazelWorkspaceRootDirectory() {
		return bazelWorkspace.hasBazelWorkspaceRootDirectory();
	}

	public static File getBazelWorkspaceRootDirectoryOnStart() {
		String path = pluginPreferences.get(PREFERENCE_WORKSPACE_ROOT_DIRECTORY, null);
		return path != null ? new File(path) : null;
	}

	/**
	 * Returns the location on disk where the Bazel workspace is located. There must be a WORKSPACE file in this
	 * location. Prior to importing/opening a Bazel workspace, this location will be null
	 */
	public static File getBazelWorkspaceRootDirectory() {
		return bazelWorkspace.getBazelWorkspaceRootDirectory();
	}

	/**
	 * Sets the location on disk where the Bazel workspace is located. There must be a WORKSPACE file in this location.
	 * Changing this location is a big deal, so use this method only during setup/import.
	 */
	public static void setBazelWorkspaceRootDirectory(String workspaceName, File rootDirectory) {
		File workspaceFile = new File(rootDirectory, "WORKSPACE");
		if (!workspaceFile.exists()) {
			workspaceFile = new File(rootDirectory, "WORKSPACE.bazel");
			if (!workspaceFile.exists()) {
				new IllegalArgumentException();
				logError(
						"BazelPluginActivator could not set the Bazel workspace directory as there is no WORKSPACE file here: " + rootDirectory.getAbsolutePath());
				return;
			}
		}
		bazelWorkspace = new BazelWorkspace(workspaceName, rootDirectory, osEnvStrategy);
		BazelWorkspaceCommandRunner commandRunner = getWorkspaceCommandRunner();
		bazelWorkspace.setBazelWorkspaceMetadataStrategy(commandRunner);
		bazelWorkspace.setBazelWorkspaceCommandRunner(commandRunner);
	}

	/**
	 * Returns the location on disk where the Bazel workspace is located. There must be a WORKSPACE file in this
	 * location. Prior to importing/opening a Bazel workspace, this location will be null
	 */
	public static String getBazelWorkspaceRootDirectoryPath() {
		if (bazelWorkspaceRootDirectory == null) {
			new Throwable().printStackTrace();
			BazelJdtPlugin.logError(
					"BazelPluginActivator was asked for the Bazel workspace root directory before it is determined.");
			return null;
		}
		return bazelWorkspaceRootDirectory.getAbsolutePath();
	}

	//	/**
	//	 * Sets the location on disk where the Bazel workspace is located. There must be a WORKSPACE file in this location.
	//	 * Changing this location is a big deal, so use this method only during setup/import.
	//	 */
	//	public static void setBazelWorkspaceRootDirectory(File dir) {
	//		File workspaceFile = new File(dir, "WORKSPACE");
	//		if (!workspaceFile.exists()) {
	//			new Throwable().printStackTrace();
	//			BazelJdtPlugin.logError(
	//					"BazelPluginActivator could not set the Bazel workspace directory as there is no WORKSPACE file here: "
	//							+ dir.getAbsolutePath());
	//			return;
	//		}
	//		pluginPreferences.put(PREFERENCE_WORKSPACE_ROOT_DIRECTORY, dir.getAbsolutePath());
	//		bazelWorkspaceRootDirectory = dir;
	//		try {
	//			// forces the application to save the preferences
	//			pluginPreferences.flush();
	//		} catch (BackingStoreException e) {
	//			BazelJdtPlugin.logError(e.getMessage());
	//		}
	//	}

	/**
	 * Returns the unique instance of {@link BazelCommandManager}, the facade enables the plugin to execute the bazel
	 * command line tool.
	 */
	public static BazelCommandManager getBazelCommandManager() {
		return bazelCommandManager;
	}

	/**
	 * Once the workspace is set, the workspace command runner is available. Otherwise returns null
	 */
	public static BazelWorkspaceCommandRunner getWorkspaceCommandRunner() {
		if (bazelWorkspaceCommandRunner == null) {
			if (bazelWorkspace == null) {
				return null;
			}
			if (bazelWorkspace.hasBazelWorkspaceRootDirectory()) {
				bazelWorkspaceCommandRunner = bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);
			}
		}
		return bazelWorkspaceCommandRunner;
	}
	
    
    /**
     * Returns the manager for imported projects
     *
     * @return
     */
    public static BazelProjectManager getBazelProjectManager() {
        return bazelProjectManager;
    }

	/**
	 * Returns the unique instance of {@link ResourceHelper}, this helper helps retrieve workspace and project objects
	 * from the environment
	 */
	public static ResourceHelper getResourceHelper() {
		return resourceHelper;
	}

	/**
	 * Returns the unique instance of {@link JavaCoreHelper}, this helper helps manipulate the Java configuration of a
	 * Java project
	 */
	public static JavaCoreHelper getJavaCoreHelper() {
		return javaCoreHelper;
	}

	public static void log(IStatus status) {
		if (context != null) {
			Platform.getLog(BazelJdtPlugin.context.getBundle()).log(status);
		}
	}

	public static void log(CoreException e) {
		log(e.getStatus());
	}

	public static void logError(String message) {
		if (context != null) {
			log(new Status(IStatus.ERROR, context.getBundle().getSymbolicName(), message));
		}
	}

	public static void logInfo(String message) {
		if (context != null) {
			log(new Status(IStatus.INFO, context.getBundle().getSymbolicName(), message));
		}
	}

	public static void logWarning(String message) {
		if (context != null) {
			log(new Status(IStatus.WARNING, context.getBundle().getSymbolicName(), message));
		}
	}

	public static void logException(Throwable ex) {
		if (context != null) {
			String message = ex.getMessage();
			if (message == null) {
				message = Throwables.getStackTraceAsString(ex);
			}
			logException(message, ex);
		}
	}

	public static void logException(String message, Throwable ex) {
		if (context != null) {
			log(new Status(IStatus.ERROR, context.getBundle().getSymbolicName(), message, ex));
		}
	}

    private static void reloadExistingProjects() {
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        IProject[] projects = workspaceRoot.getProjects();
        Arrays.stream(projects)//
                .filter(IProject::isOpen)//
                .filter(project -> {
                    try {
                        return project.hasNature(BazelNature.BAZEL_NATURE_ID);
                    } catch (CoreException e) {
                        return false;
                    }
                })//
                .map(project -> new BazelProject(project.getName(), project))//
                .forEachOrdered(getBazelProjectManager()::addProject);
    }
}
