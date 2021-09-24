package com.salesforce.bazel.eclipse.activator;

import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;

import com.salesforce.bazel.eclipse.runtime.api.BaseResourceHelper;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.sdk.project.BazelProjectManager;

public class BazelExtensionPointManager {

    private static BazelExtensionPointManager instance;

    private BazelExtensionPointManager() {}

    public static synchronized BazelExtensionPointManager getInstance() {
        if (instance == null) {
            instance = new BazelExtensionPointManager();
        }
        return instance;
    }

    public BazelProjectManager bazelProjectManager() {
        return instantiateExtensionPoint(BazelProjectManager.class,
            BazelEclipseExtensionPointDefinition.BAZEL_PROJECT_MANAGER);
    }

    public BaseResourceHelper bazelEclipseResourceHelper() {
        return instantiateExtensionPoint(BaseResourceHelper.class,
            BazelEclipseExtensionPointDefinition.BAZEL_RESOURCE_HELPER);
    }

    public JavaCoreHelper javaCoreHelper() {
        return instantiateExtensionPoint(JavaCoreHelper.class,
            BazelEclipseExtensionPointDefinition.JAVA_CORE_HELPER);
    }

    private <E> E instantiateExtensionPoint(Class<E> executableClass,
            BazelEclipseExtensionPointDefinition extensionPointDef) {
        IExtensionPoint extensionPoint =
                Platform.getExtensionRegistry().getExtensionPoint(extensionPointDef.getExtensionPointId());
        if (Objects.isNull(extensionPoint)) {
            throw new IllegalArgumentException("At-least one extenstion for BazelProjectManager should be defined");
        }
        IConfigurationElement[] configs = extensionPoint.getConfigurationElements();
        if (configs.length != 1) {
            throw new IllegalArgumentException("There should be only one BazelProjectManager defined");
        }
        try {
            Object executableExtension = configs[0].createExecutableExtension("class");
            return executableClass.cast(executableExtension);
        } catch (CoreException exc) {
            throw new IllegalArgumentException("Unable to instantiate BazelProjectManager implementation");
        }
    }
}
