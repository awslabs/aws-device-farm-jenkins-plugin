package org.jenkinsci.plugins.awsdevicefarm.test;

/**
 * POJO class for an XCTest test.
 */
public final class XCTestUITest {
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
         * @param filter The filter to use on tests.
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
        public XCTestUITest build() {
            return new XCTestUITest(this);
        }
    }

    /**
     * POJO constructor with builder.
     * @param builder The builder to use.
     */
    private XCTestUITest(Builder builder) {
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
     * @return The filter to use on tests.
     */
    public String getFilter() {
        return this.filter;
    }
}
