package com.salesforce.b2eclipse.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.salesforce.b2eclipse.BazelJdtPlugin;
import com.salesforce.b2eclipse.model.AspectOutputJars;
import com.salesforce.b2eclipse.model.AspectPackageInfo;

import org.apache.commons.lang3.StringUtils;

public final class IntellijAspectPackageInfoLoader {
    private static final String PROPERTY_BUILD_FILE_ARTIFACT_LOCATION = "build_file_artifact_location"; //$NON-NLS-1$
    private static final String PROPERTY_DEPENDENCIES = "deps"; //$NON-NLS-1$
    private static final String PROPERTY_LABEL = "label"; //$NON-NLS-1$
    private static final String PROPERTY_TARGET = "target"; //$NON-NLS-1$
    private static final String PROPERTY_RELATIVE_PATH = "relative_path"; //$NON-NLS-1$
    private static final String PROPERTY_JAVA_IDE_INFO = "java_ide_info"; //$NON-NLS-1$
    private static final String PROPERTY_JARS = "jars"; //$NON-NLS-1$
    private static final String PROPERTY_GENERATED_JARS = "generated_jars"; //$NON-NLS-1$
    private static final String PROPERTY_JAR = "jar"; //$NON-NLS-1$
    private static final String PROPERTY_KEY = "key"; //$NON-NLS-1$
    private static final String PROPERTY_SOURCE_JAR = "source_jar"; //$NON-NLS-1$
    private static final String PROPERTY_SOURCES = "sources"; //$NON-NLS-1$
    private static final String PROPERTY_INTERFACE_JAR = "interface_jar"; //$NON-NLS-1$
    private static final String PROPERTY_KIND_STRING = "kind_string"; //$NON-NLS-1$
    private static final String PROPERTY_MAIN_CLASS = "main_class"; //$NON-NLS-1$
    private static final String PROPERTY_ROOT_EXECUTION_PATH_FRAGMENT = "root_execution_path_fragment"; //$NON-NLS-1$

    private IntellijAspectPackageInfoLoader() {
    }

    public static ImmutableMap<String, AspectPackageInfo> loadAspectFiles(List<String> aspectFiles) {
        ImmutableMap.Builder<String, AspectPackageInfo> infos = ImmutableMap.builder();
        for (String aspectFile : aspectFiles) {
            AspectPackageInfo buildInfo = loadAspectFile(new File(aspectFile));
            if (buildInfo != null && buildInfo.getLabel() != null) {
                infos.put(buildInfo.getLabel(), buildInfo);
            }
        }
        return infos.build();
    }

    public static AspectPackageInfo loadAspectFile(final File aspectFile) {
        AspectPackageInfo buildInfo = null;
        try {
            JsonObject jsonObject = readRootJsonObject(aspectFile);
            buildInfo = Optional.<JsonObject>ofNullable(jsonObject).filter(IntellijAspectPackageInfoLoader::isValid)
                    .map((JsonObject rootObject) -> parseJsonObject(aspectFile, rootObject)).orElse(null);
        } catch (Exception exc) {
            BazelJdtPlugin.logException(exc);
        }
        return buildInfo;
    }

    private static JsonObject readRootJsonObject(File aspectFile) throws IOException, InterruptedException {
        JsonObject jsonRootObject = null;
        if (aspectFile.exists()) {
            try (FileReader fileReader = new FileReader(aspectFile)) {
                JsonReader jsonReader = new JsonReader(fileReader);
                JsonParser parser = new JsonParser();
                JsonElement jsonElement = parser.parse(jsonReader);
                if (JsonObject.class.isInstance(jsonElement)) {
                    jsonRootObject = jsonElement.getAsJsonObject();
                }
            }
        }
        return jsonRootObject;
    }

    private static AspectPackageInfo parseJsonObject(final File aspectFile, JsonObject rootObject) {
        JsonObject javaIdeInfoElement = parseJavaIdeInfo(rootObject);

        String workspaceRelativePath = parseRelativePath(rootObject.get(PROPERTY_BUILD_FILE_ARTIFACT_LOCATION));
        String kind = parseKind(rootObject);
        String label = parseLabel(rootObject.get(PROPERTY_KEY));
        String mainClass = parseMainClass(javaIdeInfoElement);

        ImmutableList<AspectOutputJars> jars = parseJars(javaIdeInfoElement, PROPERTY_JARS);
        ImmutableList<AspectOutputJars> generatedJars = parseJars(javaIdeInfoElement, PROPERTY_GENERATED_JARS);
        ImmutableList<String> sources = parseSources(javaIdeInfoElement);
        ImmutableList<String> deps = parseDependencies(rootObject);

        AspectPackageInfo aspectPackageInfo = new AspectPackageInfo(aspectFile, jars, generatedJars,
                workspaceRelativePath, kind, label, deps, sources, mainClass);
        return aspectPackageInfo;
    }

    private static JsonObject parseJavaIdeInfo(JsonObject rootObject) {
        JsonObject ideInfoObject = null;
        if (rootObject != null) {
            ideInfoObject = rootObject.has(PROPERTY_JAVA_IDE_INFO)
                    ? rootObject.get(PROPERTY_JAVA_IDE_INFO).getAsJsonObject() : null;
        }
        return ideInfoObject;
    }

    private static String parseKind(JsonObject rootObject) {
        String kind = rootObject.has(PROPERTY_KIND_STRING) ? rootObject.get(PROPERTY_KIND_STRING).getAsString() : null;
        return kind;
    }

    private static String parseMainClass(JsonObject javaIdeInfoObject) {
        String mainClass = null;
        if (javaIdeInfoObject != null) {
            mainClass = javaIdeInfoObject.has(PROPERTY_MAIN_CLASS) ? //
                    javaIdeInfoObject.get(PROPERTY_MAIN_CLASS).getAsString() : null;
        }
        return mainClass;
    }

    private static ImmutableList<AspectOutputJars> parseJars(JsonObject javaIdeInfoObject, String property) {
        final ImmutableList.Builder<AspectOutputJars> builder = ImmutableList.builder();
        if (javaIdeInfoObject != null) {
            JsonElement jarsElement = javaIdeInfoObject.get(property);
            if (JsonArray.class.isInstance(jarsElement)) {
                jarsElement.getAsJsonArray().forEach((JsonElement elem) -> {
                    if (JsonObject.class.isInstance(elem)) {
                        JsonObject jarDescrObject = elem.getAsJsonObject();
                        String outputJar = parseJarName(jarDescrObject.get(PROPERTY_JAR));
                        String interfaceJar = parseJarName(jarDescrObject.get(PROPERTY_INTERFACE_JAR));
                        String srcJar = parseJarName(jarDescrObject.get(PROPERTY_SOURCE_JAR));
                        AspectOutputJars jarDescr = new AspectOutputJars(interfaceJar, outputJar, srcJar);
                        builder.add(jarDescr);
                    }
                });
            }
        }
        ImmutableList<AspectOutputJars> jars = builder.build();
        return jars;
    }

    private static ImmutableList<String> parseSources(JsonObject javaIdeInfoObject) {
        final ImmutableList.Builder<String> builder = ImmutableList.builder();
        if (javaIdeInfoObject != null) {
            JsonElement sourcesElement = javaIdeInfoObject.get(PROPERTY_SOURCES);
            if (JsonArray.class.isInstance(sourcesElement)) {
                JsonArray sourcesArray = sourcesElement.getAsJsonArray();
                sourcesArray.forEach((JsonElement elem) -> {
                    if (JsonObject.class.isInstance(elem)) {
                        String sourcePath = parseRelativePath(elem);
                        if (StringUtils.isNotBlank(sourcePath)) {
                            builder.add(sourcePath.trim());
                        }
                    }
                });
            }
        }
        ImmutableList<String> sources = builder.build();
        return sources;
    }

    private static ImmutableList<String> parseDependencies(JsonObject rootObject) {
        final ImmutableList.Builder<String> builder = ImmutableList.builder();
        JsonElement depsElement = rootObject.get(PROPERTY_DEPENDENCIES);
        if (JsonArray.class.isInstance(depsElement)) {
            JsonArray depsJsonArray = depsElement.getAsJsonArray();
            depsJsonArray.forEach((JsonElement elem) -> {
                if (JsonObject.class.isInstance(elem)) {
                    String depLabel = parseLabel(elem.getAsJsonObject().get(PROPERTY_TARGET));
                    if (StringUtils.isNotBlank(depLabel)) {
                        builder.add(depLabel);
                    }
                }
            });
        }
        ImmutableList<String> deps = builder.build();
        return deps;
    }

    private static String parseRelativePath(JsonElement buildInfoElement) {
        String buildFileLocation = null;
        if (JsonObject.class.isInstance(buildInfoElement)) {
            JsonElement pathElement = buildInfoElement.getAsJsonObject().get(PROPERTY_RELATIVE_PATH);
            if (pathElement != null) {
                buildFileLocation = pathElement.getAsString();
            }
        }
        return buildFileLocation;
    }

    private static String parseJarName(JsonElement element) {
        String file = null;
        if (JsonObject.class.isInstance(element)) {
            JsonObject jarObject = element.getAsJsonObject();
            if (jarObject.has(PROPERTY_RELATIVE_PATH)) {
                file = jarObject.get(PROPERTY_RELATIVE_PATH).getAsString();
            }
            if (jarObject.has(PROPERTY_ROOT_EXECUTION_PATH_FRAGMENT)) {
                file = jarObject.get(PROPERTY_ROOT_EXECUTION_PATH_FRAGMENT).getAsString() + "/" + file;
            }
        }
        return file;
    }

    private static String parseLabel(JsonElement parentElement) {
        String label = null;
        if (JsonObject.class.isInstance(parentElement)) {
            JsonObject parentObject = parentElement.getAsJsonObject();
            label = parentObject.has(PROPERTY_LABEL) ? //
                    parentObject.get(PROPERTY_LABEL).getAsString() : //
                    null;
        }
        return label;
    }

    private static boolean isValid(JsonObject rootObject) {
        boolean hasJavaIdeInfo = rootObject == null ? false : rootObject.has(PROPERTY_JAVA_IDE_INFO);
        return hasJavaIdeInfo;
    }

}
