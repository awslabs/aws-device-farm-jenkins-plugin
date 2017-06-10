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

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Run;

import java.util.ArrayList;

/**
 * Contains collection of helper functions for common AWS Device Farm/Jenkins actions.
 */
public class AWSDeviceFarmUtils {

    /**
     * Returns the AWS Device Farm test run action from the most recent build.
     *
     * @param project The Jenkins project which contains builds/runs to examine.
     * @return The previous Device Farm build result action.
     */
    public static AWSDeviceFarmTestResultAction previousAWSDeviceFarmBuildAction(AbstractProject<?, ?> project) {
        AbstractBuild<?, ?> build = AWSDeviceFarmUtils.previousAWSDeviceFarmBuild(project);
        if (build == null) {
            return null;
        }
        return build.getAction(AWSDeviceFarmTestResultAction.class);
    }

    /**
     * Returns the most recent build which contained an AWS Device Farm test run.
     *
     * @param project The Jenkins project which contains runs to examine.
     * @return The previous Device Farm build.
     */
    public static AbstractBuild<?, ?> previousAWSDeviceFarmBuild(AbstractProject<?, ?> project) {
        AbstractBuild<?, ?> last = project.getLastBuild();
        while (last != null) {
            if (last.getAction(AWSDeviceFarmTestResultAction.class) != null) {
                break;
            }
            last = last.getPreviousBuild();
        }
        return last;
    }

    /**
     * Return collection of all previous builds of the given project which contain an AWS Device Farm test run.
     *
     * @param project The Jenkins project which contains runs to examine.
     * @return The previous Device Farm builds.
     */
    public static ArrayList<AWSDeviceFarmTestResultAction> previousAWSDeviceFarmBuilds(AbstractProject<?, ?> project) {
        ArrayList<AWSDeviceFarmTestResultAction> actions = new ArrayList<AWSDeviceFarmTestResultAction>();

        AbstractBuild<?, ?> build = project.getLastBuild();
        while (build != null) {
            AWSDeviceFarmTestResultAction action = build.getAction(AWSDeviceFarmTestResultAction.class);
            if (action != null) {
                actions.add(action);
            }
            build = build.getPreviousBuild();
        }
        return actions;
    }

    /**
     * Returns the most recent AWS Device Farm test result from the previous build.
     *
     * @param job The job which generated an AWS Device Farm test result
     * @return The previous Device Farm build result.
     */
    public static AWSDeviceFarmTestResult previousAWSDeviceFarmBuildResult(Job job) {
        Run prev = job.getLastCompletedBuild();
        if (prev == null) {
            return null;
        }
        AWSDeviceFarmTestResultAction action = prev.getAction(AWSDeviceFarmTestResultAction.class);
        if (action == null) {
            return null;
        }
        return action.getResult();
    }

    /**
     * Get the Device Farm run URL from the Device Farm run ARN.
     *
     * @param arn The Device Farm run ARN.
     * @return The Device Farm run URL.
     */
    public static String getRunUrlFromArn(String arn) {
        String projectId = getProjectIdFromArn(arn);
        String runId = getRunIdFromArn(arn);
        return String.format("https://console.aws.amazon.com/devicefarm/home?#/projects/%s/runs/%s", projectId, runId);

    }

    /**
     * Get the Device Farm run ID from the Device Farm run ARN.
     *
     * @param arn The Device Farm run ARN.
     * @return The Device Farm run ID.
     */
    public static String getRunIdFromArn(String arn) {
        String[] projectRunId = splitRunArn(arn);
        return projectRunId[1];
    }

    /**
     * Get the Device Farm project ID from the Device Farm run ARN.
     *
     * @param arn The Device Farm run ARN.
     * @return The Device Farm project ID.
     */
    public static String getProjectIdFromArn(String arn) {
        String[] projectRunId = splitRunArn(arn);
        return projectRunId[0];
    }

    /**
     * Split the run ARN into Device Farm run and project IDs.
     *
     * @param arn The Device Farm run ARN.
     * @return An array containing the run and project IDs.
     */
    public static String[] splitRunArn(String arn) {
        // The stuff we care about is in the 7th slot (index = 6)
        return arn.split(":")[6].split("/");
    }
}
