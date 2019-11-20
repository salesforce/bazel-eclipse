package com.salesforce.bazel.eclipse.launch;

import java.util.HashMap;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import com.salesforce.bazel.eclipse.launch.BazelLaunchConfigurationSupport.BazelLaunchConfigAttributes;
import com.salesforce.bazel.eclipse.mock.MockLaunchConfiguration;

public class BazelLaunchConfigurationDelegateFTest {

    @Test
    @Ignore // need to add Mockito and fill out delegate collaborators before we can do this.
    public void testHappyRunLaunch() throws Exception {
        BazelLaunchConfigurationDelegate delegate = createBazelLaunchConfigurationDelegate();
        MockLaunchConfiguration testConfig = createLaunchConfiguration();

        delegate.launch(testConfig, "run", null, null);
    }

    // HELPERS

    private BazelLaunchConfigurationDelegate createBazelLaunchConfigurationDelegate() {
        return Mockito.mock(BazelLaunchConfigurationDelegate.class);
    }

    private MockLaunchConfiguration createLaunchConfiguration() {
        MockLaunchConfiguration testConfig = new MockLaunchConfiguration();
        testConfig.attributes.put(BazelLaunchConfigAttributes.PROJECT.getAttributeName(), "MyTestProject");
        testConfig.attributes.put(BazelLaunchConfigAttributes.LABEL.getAttributeName(),
            "//projects/services/testproject");
        testConfig.attributes.put(BazelLaunchConfigAttributes.TARGET_KIND.getAttributeName(), "java_test");

        Map<String, String> args = new HashMap<>();
        args.put("arg1", "argvalue1");
        args.put("arg2", "argvalue2");
        args.put("arg3", "argvalue3");
        testConfig.attributes.put(BazelLaunchConfigAttributes.INTERNAL_BAZEL_ARGS.getAttributeName(), args);

        return testConfig;
    }

}
