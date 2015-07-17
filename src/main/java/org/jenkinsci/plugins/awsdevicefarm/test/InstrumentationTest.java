package org.jenkinsci.plugins.awsdevicefarm.test;

/**
 * POJO class for an instrumentation test.
 */
public final class InstrumentationTest {
    private final String artifact;
    private final String filter;

    /**
     * Static builder class.
     */
    public static class Builder {
        private String artifact;
        private String filter;

        /**
         * Artifact setter.
         * @param artifact Path to test artifact.
         * @return The builder object.
         */
        public Builder withArtifact(String artifact) {
            this.artifact = artifact;
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
        public InstrumentationTest build() {
            return new InstrumentationTest(this);
        }
    }

    /**
     * POJO constructor with builder.
     * @param builder The builder to use.
     */
    private InstrumentationTest(Builder builder) {
        this.artifact = builder.artifact;
        this.filter = builder.filter;
    }

    /**
     * Artifact getter.
     * @return The path to the test artifact.
     */
    public String getArtifact() {
        return this.artifact;
    }

    /**
     * Filter getter.
     * @return The filter to use on tests.
     */
    public String getFilter() {
        return this.filter;
    }
}
