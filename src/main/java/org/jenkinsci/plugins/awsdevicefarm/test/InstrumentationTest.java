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
         *
         * @param artifact Path to test artifact.
         * @return The builder object.
         */
        public Builder withArtifact(String artifact) {
            this.artifact = artifact;
            return this;
        }

        /**
         * Filter setter.
         *
         * @param filter The filter to use on tests.
         * @return The builder object.
         */
        public Builder withFilter(String filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Build method.
         *
         * @return The new POJO.
         */
        public InstrumentationTest build() {
            return new InstrumentationTest(this);
        }
    }

    /**
     * POJO constructor with builder.
     *
     * @param builder The builder to use.
     */
    private InstrumentationTest(Builder builder) {
        this.artifact = builder.artifact;
        this.filter = builder.filter;
    }

    /**
     * Artifact getter.
     *
     * @return The path to the test artifact.
     */
    public String getArtifact() {
        return this.artifact;
    }

    /**
     * Filter getter.
     *
     * @return The filter to use on tests.
     */
    public String getFilter() {
        return this.filter;
    }
}
