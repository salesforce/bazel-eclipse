package com.salesforce.bazel.sdk.aspect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.simple.JSONObject;
import org.junit.Test;

import com.salesforce.bazel.sdk.aspect.jvm.JVMAspectOutputJarSet;
import com.salesforce.bazel.sdk.path.FSPathHelper;

public class AspectOutputJarsTest {

    //  {
    //   "jar": {
    //      "relative_path": "external/com_google_guava_guava/jar/guava-20.0.jar", $SLASH_OK: sample code
    //   }
    //   "interface_jar": {
    //      "relative_path": "bazel-out/darwin-fastbuild/bin/external/com_google_guava_guava/jar/_ijar/jar/external/com_google_guava_guava/jar/guava-20.0-ijar.jar", $SLASH_OK: sample code
    //   }
    //   "source_jar": {
    //      "relative_path": "external/com_google_guava_guava/jar/guava-20.0-sources.jar" $SLASH_OK: sample code
    //   }
    //  }
    private final String GUAVA_JAR = FSPathHelper.osSeps("external/com_google_guava_guava/jar/guava-20.0.jar"); // $SLASH_OK
    private final String GUAVA_IJAR = FSPathHelper.osSeps(
        "bazel-out/darwin-fastbuild/bin/external/com_google_guava_guava/jar/_ijar/jar/external/com_google_guava_guava/jar/guava-20.0-ijar.jar"); // $SLASH_OK
    private final String GUAVA_SJAR = FSPathHelper.osSeps("external/com_google_guava_guava/jar/guava-20.0-sources.jar"); // $SLASH_OK

    @Test
    public void testDeserializationHappy() {
        JSONObject jars = createJarsArray(GUAVA_JAR, GUAVA_IJAR, GUAVA_SJAR);

        JVMAspectOutputJarSet parsedJars = new JVMAspectOutputJarSet(jars);
        assertEquals(GUAVA_JAR, parsedJars.getJar());
        assertEquals(GUAVA_IJAR, parsedJars.getInterfaceJar());
        assertEquals(GUAVA_SJAR, parsedJars.getSrcJar());
    }

    @Test
    public void testDeserializationNullJar() {
        JSONObject jars = createJarsArray(null, GUAVA_IJAR, GUAVA_SJAR);

        JVMAspectOutputJarSet parsedJars = new JVMAspectOutputJarSet(jars);
        assertNull(parsedJars.getJar());
        assertEquals(GUAVA_IJAR, parsedJars.getInterfaceJar());
        assertEquals(GUAVA_SJAR, parsedJars.getSrcJar());
    }

    @Test
    public void testDeserializationNullIJar() {
        JSONObject jars = createJarsArray(GUAVA_JAR, null, GUAVA_SJAR);

        JVMAspectOutputJarSet parsedJars = new JVMAspectOutputJarSet(jars);
        assertEquals(GUAVA_JAR, parsedJars.getJar());
        assertNull(parsedJars.getInterfaceJar());
        assertEquals(GUAVA_SJAR, parsedJars.getSrcJar());
    }

    @Test
    public void testDeserializationNullSJar() {
        JSONObject jars = createJarsArray(GUAVA_JAR, GUAVA_IJAR, null);

        JVMAspectOutputJarSet parsedJars = new JVMAspectOutputJarSet(jars);
        assertEquals(GUAVA_JAR, parsedJars.getJar());
        assertEquals(GUAVA_IJAR, parsedJars.getInterfaceJar());
        assertNull(parsedJars.getSrcJar());
    }

    private JSONObject createJarsArray(String jar, String ijar, String sourcejar) {

        JSONObject jarsObj = new JSONObject();
        if (jar != null) {
            JSONObject jarObj = new JSONObject();
            jarObj.put("relative_path", jar);
            jarsObj.put("jar", jarObj);
        }
        if (ijar != null) {
            JSONObject jarObj = new JSONObject();
            jarObj.put("relative_path", ijar);
            jarsObj.put("interface_jar", jarObj);
        }
        if (sourcejar != null) {
            JSONObject jarObj = new JSONObject();
            jarObj.put("relative_path", sourcejar);
            jarsObj.put("source_jar", jarObj);
        }

        return jarsObj;
    }
}
