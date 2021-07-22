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
import com.salesforce.bazel.sdk.logging.LogHelper;

/**
 * Factory for AspectTargetInfo instances, using the JSON emitted from the aspect. Each rule type will have a different
 * JSON format, and therefore the factory is specific to a rule type (e.g. java_library).
 */
public class AspectTargetInfoFactory {

    private static final LogHelper LOG = LogHelper.log(AspectTargetInfoFactory.class);
    public static final String ASPECT_FILENAME_SUFFIX = ".bzljavasdk-data.json";

    protected static List<AspectTargetInfoFactoryProvider> providers = new ArrayList<>();
    static {
        providers.add(new JVMAspectTargetInfoFactoryProvider());
    }

    /**
     * During initialization, add providers that can parse target specific json in the apsect files.
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
            if (buildInfo == null) {
                // bug in the aspect parsing code
                LOG.error("The aspect file could not be parsed for aspect path {}",
                    aspectFile.getAbsolutePath());
                continue;
            }

            String labelPath = buildInfo.getLabelPath();
            if (labelPath != null) {
                infos.put(labelPath, buildInfo);
            } else {
                // bug in the aspect parsing code
                LOG.error("Bug in the aspect parsing code, the label is null for package path {} for aspect file {}",
                    buildInfo.workspaceRelativePath, aspectFile.getAbsolutePath());
            }
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
                LOG.error("JSON file {} has illegal characters: {}", aspectFile.getAbsolutePath(),
                    aspectFile.getAbsolutePath());
                throw new IllegalArgumentException(je);
            }
            buildInfo = loadAspectFromJson(aspectFile, jsonObject, jsonParser);
        } else {
            LOG.error("Aspect JSON file {} is missing.", aspectFile.getAbsolutePath());
        }
        return buildInfo;
    }

    // INTERNAL

    static AspectTargetInfo loadAspectFromJson(File aspectDataFile, JSONObject aspectObject, JSONParser jsonParser) {
        AspectTargetInfo info = null;

        try {
            List<String> deps = loadDeps(aspectObject);

            String build_file_artifact_location = "test";//aspectObject.getString("build_file_artifact_location");
            String kind = (String) aspectObject.get("kind_string");
            if (kind == null) {
                LOG.error(
                    "Aspect file {} is missing the kind_string property; this is likely a data file created by an older version of the aspect",
                    aspectDataFile);
            }
            String label = loadLabel(aspectObject);

            for (AspectTargetInfoFactoryProvider provider : providers) {
                info = provider.buildAspectTargetInfo(aspectDataFile, aspectObject, jsonParser,
                    build_file_artifact_location, kind, label, deps);
                if (info != null) {
                    break;
                }
            }
            if (info == null) {
                LOG.info("Could not find an AspectTargetInfoFactoryProvider for rule kind {}", kind);
            }
        } catch (Exception anyE) {
            //System.err.println("Error parsing Bazel aspect info from file "+aspectDataFile.getAbsolutePath()+". Error: "+anyE.getMessage());
            throw new IllegalArgumentException(anyE);
        }
        return info;
    }

    private static String loadLabel(JSONObject aspectObject) throws Exception {
        String label = null;

        JSONObject keyObj = (JSONObject) aspectObject.get("key");
        if (keyObj != null) {
            Object labelObj = keyObj.get("label");
            if (labelObj != null) {
                label = labelObj.toString();
            }
        }

        return label;
    }

    private static List<String> loadDeps(JSONObject aspectObject) throws Exception {
        List<String> list = new ArrayList<>();
        if (aspectObject == null) {
            return list;
        }
        JSONArray depArray = (JSONArray) aspectObject.get("deps");
        if (depArray != null) {
            for (Object dep : depArray) {
                JSONObject depObj = (JSONObject) dep;
                JSONObject targetObj = (JSONObject) depObj.get("target");
                if (targetObj != null) {
                    Object labelObj = targetObj.get("label");
                    if (labelObj != null) {
                        list.add(labelObj.toString());
                    }
                }
            }
        }
        return list;
    }

}
