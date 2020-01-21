package com.salesforce.bazel.eclipse.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Holds the list of command options defined for the Workspace.
 * Command options come from .bazelrc file(s) (and additional files imported therein) found
 * in the standard file system locations.
 * <p>
 * https://docs.bazel.build/versions/2.0.0/guide.html#bazelrc
 * <p>
 * To see the list of options used for your workspace, run this command:  bazel info --announce_rc
 */
public class BazelWorkspaceCommandOptions {

    private BazelWorkspace bazelWorkspace;
    private Map<String, String> allExplicitOptions = new HashMap<>();
    private Map<String, Map<String, String>> contextualExplicitOptions = new HashMap<>();
    
    public BazelWorkspaceCommandOptions(BazelWorkspace bazelWorkspace) {
        this.bazelWorkspace = bazelWorkspace;
    }

    /**
     * Get the command option, regardless of context (test, build)
     */
    public String getOption(String optionName) {
        return allExplicitOptions.get(optionName);
    }

    /**
     * Contextual options are indexed based on their source context (e.g. test, build)
     * This method does not follow Inherited conventions.
     */
    public String getContextualOption(String context, String optionName) {
        Map<String, String> contextualMap = contextualExplicitOptions.get(context);
        if (contextualMap != null) {
            return contextualMap.get(optionName);
        }
        return null;
    }

    /**
     * Get the command option, regardless of context (test, build)
     */
    public String getOptionWithDefault(String optionName) {
        String optionValue = allExplicitOptions.get(optionName);
        
        if (optionValue == null) {
            // apply the default TODO build the dictionary of default values for options
        }
        
        return optionValue;
    }

    /**
     * Contextual options are indexed based on their source context (e.g. test, build)
     * This method does not follow Inherited conventions.
     */
    public String getContextualOptionWithDefault(String context, String optionName) {
        String optionValue = getContextualOption(context, optionName);
        
        if (optionValue == null) {
            // apply the default TODO build the dictionary of default values for options
        }

        return optionValue;
    }
    
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("BazelWorkspaceCommandOptions: workspace_name:");
        sb.append(bazelWorkspace.getName());
        for (String optionName : allExplicitOptions.keySet()) {
            sb.append(" [");
            sb.append(optionName);
            sb.append(":");
            sb.append(allExplicitOptions.get(optionName));
            sb.append("]");
        }
        
        return sb.toString();
    }
    
    /**
     * Method to parse options from a 'bazel test' command run with the --announce_rc option
     */
    public void parseOptionsFromOutput(List<String> outputLines) {
        // SAMPLE OUTPUT
        // INFO: Options provided by the client:
        //   Inherited 'common' options: --isatty=1 --terminal_columns=260
        // INFO: Reading rc options for 'test' from /Users/darth/dev/deathstar/.user-bazelrc:
        //   Inherited 'build' options: --javacopt=-source 8 -target 8 --host_javabase=//tools/jdk:my-linux-jdk11 --javabase=//tools/jdk:my-linux-jdk8 --stamp 
        //      --workspace_status_command tools/buildstamp/get_workspace_status --test_output=errors --verbose_explanations --explain=/tmp/bazel_explain.txt 
        //      --output_filter=^(?!@) --incompatible_no_support_tools_in_action_inputs=false --incompatible_depset_is_not_iterable=false --incompatible_new_actions_api=false 
        //      --incompatible_disable_deprecated_attr_params=false --incompatible_depset_union=false
        // INFO: Reading rc options for 'test' from /Users/darth/dev/deathstar/.base-bazelrc:
        //   'test' options: --explicit_java_test_deps=true --test_timeout=45,180,300,360 --test_tag_filters=-flaky

        for (String line : outputLines) {
            line = line.trim();

            int optionsIndex = line.indexOf("options:");
            if (optionsIndex == -1) {
                // this line has nothing to do with options, ignore
                continue;
            }

            // some lines are prefixed with "Inherited", remove that
            int inheritedIndex = line.indexOf("Inherited");
            if (inheritedIndex != -1) {
                line = line.substring(inheritedIndex+10);
                line = line.trim();
                optionsIndex = line.indexOf("options:");
            }

            // pick apart the context name from the sequence of options
            String optionsContext = line.substring(1, optionsIndex-2);  // 'build' options: ...         => build
            String optionsFullString = line.substring(optionsIndex+9);  // 'common' options: --isatty=1 => --isatty=1
            
            String[] optionsSplitList = optionsFullString.split(" --");
            for (String option : optionsSplitList) {
                if (option.startsWith("--")) {
                    // first option will still have it
                    option = option.substring(2);
                }
                String[] optionTokens = option.split("=");
                if (optionTokens.length > 2) {
                    System.err.println("Did not know how to parse option ["+option+"] from line "+line);
                    continue;
                } else if (optionTokens.length == 1) {
                    // if only the option name is provided, the value is implied to be 'true' (e.g. --stamp is interpreted as --stamp=true)
                    this.allExplicitOptions.put(optionTokens[0], "true");
                    getContextualMap(optionsContext).put(optionTokens[0], "true");
                } else {
                    this.allExplicitOptions.put(optionTokens[0], optionTokens[1]);
                    getContextualMap(optionsContext).put(optionTokens[0], optionTokens[1]);
                }
            }
        }
    }
    
    private Map<String, String> getContextualMap(String context) {
        Map<String, String> contextualMap = this.contextualExplicitOptions.get(context);
        if (contextualMap == null) {
            contextualMap = new TreeMap<>();
            contextualExplicitOptions.put(context, contextualMap);
        }
        return contextualMap;
    }
}
