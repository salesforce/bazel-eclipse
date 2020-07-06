package com.salesforce.bazel.sdk.aspect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONObject;
import org.junit.Test;

public class AspectOutputJarsTest {

    //  {
    //   "jar":"external/com_google_guava_guava/jar/guava-20.0.jar",
    //   "interface_jar":"bazel-out/darwin-fastbuild/bin/external/com_google_guava_guava/jar/_ijar/jar/external/com_google_guava_guava/jar/guava-20.0-ijar.jar",
    //   "source_jar":"external/com_google_guava_guava/jar/guava-20.0-sources.jar"
    //  }
    private final String GUAVA_JAR = "external/com_google_guava_guava/jar/guava-20.0.jar";
    private final String GUAVA_IJAR = "bazel-out/darwin-fastbuild/bin/external/com_google_guava_guava/jar/_ijar/jar/external/com_google_guava_guava/jar/guava-20.0-ijar.jar";
    private final String GUAVA_SJAR = "external/com_google_guava_guava/jar/guava-20.0-sources.jar";


    @Test
    public void testDeserializationHappy() {
        JSONObject jars = createJarsArray(GUAVA_JAR, GUAVA_IJAR, GUAVA_SJAR);

        AspectOutputJarSet parsedJars = new AspectOutputJarSet(jars);
        assertEquals(GUAVA_JAR, parsedJars.getJar());
        assertEquals(GUAVA_IJAR, parsedJars.getInterfaceJar());
        assertEquals(GUAVA_SJAR, parsedJars.getSrcJar());
    }

    @Test
    public void testDeserializationNullJar() {
        JSONObject jars = createJarsArray(null, GUAVA_IJAR, GUAVA_SJAR);

        AspectOutputJarSet parsedJars = new AspectOutputJarSet(jars);
        assertNull(parsedJars.getJar());
        assertEquals(GUAVA_IJAR, parsedJars.getInterfaceJar());
        assertEquals(GUAVA_SJAR, parsedJars.getSrcJar());
    }

    @Test
    public void testDeserializationNullIJar() {
        JSONObject jars = createJarsArray(GUAVA_JAR, null, GUAVA_SJAR);

        AspectOutputJarSet parsedJars = new AspectOutputJarSet(jars);
        assertEquals(GUAVA_JAR, parsedJars.getJar());
        assertNull(parsedJars.getInterfaceJar());
        assertEquals(GUAVA_SJAR, parsedJars.getSrcJar());
    }

    @Test
    public void testDeserializationNullSJar() {
        JSONObject jars = createJarsArray(GUAVA_JAR, GUAVA_IJAR, null);

        AspectOutputJarSet parsedJars = new AspectOutputJarSet(jars);
        assertEquals(GUAVA_JAR, parsedJars.getJar());
        assertEquals(GUAVA_IJAR, parsedJars.getInterfaceJar());
        assertNull(parsedJars.getSrcJar());
    }


    private JSONObject createJarsArray(String jar, String ijar, String sourcejar) {

        JSONObject jarsObj = new JSONObject();
        if (jar != null) {
            jarsObj.put("jar", jar);
        }
        if (ijar != null) {
            jarsObj.put("interface_jar", ijar);
        }
        if (sourcejar != null) {
            jarsObj.put("source_jar", sourcejar);
        }

        return jarsObj;
    }
}
