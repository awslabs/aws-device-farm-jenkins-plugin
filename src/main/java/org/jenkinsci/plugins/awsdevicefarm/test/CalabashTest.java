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
 * POJO class for a Calabash test.
 */
public final class CalabashTest {
    private final String features;
    private final String tags;
    private final String profile;

    /**
     * Static builder class.
     */
    public static class Builder {
        private String features;
        private String tags;
        private String profile;

        /**
         * Features setter.
         *
         * @param features The path to the Calabash features file.
         * @return The builder object.
         */
        public Builder withFeatures(String features) {
            this.features = features;
            return this;
        }

        /**
         * Tags setter.
         *
         * @param tags The tags to use.
         * @return The builder object.
         */
        public Builder withTags(String tags) {
            this.tags = tags;
            return this;
        }

        /**
         * Profile setter.
         *
         * @param profile The profile to use.
         * @return The builder object.
         */
        public Builder withProfile(String profile) {
            this.profile = profile;
            return this;
        }

        /**
         * Build method
         *
         * @return The new POJO.
         */
        public CalabashTest build() {
            return new CalabashTest(this);
        }
    }

    /**
     * POJO constructor with builder.
     *
     * @param builder The builder to use.
     */
    private CalabashTest(Builder builder) {
        this.features = builder.features;
        this.tags = builder.tags;
        this.profile = builder.profile;
    }

    /**
     * Features getter.
     *
     * @return The path to the Calabash features file.
     */
    public String getFeatures() {
        return this.features;
    }

    /**
     * Tags getter.
     *
     * @return The tags to use.
     */
    public String getTags() {
        return this.tags;
    }

    /**
     * profile getter.
     *
     * @return The profile to use.
     */
    public String getProfile() {
        return this.profile;
    }
}
