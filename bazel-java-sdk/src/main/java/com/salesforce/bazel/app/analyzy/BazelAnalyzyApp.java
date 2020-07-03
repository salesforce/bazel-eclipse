package com.salesforce.bazel.app.analyzy;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.salesforce.bazel.sdk.aspect.AspectDependencyGraphBuilder;
import com.salesforce.bazel.sdk.aspect.AspectPackageInfo;
import com.salesforce.bazel.sdk.aspect.AspectPackageInfos;
import com.salesforce.bazel.sdk.aspect.BazelAspectLocation;
import com.salesforce.bazel.sdk.aspect.LocalBazelAspectLocation;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandOptions;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.command.CommandBuilder;
import com.salesforce.bazel.sdk.command.shell.ShellCommandBuilder;
import com.salesforce.bazel.sdk.console.CommandConsoleFactory;
import com.salesforce.bazel.sdk.console.StandardCommandConsoleFactory;
import com.salesforce.bazel.sdk.model.BazelDependencyGraph;
import com.salesforce.bazel.sdk.model.BazelPackageInfo;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.util.BazelPathHelper;
import com.salesforce.bazel.sdk.workspace.BazelWorkspaceScanner;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.sdk.workspace.ProjectOrderResolver;
import com.salesforce.bazel.sdk.workspace.ProjectOrderResolverImpl;
import com.salesforce.bazel.sdk.workspace.RealOperatingEnvironmentDetectionStrategy;

/**
 * This app, as a tool, is not useful. It simply uses the Bazel Java SDK to 
 * read a Bazel workspace, compute the dependency graph, and a few other tasks.
 * <p>
 * The value in this app is the code sample, that shows how to use the SDK to
 * write tools that are actually useful.
 */
public class BazelAnalyzyApp {
	private static String bazelWorkspacePath;
	private static File bazelWorkspaceDir;
	
	// update this to your local environment
	private static final String BAZEL_EXECUTABLE = "/usr/local/bin/bazel";
	private static final String ASPECT_LOCATION = "/Users/plaird/dev/bazel-eclipse/bazel-java-sdk/aspect";
	
	private static BazelWorkspaceScanner workspaceScanner = new BazelWorkspaceScanner();

	public static void main(String[] args) throws Exception {
		parseArgs(args);

		// set up the command line env
		File bazelExecutable = new File(BAZEL_EXECUTABLE);
		File aspectDir = new File(ASPECT_LOCATION);
		BazelAspectLocation aspectLocation = new LocalBazelAspectLocation(aspectDir);
		CommandConsoleFactory consoleFactory = new StandardCommandConsoleFactory();
		CommandBuilder commandBuilder = new ShellCommandBuilder(consoleFactory);
		BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = new BazelWorkspaceCommandRunner(bazelExecutable, aspectLocation,
				commandBuilder, consoleFactory, bazelWorkspaceDir);
		
		// create the Bazel workspace SDK objects
		String workspaceName = BazelWorkspaceScanner.getBazelWorkspaceName(bazelWorkspacePath); // TODO use a File arg
		OperatingEnvironmentDetectionStrategy osDetector =  new RealOperatingEnvironmentDetectionStrategy();
		BazelWorkspace bazelWorkspace = new BazelWorkspace(workspaceName, bazelWorkspaceDir, osDetector, bazelWorkspaceCmdRunner);
		BazelWorkspaceCommandOptions bazelOptions = bazelWorkspace.getBazelWorkspaceCommandOptions();
		printBazelOptions(bazelOptions);
		
		// scan for Bazel packages
		BazelPackageInfo rootPackage = workspaceScanner.getPackages(bazelWorkspaceDir);
		printPackageListToStdOut(rootPackage);
		List<BazelPackageLocation> allPackages = rootPackage.gatherChildren();
		
		// run the Aspects to compute the dependency data
		AspectPackageInfos aspects = new AspectPackageInfos();
		Map<String, Set<AspectPackageInfo>> aspectMap = bazelWorkspaceCmdRunner.getAspectPackageInfoForPackages(allPackages, null, "BazelBuildyApp");
		for (String target : aspectMap.keySet()) {
			Set<AspectPackageInfo> aspectsForTarget = aspectMap.get(target);
			aspects.addAll(aspectsForTarget);
		}
		
		// use the dependency data to interact with the dependency graph
		BazelDependencyGraph depGraph = AspectDependencyGraphBuilder.build(aspects, false);
		Set<String> rootLabels = depGraph.getRootLabels();
		printRootLabels(rootLabels);
				
		// put them in the right order for analysis
		ProjectOrderResolver projectOrderResolver = new ProjectOrderResolverImpl();
        Iterable<BazelPackageLocation> orderedPackages = projectOrderResolver.computePackageOrder(rootPackage, aspects);
        printPackageListOrder(orderedPackages);
	}
	
	
	// HELPERS
	
	private static void parseArgs(String[] args) {
		if (args.length < 1) {
			throw new IllegalArgumentException("Usage: java -jar buildyapp.jar [Bazel workspace absolute path]");
		}
		bazelWorkspacePath = args[0];
		bazelWorkspaceDir = new File(bazelWorkspacePath);
		bazelWorkspaceDir = BazelPathHelper.getCanonicalFileSafely(bazelWorkspaceDir);
		
		if (!bazelWorkspaceDir.exists()) {
			throw new IllegalArgumentException("Usage: java -jar buildyapp.jar [Bazel workspace absolute path]");
		}
		if (!bazelWorkspaceDir.isDirectory()) {
			throw new IllegalArgumentException("Usage: java -jar buildyapp.jar [Bazel workspace absolute path]");
		}
	}
	
	private static void printBazelOptions(BazelWorkspaceCommandOptions bazelOptions) {
		System.out.println("\nBazel configuration options for the workspace:");
		System.out.println(bazelOptions.toString());
	}
	
	private static void printPackageListToStdOut(BazelPackageInfo rootPackage) {
		System.out.println("\nFound packages eligible for import:");
		printPackage(rootPackage, "");
	}
	
	private static void printPackage(BazelPackageInfo pkg, String prefix) {
		if (pkg.isWorkspaceRoot()) {
			System.out.println("WORKSPACE");
		} else {
			System.out.println(prefix+pkg.getBazelPackageNameLastSegment());
		}
		for (BazelPackageInfo child : pkg.getChildPackageInfos()) {
			printPackage(child, prefix+"   ");
		}
	}

	private static void printRootLabels(Set<String> rootLabels) {
		System.out.println("\nRoot labels in the dependency tree (nothing depends on them):");
		for (String label : rootLabels) {
			System.out.println("  "+label);
		}
	}

	private static void printPackageListOrder(Iterable<BazelPackageLocation> postOrderedModules) {
		System.out.println("\nPackages in import order:");
		for (BazelPackageLocation loc : postOrderedModules) {
			System.out.println("  "+loc.getBazelPackageName());
		}
	}
}
