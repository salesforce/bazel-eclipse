package com.salesforce.bazel.eclipse.component;

import com.salesforce.bazel.eclipse.activator.BazelEclipseExtensionPointDefinition;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;

public class JavaCoreHelperComponentFacade extends AbstractExtensionPointComponentFacade<JavaCoreHelper> {
    private static JavaCoreHelperComponentFacade instance;

    private JavaCoreHelperComponentFacade() {}

    public static synchronized JavaCoreHelperComponentFacade getInstance() {
        if (instance == null) {
            instance = new JavaCoreHelperComponentFacade();
        }
        return instance;
    }

    @Override
    protected BazelEclipseExtensionPointDefinition getExtensionPointDefinition() {
        return BazelEclipseExtensionPointDefinition.BAZEL_JAVA_CORE_HELPER;
    }

}
