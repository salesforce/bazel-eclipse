package com.salesforce.bazel.sdk.aspect;

import java.io.File;
import java.util.List;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * Provider that knows how to construct AspectTargetInfo objects for one or more rule kinds. (e.g. java_library)
 */
public interface AspectTargetInfoFactoryProvider {

    AspectTargetInfo buildAspectTargetInfo(File aspectDataFile, JSONObject jsonObject, JSONParser jsonParser,
            String workspaceRelativePath, String kind, String label, List<String> deps, List<String> sources);

}
