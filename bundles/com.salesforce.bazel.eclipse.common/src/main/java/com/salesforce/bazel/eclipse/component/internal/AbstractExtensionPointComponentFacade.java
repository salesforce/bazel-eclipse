package com.salesforce.bazel.eclipse.component.internal;

import java.lang.reflect.ParameterizedType;
import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;

import com.salesforce.bazel.eclipse.activator.BazelEclipseExtensionPointDefinition;

public abstract class AbstractExtensionPointComponentFacade<C> {
    private C component;
    
    public AbstractExtensionPointComponentFacade() {
        component = instantiateExtensionPoint(getComponentClass(), getExtensionPointDefinition());
    }

    public C getComponent() {
        return component;
    }

    protected abstract BazelEclipseExtensionPointDefinition getExtensionPointDefinition();

    @SuppressWarnings("unchecked")
    private Class<C> getComponentClass() {
        return (Class<C>) ParameterizedType.class.cast(getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }

    private C instantiateExtensionPoint(Class<C> executableClass,
            BazelEclipseExtensionPointDefinition extensionPointDef) {
        IExtensionPoint extensionPoint =
                Platform.getExtensionRegistry().getExtensionPoint(extensionPointDef.getExtensionPointId());
        if (Objects.isNull(extensionPoint)) {
            throw new IllegalArgumentException(
                    "At-least one extenstion for " + executableClass.getName() + " should be defined");
        }
        IConfigurationElement[] configs = extensionPoint.getConfigurationElements();
        if (configs.length == 0) {
            throw new IllegalArgumentException(
                    "At-least one extenstion for " + executableClass.getName() + " should be defined");
        } else if (configs.length > 1) {
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
