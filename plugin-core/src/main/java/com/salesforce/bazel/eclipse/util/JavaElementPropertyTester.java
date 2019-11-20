package com.salesforce.bazel.eclipse.util;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.jdt.core.IJavaElement;

public class JavaElementPropertyTester extends PropertyTester {

    private static final String PROPERTY_IS_TEST_JAVA_ELEMENT = "isTestJavaElement"; //$NON-NLS-1$

    @Override
    public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
        IJavaElement resource = (IJavaElement) receiver;
        if (PROPERTY_IS_TEST_JAVA_ELEMENT.equals(property)) {
            String path = resource.getPath().toString();
            return path.contains("/src/test/java");
        }
        return false;
    }

}
