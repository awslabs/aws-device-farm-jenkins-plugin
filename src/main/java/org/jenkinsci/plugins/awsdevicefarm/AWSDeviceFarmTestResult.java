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

import com.amazonaws.services.devicefarm.model.ExecutionResult;
import com.amazonaws.services.devicefarm.model.Run;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.tasks.test.TestObject;
import hudson.tasks.test.TestResult;
import hudson.util.ChartUtil;
import hudson.util.Graph;
import org.apache.commons.lang.WordUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Result object which contains high level result information, pass/warn/fail counters, and performance
 * stats for an AWS Device Farm run.
 * <p>
 * A "snapshot" of the results of an AWS Device Farm run at some point during its execution lifecycle.
 * New result objects will be created periodically during active runs and a final object will be stored
 * and attached to a Jenkins run, once it has completed.
 */
public class AWSDeviceFarmTestResult extends TestResult {

    private static final HashMap<ExecutionResult, Result> resultMap = new HashMap<ExecutionResult, Result>();
    private static final int DefaultTrendGraphSize = 3;

    private String id = "";
    private String status = "";
    private ExecutionResult result;

    private int passCount = 0;
    private int warnCount = 0;
    private int failCount = 0;
    private int totalCount = 0;
    private int errorCount = 0;
    private int skipCount = 0;
    private int stopCount = 0;

    private int duration = 0;

    private String url = "";
    private String project = "";

    private AbstractBuild<?, ?> build;

    public AWSDeviceFarmTestResult(AbstractBuild<?, ?> build, Run run) {
        this.build = build;

        if (run != null) {
            this.id = AWSDeviceFarmUtils.getRunIdFromArn(run.getArn());
            this.status = run.getStatus();
            this.result = ExecutionResult.fromValue(run.getResult());
            this.passCount = run.getCounters().getPassed();
            this.warnCount = run.getCounters().getWarned();
            this.failCount = run.getCounters().getFailed();
            this.totalCount = run.getCounters().getTotal();
            this.errorCount = run.getCounters().getErrored();
            this.skipCount = run.getCounters().getSkipped();

            try {
                this.stopCount = run.getCounters().getStopped();
            } catch (NullPointerException e) {
                // Ignore this until the stopped bug is fixed.
            }

            this.url = AWSDeviceFarmUtils.getRunUrlFromArn(run.getArn());
        }
    }

    /**
     * Map Device Farm results to Jenkins results.
     */
    static {
        resultMap.put(ExecutionResult.PASSED, Result.SUCCESS);
        resultMap.put(ExecutionResult.WARNED, Result.UNSTABLE);
        resultMap.put(ExecutionResult.FAILED, Result.FAILURE);
        resultMap.put(ExecutionResult.SKIPPED, Result.FAILURE);
        resultMap.put(ExecutionResult.ERRORED, Result.FAILURE);
        resultMap.put(ExecutionResult.STOPPED, Result.FAILURE);
    }

    /**
     * Create the graph image for the number of pass/warn/fail results in a test run, for the previous three Jenkins runs.
     *
     * @param request
     * @param response
     * @throws IOException
     */
    @SuppressWarnings("unused")
    public void doGraph(StaplerRequest request, StaplerResponse response) throws IOException {
        // Abort if having Java AWT issues.
        if (ChartUtil.awtProblemCause != null) {
            response.sendRedirect2(String.format("%s/images/headless.png", request.getContextPath()));
            return;
        }

        // Check the "If-Modified-Since" header and abort if we don't need re-create the graph.
        if (isCompleted()) {
            Calendar timestamp = getOwner().getTimestamp();
            if (request.checkIfModified(timestamp, response)) {
                return;
            }
        }

        // Create new graph for this AWS Device Farm result.
        Graph graph = AWSDeviceFarmGraph.createResultTrendGraph(build, isCompleted(), getPreviousResults(DefaultTrendGraphSize));
        graph.doPng(request, response);
    }

    /**
     * Create the graph image for the number of device minutes used in a test run, for the previous three Jenkins runs.
     *
     * @param request
     * @param response
     * @throws IOException
     */
    @SuppressWarnings("unused")
    public void doDurationGraph(StaplerRequest request, StaplerResponse response) throws IOException {
        // Abort if having Java AWT issues.
        if (ChartUtil.awtProblemCause != null) {
            response.sendRedirect2(String.format("%s/images/headless.png", request.getContextPath()));
            return;
        }

        // Check the "If-Modified-Since" header and abort if we don't need re-create the graph.
        if (isCompleted()) {
            Calendar timestamp = getOwner().getTimestamp();
            if (request.checkIfModified(timestamp, response)) {
                return;
            }
        }

        // Create new duration graph for this AWS Device Farm result.
        Graph graph = AWSDeviceFarmGraph.createDurationTrendGraph(build, isCompleted(), getPreviousResults(DefaultTrendGraphSize));
        graph.doPng(request, response);
    }

    /**
     * Return the AWS Device Farm result of the most recent build which contained an AWS Device Farm run.
     *
     * @return The result of most recent build
     */
    @SuppressWarnings("unused")
    public AWSDeviceFarmTestResult getPreviousResult() {
        AWSDeviceFarmTestResultAction prev = AWSDeviceFarmUtils.previousAWSDeviceFarmBuildAction(build.getProject());
        if (prev == null) {
            return null;
        }
        return prev.getResult();
    }

    /**
     * Return a list of up to (n) of the most recent/previous AWS Device Farm results.
     *
     * @param n number of results requested
     * @return list of previous results
     */
    @SuppressWarnings("unused")
    protected List<AWSDeviceFarmTestResult> getPreviousResults(int n) {
        List<AWSDeviceFarmTestResult> results = getPreviousResults();
        return results.subList(Math.max(0, results.size() - n), results.size());
        //return results.subList(0, Math.min(n, results.size()));
    }

    /**
     * Return a list of all AWS Device Farm results from all builds previous to the build that this result is tied to.
     * The list is return in increasing, sequential order.
     *
     * @return list of previous results
     */
    @SuppressWarnings("unused")
    protected List<AWSDeviceFarmTestResult> getPreviousResults() {
        ArrayList<AWSDeviceFarmTestResultAction> actions = AWSDeviceFarmUtils.previousAWSDeviceFarmBuilds(build.getProject());
        ArrayList<AWSDeviceFarmTestResult> results = new ArrayList<AWSDeviceFarmTestResult>();
        for (AWSDeviceFarmTestResultAction action : actions) {
            AWSDeviceFarmTestResult result = action.getResult();
            if (result == null) {
                continue;
            }
            results.add(result);
        }
        Collections.reverse(results);
        return results;
    }

    public String getReportUrl() {
        return url;
    }

    public String getRunId() {
        return id;
    }

    public String getBuildNumber() {
        return "" + build.getNumber();
    }

    public String getStatus() {
        return WordUtils.capitalizeFully(status);
    }

    public String getProject() {
        return project;
    }

    /**
     * Return true if this result is "completed". A completed result is a result
     * who is both marked as completed and has had its results archived for download.
     *
     * @return true if completed
     */
    public Boolean isCompleted() {
        return status != null
                && status.equalsIgnoreCase("COMPLETED");
    }

    /**
     * Return a Jenkins build result which matches the result status from AWS Device Farm.
     *
     * @return Jenkins build result
     */
    public Result getBuildResult(Boolean ignoreRunError) {
        if (ignoreRunError != null && ignoreRunError && ExecutionResult.ERRORED.equals(result)) {
            if (skipCount > 0) {
                result = ExecutionResult.SKIPPED;
            } else if (stopCount > 0) {
                result = ExecutionResult.STOPPED;
            } else if (failCount > 0) {
                result = ExecutionResult.FAILED;
            } else if (warnCount > 0) {
                result = ExecutionResult.WARNED;
            } else {
                result = ExecutionResult.PASSED;
            }
        }
        if (!ExecutionResult.ERRORED.equals(result)) {
            if (skipCount > 0) {
                result = ExecutionResult.SKIPPED;
            }
        }
        return resultMap.get(result);
    }

    /**
     * Returns the AWS Device Farm test result for the given id. The id will likely be the default
     * value generated by Jenkins, which is usually just the human readable name. Return this
     * test result the ID's match, otherwise scan our previous runs looking for a matching result.
     * If no match is found, return null.
     *
     * @param id test result ID
     * @return the AWS Device Farm test result for the given id
     */
    public TestResult findCorrespondingResult(String id) {
        if (id == null || getId().equalsIgnoreCase(id)) {
            return this;
        }
        ArrayList<AWSDeviceFarmTestResultAction> prevActions = AWSDeviceFarmUtils.previousAWSDeviceFarmBuilds(build.getProject());
        if (prevActions == null || prevActions.isEmpty()) {
            return null;
        }
        for (AWSDeviceFarmTestResultAction action : prevActions) {
            AWSDeviceFarmTestResult prevResult = action.getResult();
            if (prevResult == null) {
                continue;
            }
            if (prevResult.getId().equalsIgnoreCase(id)) {
                return prevResult;
            }
        }
        return null;
    }

    /**
     * @return number of test passes in this result
     */
    @Override
    public int getPassCount() {
        return passCount;
    }

    /**
     * @return number of test warnings in this result
     */
    public int getWarnCount() {
        return warnCount;
    }

    /**
     * @return number of test failures in this result
     */
    @Override
    public int getFailCount() {
        return failCount;
    }

    /**
     * @return number of test errors in this result
     */
    public int getStopCount() {
        return stopCount;
    }

    /**
     * @return number of test errors in this result
     */
    public int getErrorCount() {
        return errorCount;
    }

    /**
     * @return number of test skipped in this result
     */
    @Override
    public int getSkipCount() {
        return skipCount;
    }

    /**
     * @return total number of tests run in this result
     */
    @Override
    public int getTotalCount() {
        return totalCount;
    }

    /**
     * @return total number of device minutes used by the run which generated this result
     */
    @Override
    public float getDuration() {
        return (float) duration;
    }

    /**
     * @return true if there are no tests for this result, false otherwise
     */
    public Boolean isEmpty() {
        return getTotalCount() == 0;
    }

    public TestObject getParent() {
        return null;
    }

    public AbstractBuild<?, ?> getOwner() {
        return build;
    }

    public String getDisplayName() {
        return String.format("AWS Device Farm #%s", id);
    }

    @Override
    public String getName() {
        return String.format("AWSDeviceFarm%s", id);
    }

    @Override
    public String getSearchUrl() {
        return "aws-device-farm";
    }
}
