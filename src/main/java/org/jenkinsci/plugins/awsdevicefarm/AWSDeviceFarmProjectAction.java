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
import hudson.model.Action;
import hudson.util.ChartUtil;
import hudson.util.Graph;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.util.ArrayList;

/**
 * AWS Device Farm specific action tied to a Jenkins project.
 * <p>
 * This class is used for the top-level project view of your project if it is configured to use AWS Device Farm. It is
 * responsible for serving up the project level graph (for all AWS Device Farm builds) as well as providing results for
 * the most recent AWS Device Farm runs.
 */
public class AWSDeviceFarmProjectAction implements Action {
    private AbstractProject<?, ?> project;

    /**
     * Create new AWS Device Farm project action.
     *
     * @param project The Project which this action will be applied to.
     */
    public AWSDeviceFarmProjectAction(AbstractProject<?, ?> project) {
        this.project = project;
    }

    /**
     * Get project associated with this action.
     *
     * @return The project.
     */
    public AbstractProject<?, ?> getProject() {
        return project;
    }

    /**
     * Returns true if there are any builds in the associated project.
     *
     * @return Whether or not the graph should be displayed.
     */
    public boolean shouldDisplayGraph() {
        return AWSDeviceFarmUtils.previousAWSDeviceFarmBuildAction(project) != null;
    }

    /**
     * Return the action of last build associated with AWS Device Farm.
     *
     * @return The most recent build with AWS Device Farm or null.
     */
    public AWSDeviceFarmTestResultAction getLastBuildAction() {
        return AWSDeviceFarmUtils.previousAWSDeviceFarmBuildAction(project);
    }

    /**
     * Return the action of all previous builds associated with AWS Device Farm.
     *
     * @return An ArrayList of all AWS Device Farm build actions for this project.
     */
    public ArrayList<AWSDeviceFarmTestResultAction> getLastBuildActions() {
        return AWSDeviceFarmUtils.previousAWSDeviceFarmBuilds(project);
    }

    /**
     * Return the actions of 'n' previous builds associated with AWS Device Farm.
     *
     * @param n Number of previous builds to get.
     * @return An ArrayList of the previous builds.
     */
    public ArrayList<AWSDeviceFarmTestResultAction> getLastBuildActions(int n) {
        ArrayList<AWSDeviceFarmTestResultAction> actions = getLastBuildActions();
        return new ArrayList<AWSDeviceFarmTestResultAction>(actions.subList(0, Math.min(n, actions.size())));
    }

    /**
     * Serve up AWS Device Farm project page which redirects to the latest test results or 404.
     *
     * @param request  The request object.
     * @param response The response object.
     * @throws IOException
     */
    @SuppressWarnings("unused")
    public void doIndex(StaplerRequest request, StaplerResponse response) throws IOException {
        AbstractBuild<?, ?> prev = AWSDeviceFarmUtils.previousAWSDeviceFarmBuild(project);
        if (prev == null) {
            response.sendRedirect2("404");
        } else {
            // Redirect to build page of most recent AWS Device Farm test run.
            response.sendRedirect2(String.format("../%d/%s", prev.getNumber(), getUrlName()));
        }
    }

    /**
     * Return trend graph of all AWS Device Farm results for this project.
     *
     * @param request  The request object.
     * @param response The response object.
     * @throws IOException
     */
    @SuppressWarnings("unused")
    public void doGraph(StaplerRequest request, StaplerResponse response) throws IOException {
        // Abort if having Java AWT issues.
        if (ChartUtil.awtProblemCause != null) {
            response.sendRedirect2(String.format("%s/images/headless.png", request.getContextPath()));
            return;
        }

        // Get previous AWS Device Farm build and results.
        AWSDeviceFarmTestResultAction prev = getLastBuildAction();
        if (prev == null) {
            return;
        }
        AWSDeviceFarmTestResult result = prev.getResult();
        if (result == null) {
            return;
        }

        // Create new graph for the AWS Device Farm results of all runs in this project.
        Graph graph = AWSDeviceFarmGraph.createResultTrendGraph(prev.getOwner(), false, result.getPreviousResults());
        graph.doPng(request, response);
    }

    /**
     * Get the icon file name.
     *
     * @return The path to the icon.
     */
    public String getIconFileName() {
        //return "http://g-ecx.images-amazon.com/images/G/01/aws-device-farm/service-icon.svg";
        return "/plugin/aws-device-farm/service-icon.svg";
    }

    /**
     * Get the display name.
     *
     * @return The display name.
     */
    public String getDisplayName() {
        return "AWS Device Farm";
    }

    /**
     * Get the URL name.
     *
     * @return The URL name.
     */
    public String getUrlName() {
        return "aws-device-farm";
    }
}
