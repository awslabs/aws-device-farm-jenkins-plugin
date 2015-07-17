package org.jenkinsci.plugins.awsdevicefarm.test;

/**
 * POJO class for a UI automator test.
 */
public class UIAutomatorTest {
    private final String tests;
    private final String filter;

    /**
     * Static builder class.
     */
    public static class Builder {
        private String tests;
        private String filter;

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
         * Filter setter.
         * @param filter Filter to apply to the tests.
         * @return The builder object.
         */
        public Builder withFilter(String filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Build method.
         * @return The new POJO.
         */
        public UIAutomatorTest build() {
            return new UIAutomatorTest(this);
        }
    }

    /**
     * POJO constructor with builder.
     * @param builder The builder to use.
     */
    private UIAutomatorTest(Builder builder) {
        this.tests = builder.tests;
        this.filter = builder.filter;
    }

    /**
     * Test getter.
     * @return The path to the tests to run.
     */
    public String getTests() {
        return this.tests;
    }

    /**
     * Filter getter.
     * @return The filter to apply to the tests.
     */
    public String getFilter() {
        return this.filter;
    }
}
