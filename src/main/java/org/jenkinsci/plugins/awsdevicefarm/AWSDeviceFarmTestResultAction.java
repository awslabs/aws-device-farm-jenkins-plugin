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

import com.amazonaws.services.devicefarm.model.GetRunResult;
import com.amazonaws.services.devicefarm.model.Run;
import com.amazonaws.services.devicefarm.model.ScheduleRunResult;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.tasks.test.AbstractTestResultAction;
import org.kohsuke.stapler.StaplerProxy;

import javax.annotation.CheckForNull;
import java.io.PrintStream;

/**
 * Action which controls the execution management and results updating for AWS Device Farm runs.
 * <p>
 * This object is analogous to an AWS Device Farm run.
 *
 * @author hawker
 */
public class AWSDeviceFarmTestResultAction extends AbstractTestResultAction<AWSDeviceFarmTestResultAction> implements StaplerProxy {

    private static final int DefaultUpdateInterval = 30 * 1000;
    private AWSDeviceFarmTestResult result;

    public AWSDeviceFarmTestResultAction(AbstractBuild<?, ?> owner, AWSDeviceFarmTestResult result) {
        super(owner);
        this.result = result;
    }

    public AWSDeviceFarmTestResultAction(hudson.model.Run<?, ?> owner, AWSDeviceFarmTestResult result) {
        super();
        onAttached(owner);
        this.result = result;
    }

    /**
     * @deprecated log is no longer passed, use {@link #AWSDeviceFarmTestResultAction(AbstractBuild, AWSDeviceFarmTestResult)}
     */
    @Deprecated
    public AWSDeviceFarmTestResultAction(AbstractBuild<?, ?> owner, AWSDeviceFarmTestResult result, @CheckForNull PrintStream log) {
        this(owner, result);
    }

    /**
     * @return the Jenkins result which matches the result of this AWS Device Farm run
     */
    public Result getBuildResult(Boolean ignoreRunError) {
        return getResult().getBuildResult(ignoreRunError);
    }

    public void waitForRunCompletion(AWSDeviceFarm adf, ScheduleRunResult runResult) throws InterruptedException {
        waitForRunCompletion(adf, runResult, TaskListener.NULL);
    }

    /**
     * Blocking function which periodically polls the given AWS Device Farm run until its completed. During this waiting period,
     * we will grab the latest results reported by Device Farm and updated our internal result "snapshot" which will be used
     * to populate/inform the UI of test results/progress.
     *
     * @param runResult
     */
    public void waitForRunCompletion(AWSDeviceFarm adf, ScheduleRunResult runResult, TaskListener listener) throws InterruptedException {
        PrintStream log = listener.getLogger();
        while (true) {
            GetRunResult latestRunResult = adf.describeRun(runResult.getRun().getArn());
            Run run = latestRunResult.getRun();
            result = new AWSDeviceFarmTestResult(owner, run);
            writeToLog(log, String.format("Run %s status %s", run.getName(), run.getStatus()));
            if (result.isCompleted()) {
                break;
            }
            try {
                Thread.sleep(DefaultUpdateInterval);
            } catch (InterruptedException ex) {
                writeToLog(log, String.format("Thread interrupted while waiting for the Run to complete"));
                throw ex;
            }
        }
    }

    /**
     * @return the most recent AWS Device Farm test action from the previous build
     */
    @Override
    public AWSDeviceFarmTestResultAction getPreviousResult() {
        AbstractBuild<?, ?> build = getOwner();
        if (owner == null) {
            return null;
        }
        return AWSDeviceFarmUtils.previousAWSDeviceFarmBuildAction(build.getProject());
    }

    /**
     * @return a snapshot of the current results for this AWS Device Farm run
     */
    @Override
    public AWSDeviceFarmTestResult getResult() {
        return result;
    }

    /**
     * @return a snapshot of the current results for this AWS Device Farm run
     */
    public AWSDeviceFarmTestResult getTarget() {
        return getResult();
    }

    /**
     * @return the number of failed tests for this AWS Device Farm run
     */
    @Override
    public int getFailCount() {
        AWSDeviceFarmTestResult result = getResult();
        if (result != null) {
            return getResult().getFailCount();
        } else {
            return -1;
        }
    }

    /**
     * @return the total number of tests for this AWS Device Farm run
     */
    @Override
    public int getTotalCount() {
        AWSDeviceFarmTestResult result = getResult();
        if (result != null) {
            return getResult().getTotalCount();
        } else {
            return -1;
        }
    }

    public AbstractBuild<?, ?> getOwner() {
        return owner;
    }

    @Override
    public String getUrlName() {
        return "aws-device-farm";
    }

    @Override
    public String getDisplayName() {
        return "AWS Device Farm";
    }

    @Override
    public String getIconFileName() {
        return "/plugin/aws-device-farm/service-icon.svg";
    }

    //// Helper Methods

    private void writeToLog(PrintStream log, String message) {
        if (log != null) {
            log.println(String.format("[AWSDeviceFarm] %s", message));
        }
    }
}
