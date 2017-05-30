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
package org.jenkinsci.plugins.awsdevicefarm;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.views.ListViewColumn;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * AWS Device Farm specific column entry to display (pass/warn/fail)
 * result numbers on the Jenkins homepage within the project list view
 * from the most recent run of that project.
 */
public class AWSDeviceFarmListViewColumn extends ListViewColumn {

    @DataBoundConstructor
    public AWSDeviceFarmListViewColumn() {
    }

    /**
     * Returns true if the previous job has an AWS Device Farm result with valid tests it should display.
     *
     * @param job The job to test.
     * @return Whether or the not the job should be displayed.
     */
    public boolean shouldDisplay(Job job) {
        AWSDeviceFarmTestResult result = getPreviousResult(job);
        if (result == null || result.getTotalCount() <= 0) {
            return false;
        }
        return true;
    }

    /**
     * Get the AWS Device Farm test run from the most recent build of this job
     *
     * @param job The job to get the previous result of.
     * @return The Device Farm test result of the job.
     */
    public AWSDeviceFarmTestResult getPreviousResult(Job job) {
        return AWSDeviceFarmUtils.previousAWSDeviceFarmBuildResult(job);
    }

    /**
     * Column caption getter.
     *
     * @return The caption of the column.
     */
    @Override
    public String getColumnCaption() {
        return getDescriptor().getDisplayName();
    }

    /**
     * The descriptor implementation.
     */
    @Extension
    public static final class DescriptorImpl extends Descriptor<ListViewColumn> {

        /**
         * Display name getter.
         *
         * @return The display name.
         */
        @Override
        public String getDisplayName() {
            return "AWS Device Farm Pass/Warn/Fail";
        }
    }
}
