package org.jenkinsci.plugins.awsdevicefarm.test;

/**
 * POJO class for an XCTest test.
 */
public final class XCTestTest {
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
        public XCTestTest build() {
            return new XCTestTest(this);
        }
    }

    /**
     * POJO constructor with builder.
     * @param builder The builder to use.
     */
    private XCTestTest(Builder builder) {
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
