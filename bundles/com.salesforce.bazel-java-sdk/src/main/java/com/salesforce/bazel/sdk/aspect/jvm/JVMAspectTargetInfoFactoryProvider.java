package com.salesforce.bazel.sdk.aspect.jvm;

import java.io.File;
import java.util.List;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfoFactoryProvider;
import com.salesforce.bazel.sdk.logging.LogHelper;

/**
 * Provider that knows how to construct AspectTargetInfo objects for JVM rule kinds. (e.g. java_library)
 */
public class JVMAspectTargetInfoFactoryProvider implements AspectTargetInfoFactoryProvider {
    private static final LogHelper LOG = LogHelper.log(JVMAspectTargetInfoFactoryProvider.class);

    @Override
    public AspectTargetInfo buildAspectTargetInfo(File aspectDataFile, JSONObject jsonObject, JSONParser jsonParser,
            String workspaceRelativePath, String kind, String label, List<String> deps, List<String> sources) {
        JVMAspectTargetInfo info = null;

        // TODO verify kind is a JVM rule type; but be careful to allow open ended (like scala, etc) as JVM support evolves

        try {
            info = new JVMAspectTargetInfo(aspectDataFile, jsonObject, jsonParser, workspaceRelativePath, kind, label,
                    deps, sources);
        } catch (Exception anyE) {
            LOG.error("Error creating the JVMAspectTargetInfo for path [{}] label [{}] kind [{}] from json {}", anyE,
                workspaceRelativePath, label, kind, jsonObject);
        }

        return info;
    }

}
