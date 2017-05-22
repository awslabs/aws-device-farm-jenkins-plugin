package org.jenkinsci.plugins.awsdevicefarm.test;

/**
 * POJO class for an Appium Java JUnit test.
 */
public class AppiumWebJavaJUnitTest {
    private final String tests;

    /**
     * Static builder class.
     */
    public static class Builder {
        private String tests;

        /**
         * Test setter.
         * @param tests Path to the tests to run.
         * @return The builder object.
         */
        public Builder withTests(String tests) {
            this.tests = tests;
            return this;
        }

        /**
         * Build method.
         * @return The new POJO.
         */
        public AppiumWebJavaJUnitTest build() {
            return new AppiumWebJavaJUnitTest(this);
        }
    }

    /**
     * POJO constructor with builder.
     * @param builder The builder to use.
     */
    private AppiumWebJavaJUnitTest(Builder builder) {
        this.tests = builder.tests;
    }

    /**
     * Test getter.
     * @return The path to the tests to run.
     */
    public String getTests() {
        return this.tests;
    }
}
