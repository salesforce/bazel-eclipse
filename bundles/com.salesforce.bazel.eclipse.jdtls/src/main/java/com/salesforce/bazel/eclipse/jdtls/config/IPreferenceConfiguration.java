package com.salesforce.bazel.eclipse.jdtls.config;

public interface IPreferenceConfiguration {
    /**
     * Preference key to enable/disable bazel importer.
     */
    String IMPORT_BAZEL_ENABLED = "java.import.bazel.enabled";
    /**
     * Preference key for log level.
     */
    String BJLS_LOG_LEVEL = "java.bjls.log.level";
    /**
     * Preference key for extended logging.
     */
    String BJLS_LOG_EXTENDED = "java.bjls.log.extended";

}
