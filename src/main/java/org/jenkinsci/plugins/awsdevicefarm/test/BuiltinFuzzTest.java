package org.jenkinsci.plugins.awsdevicefarm.test;

/**
 * POJO class for a built-in Android test.
 */
public final class BuiltinFuzzTest {
    private final String eventCount;
    private final String eventThrottle;
    private final String seed;

    /**
     * Static builder class.
     */
    public static class Builder {
        private String eventCount;
        private String eventThrottle;
        private String seed;

        /**
         * Number of events setter.
         * @param eventCount The number of fuzz events to run.
         * @return The builder object.
         */
        public Builder withEventCount(String eventCount) {
            this.eventCount = eventCount;
            return this;
        }

        /**
         * Event throttle data setter.
         * @param eventThrottle The event throttle.
         * @return The builder object.
         */
        public Builder withEventThrottle(String eventThrottle) {
            this.eventThrottle = eventThrottle;
            return this;
        }

        /**
         * Seed setter.
         * @param seed The initial seed to use.
         * @return The builder object.
         */
        public Builder withSeed(String seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Builder method.
         * @return The new POJO.
         */
        public BuiltinFuzzTest build() {
            return new BuiltinFuzzTest(this);
        }
    }

    /**
     * POJO constructor with builder.
     * @param builder The builder to use.
     */
    private BuiltinFuzzTest(Builder builder) {
        this.eventCount = builder.eventCount;
        this.eventThrottle = builder.eventThrottle;
        this.seed = builder.seed;
    }

    /**
     * Number of events getter.
     * @return The number of fuzz events to run.
     */
    public String getEventCount() {
        return this.eventCount;
    }

    /**
     * Event throttle data getter.
     * @return The event throttle.
     */
    public String getEventThrottle() {
        return this.eventThrottle;
    }

    /**
     * Seed getter.
     * @return The initial seed.
     */
    public String getSeed() {
        return this.seed;
    }

}
