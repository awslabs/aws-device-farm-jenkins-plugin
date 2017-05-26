package org.jenkinsci.plugins.awsdevicefarm;

import java.util.HashMap;
import java.util.Map;

/**
 * This takes the place of the AWS Device Farm SDK type UploadType.
 */
public enum AWSDeviceFarmUploadType {
    ANDROID_APP ("ANDROID_APP"),                                    // App
    IOS_APP ("IOS_APP"),                                            // App
    APPIUM_JAVA_TESTNG("APPIUM_JAVA_TESTNG_TEST_PACKAGE"),          // Test
    APPIUM_JAVA_JUNIT("APPIUM_JAVA_JUNIT_TEST_PACKAGE"),            // Test
    APPIUM_PYTHON("APPIUM_PYTHON_TEST_PACKAGE"),                    // Test
    CALABASH ("CALABASH_TEST_PACKAGE"),                             // Test
    INSTRUMENTATION ("INSTRUMENTATION_TEST_PACKAGE"),               // Test
    UIAUTOMATOR ("UIAUTOMATOR_TEST_PACKAGE"),                       // Test
    UIAUTOMATION ("UIAUTOMATION_TEST_PACKAGE"),                     // Test
    XCTEST ("XCTEST_TEST_PACKAGE"),                                 // Test
    XCTEST_UI("XCTEST_UI_TEST_PACKAGE"),                            // Test
    APPIUM_WEB_PYTHON("APPIUM_WEB_PYTHON_TEST_PACKAGE"),            // Test
    APPIUM_WEB_JAVA_TESTNG("APPIUM_WEB_JAVA_TESTNG_TEST_PACKAGE"),  // Test
    APPIUM_WEB_JAVA_JUNIT("APPIUM_WEB_JAVA_JUNIT_TEST_PACKAGE"),    // Test
    EXTERNAL_DATA("EXTERNAL_DATA");                                 // Extra Data


    private final String type;
    // Lookup table For getting enum by value.
    private static final Map<String, AWSDeviceFarmUploadType> lut = new HashMap<String, AWSDeviceFarmUploadType>();

    /**
     * The constructor.
     * @param type The type.
     */
    private AWSDeviceFarmUploadType(final String type) {
        this.type = type;
    }

    /**
     * Populate the look up table.
     */
    static {
        for (AWSDeviceFarmUploadType t : AWSDeviceFarmUploadType.values())
            lut.put(t.toString(), t);
    }

    /**
     * Getter for getting the enum by value.
     * @param s The String representation of the enum.
     * @return The corresponding enum value.
     */
    public static AWSDeviceFarmUploadType get(String s) {
        return lut.get(s);
    }

    /**
     * Convert the enum to a string.
     * @return The enum as a String.
     */
    @Override
    public String toString(){
        return type;
    }
}
