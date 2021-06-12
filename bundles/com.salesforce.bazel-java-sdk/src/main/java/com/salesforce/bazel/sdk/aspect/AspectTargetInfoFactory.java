package com.salesforce.bazel.sdk.aspect;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.salesforce.bazel.sdk.aspect.jvm.JVMAspectTargetInfoFactoryProvider;

/**
 * Factory for AspectTargetInfo instances, using the JSON emitted from the aspect.
 * Each rule type will have a different JSON format, and therefore the factory 
 * is specific to a rule type (e.g. java_library).
 */
public class AspectTargetInfoFactory {
    
    public static final String ASPECT_FILENAME_SUFFIX = ".bzljavasdk-build.json";
    
    protected static List<AspectTargetInfoFactoryProvider> providers = new ArrayList<>();
    static {
        providers.add(new JVMAspectTargetInfoFactoryProvider());
    }
    
    /**
     * During initialization, add providers that can parse target specific json in the
     * apsect files. 
     */
    public static void addProvider(AspectTargetInfoFactoryProvider provider) {
        providers.add(provider);
    }

    /**
     * Constructs a map of label -> @link AspectTargetInfo} from a list of file paths, parsing each files into a
     * {@link JSONObject} and then converting that {@link JSONObject} to an {@link AspectTargetInfo} object.
     */
    public static Map<String, AspectTargetInfo> loadAspectFilePaths(List<String> aspectFilePaths)
            throws IOException, InterruptedException {

        List<File> fileList = new ArrayList<>();
        for (String aspectFilePath : aspectFilePaths) {
            if (!aspectFilePath.isEmpty()) {
                fileList.add(new File(aspectFilePath));
            }
        }
        return loadAspectFiles(fileList);
    }

    /**
     * Constructs a map of label -> {@link AspectTargetInfo} from a list of files, parsing each files into a
     * {@link JSONObject} and then converting that {@link JSONObject} to an {@link AspectTargetInfo} object.
     */
    public static Map<String, AspectTargetInfo> loadAspectFiles(List<File> aspectFiles) {
        Map<String, AspectTargetInfo> infos = new HashMap<>();
        for (File aspectFile : aspectFiles) {
            AspectTargetInfo buildInfo = loadAspectFile(aspectFile);
            infos.put(buildInfo.getLabel(), buildInfo);
        }
        return infos;
    }

    /**
     * Constructs a map of label -> {@link AspectTargetInfo} from a list of files, parsing each files into a
     * {@link JSONObject} and then converting that {@link JSONObject} to an {@link AspectTargetInfo} object.
     */
    public static AspectTargetInfo loadAspectFile(File aspectFile) {
        AspectTargetInfo buildInfo = null;
        JSONParser jsonParser = new JSONParser();

        if (aspectFile.exists()) {
            JSONObject jsonObject = null;
            try {
                jsonObject = (JSONObject) jsonParser.parse(new FileReader(aspectFile));
            } catch (Exception je) {
                System.err.println("JSON file has illegal characters: " + aspectFile.getAbsolutePath()); // TODO log
                throw new IllegalArgumentException(je);
            }
            buildInfo = loadAspectFromJson(aspectFile, jsonObject, jsonParser);
        } else {
            System.err.println("Aspect JSON file is missing: " + aspectFile.getAbsolutePath()); // TODO log
        }
        return buildInfo;
    }

    // INTERNAL

    static AspectTargetInfo loadAspectFromJson(File aspectDataFile, JSONObject jsonObject, JSONParser jsonParser) {
        AspectTargetInfo info = null;

        try {
            List<String> deps = jsonArrayToStringArray(jsonObject.get("dependencies"));
            List<String> sources = jsonArrayToStringArray(jsonObject.get("sources"));

            String build_file_artifact_location = null; // object.getString("build_file_artifact_location");
            String kind = (String) jsonObject.get("kind");
            String label = (String) jsonObject.get("label");

            for (AspectTargetInfoFactoryProvider provider: providers) {
                info = provider.buildAspectTargetInfo(aspectDataFile, jsonObject, jsonParser, build_file_artifact_location, kind, label,
                        deps, sources);
                if (info != null) {
                    break;
                }
            }
            if (info == null) {
                System.out.println("Could not find an AspectTargetInfoFactoryProvider for rule kind "+kind);
            }
        } catch (Exception anyE) {
            //System.err.println("Error parsing Bazel aspect info from file "+aspectDataFile.getAbsolutePath()+". Error: "+anyE.getMessage());
            throw new IllegalArgumentException(anyE);
        }
        return info;
    }

    private static List<String> jsonArrayToStringArray(Object arrayAsObject) throws Exception {
        List<String> list = new ArrayList<>();
        if (arrayAsObject == null) {
            return list;
        }

        JSONArray array = null;
        if (arrayAsObject instanceof JSONArray) {
            array = (JSONArray) arrayAsObject;
        } else {
            return list;
        }

        for (Object o : array) {
            list.add(o.toString());
        }
        return list;
    }


}
