//
// Copyright 2015-2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License").
// You may not use this file except in compliance with the License.
// A copy of the License is located at
//
// http://aws.amazon.com/apache2.0
//
// or in the "license" file accompanying this file. This file is distributed
// on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied. See the License for the specific language governing
// permissions and limitations under the License.
//
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
         *
         * @param eventCount The number of fuzz events to run.
         * @return The builder object.
         */
        public Builder withEventCount(String eventCount) {
            this.eventCount = eventCount;
            return this;
        }

        /**
         * Event throttle data setter.
         *
         * @param eventThrottle The event throttle.
         * @return The builder object.
         */
        public Builder withEventThrottle(String eventThrottle) {
            this.eventThrottle = eventThrottle;
            return this;
        }

        /**
         * Seed setter.
         *
         * @param seed The initial seed to use.
         * @return The builder object.
         */
        public Builder withSeed(String seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Builder method.
         *
         * @return The new POJO.
         */
        public BuiltinFuzzTest build() {
            return new BuiltinFuzzTest(this);
        }
    }

    /**
     * POJO constructor with builder.
     *
     * @param builder The builder to use.
     */
    private BuiltinFuzzTest(Builder builder) {
        this.eventCount = builder.eventCount;
        this.eventThrottle = builder.eventThrottle;
        this.seed = builder.seed;
    }

    /**
     * Number of events getter.
     *
     * @return The number of fuzz events to run.
     */
    public String getEventCount() {
        return this.eventCount;
    }

    /**
     * Event throttle data getter.
     *
     * @return The event throttle.
     */
    public String getEventThrottle() {
        return this.eventThrottle;
    }

    /**
     * Seed getter.
     *
     * @return The initial seed.
     */
    public String getSeed() {
        return this.seed;
    }

}
