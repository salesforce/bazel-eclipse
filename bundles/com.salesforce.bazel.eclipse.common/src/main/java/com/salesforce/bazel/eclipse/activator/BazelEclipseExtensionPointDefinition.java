package com.salesforce.bazel.eclipse.activator;

public enum BazelEclipseExtensionPointDefinition {
    BAZEL_PROJECT_MANAGER("com.salesforce.bazel.projectmanager"),
    BAZEL_RESOURCE_HELPER("com.salesforce.bazel.resourcehelper"),
    BAZEL_ASPECT_LOCATION("com.salesforce.bazel.aspectlocation"),
    BAZEL_JAVA_CORE_HELPER("com.salesforce.bazel.javacorehelper"),
    BAZEL_PREFERENCES_SCOPE("com.salesforce.bazel.preference.scope");

    private final String extensionPointId;

    private BazelEclipseExtensionPointDefinition(String id) {
        this.extensionPointId = id;
    }

    public String getExtensionPointId() {
        return extensionPointId;
    }
}
