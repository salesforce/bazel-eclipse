package com.salesforce.bazel.eclipse.component;

import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;

import com.salesforce.bazel.eclipse.activator.BazelEclipseExtensionPointDefinition;
import com.salesforce.bazel.sdk.aspect.BazelAspectLocation;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.sdk.workspace.RealOperatingEnvironmentDetectionStrategy;

public class EclipseBazelComponentFacade {

    private static EclipseBazelComponentFacade instance;

    private final OperatingEnvironmentDetectionStrategy osDetectionStrategy;

    private EclipseBazelComponentFacade() {
        osDetectionStrategy = new RealOperatingEnvironmentDetectionStrategy();
    }

    public static synchronized EclipseBazelComponentFacade getInstance() {
        if (instance == null) {
            instance = new EclipseBazelComponentFacade();
        }
        return instance;
    }

    public OperatingEnvironmentDetectionStrategy getOsDetectionStrategy() {
        return osDetectionStrategy;
    }

    public BazelAspectLocation getBazelAspectLocation() {
        return instantiateExtensionPoint(BazelAspectLocation.class,
            BazelEclipseExtensionPointDefinition.BAZEL_ASPECT_LOCATION);
    }

    private <E> E instantiateExtensionPoint(Class<E> executableClass,
            BazelEclipseExtensionPointDefinition extensionPointDef) {
        IExtensionPoint extensionPoint =
                Platform.getExtensionRegistry().getExtensionPoint(extensionPointDef.getExtensionPointId());
        if (Objects.isNull(extensionPoint)) {
            throw new IllegalArgumentException(
                    "At-least one extenstion for " + executableClass.getName() + " should be defined");
        }
        IConfigurationElement[] configs = extensionPoint.getConfigurationElements();
        if (configs.length != 1) {
            throw new IllegalArgumentException("There should be only one " + executableClass.getName() + " defined");
        }
        try {
            Object executableExtension = configs[0].createExecutableExtension("class");
            return executableClass.cast(executableExtension);
        } catch (CoreException exc) {
            throw new IllegalArgumentException(
                    "Unable to instantiate " + executableClass.getName() + " implementation");
        }
    }
}
