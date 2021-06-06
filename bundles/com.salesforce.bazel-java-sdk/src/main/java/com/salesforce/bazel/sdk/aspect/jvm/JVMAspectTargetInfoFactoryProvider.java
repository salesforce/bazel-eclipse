package com.salesforce.bazel.sdk.aspect.jvm;

import java.io.File;
import java.util.List;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfoFactoryProvider;

public class JVMAspectTargetInfoFactoryProvider implements AspectTargetInfoFactoryProvider {

    @Override
    public AspectTargetInfo buildAspectTargetInfo(File aspectDataFile, JSONObject jsonObject, JSONParser jsonParser, 
            String workspaceRelativePath, String kind, String label,
            List<String> deps, List<String> sources) {
        JVMAspectTargetInfo info = null;
        
        try {
            info = new JVMAspectTargetInfo(aspectDataFile, jsonObject, jsonParser, workspaceRelativePath,
                kind, label, deps, sources);
        } catch (Exception anyE) {
            anyE.printStackTrace(); // TODO log
        }
                
        return info;
    }

}
