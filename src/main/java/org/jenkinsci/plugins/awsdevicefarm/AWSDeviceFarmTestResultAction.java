package org.jenkinsci.plugins.awsdevicefarm;

import java.io.PrintStream;

import com.amazonaws.services.devicefarm.model.GetRunResult;
import com.amazonaws.services.devicefarm.model.Run;
import com.amazonaws.services.devicefarm.model.ScheduleRunResult;

import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.tasks.test.AbstractTestResultAction;

import java.lang.InterruptedException;

import org.kohsuke.stapler.StaplerProxy;

/**
 * Action which controls the execution management and results updating for AWS Device Farm runs.
 * 
 * This object is analogous to an AWS Device Farm run.
 * 
 * @author hawker
 *
 */
public class AWSDeviceFarmTestResultAction extends AbstractTestResultAction<AWSDeviceFarmTestResultAction> implements StaplerProxy {

    private static final int DefaultUpdateInterval = 30 * 1000;

    private PrintStream log;
    private AWSDeviceFarmTestResult result;

    public AWSDeviceFarmTestResultAction(hudson.model.Run<?, ?> owner, AWSDeviceFarmTestResult result, PrintStream log) {

        super();
        onAttached(owner);
        this.log = log;
        this.result = result;
    }

    /**
     * Returns the Jenkins result which matches the result of this AWS Device Farm run.
     * @return
     */
    public Result getBuildResult(Boolean ignoreRunError) {
        return getResult().getBuildResult(ignoreRunError);
    }

    /**
     * Blocking function which periodically polls the given AWS Device Farm run until its completed. During this waiting period,
     * we will grab the latest results reported by Device Farm and updated our internal result "snapshot" which will be used
     * to populate/inform the UI of test results/progress.
     * @param runResult
     */
    public void waitForRunCompletion(AWSDeviceFarm adf, ScheduleRunResult runResult) throws InterruptedException {
        while (true) {
            GetRunResult latestRunResult = adf.describeRun(runResult.getRun().getArn());
            Run run = latestRunResult.getRun();
            result = new AWSDeviceFarmTestResult(owner, run);
            writeToLog(String.format("Run %s status %s", run.getName(), run.getStatus()));
            if (result.isCompleted()) {
                break;
            }
            try {
                Thread.sleep(DefaultUpdateInterval);
            }
            catch(InterruptedException ex) {
                writeToLog(String.format("Thread interrupted while waiting for the Run to complete"));
                throw ex;
            }
        }
    }

    /**
     * Returns the most recent AWS Device Farm test action from the previous build.
     * @return
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
     * Returns a snapshot of the current results for this AWS Device Farm run.
     * @return
     */
    @Override
    public AWSDeviceFarmTestResult getResult() {
        return result;
    }

    /**
     * Returns a snapshot of the current results for this AWS Device Farm run.
     * @return
     */
    public AWSDeviceFarmTestResult getTarget() {
        return getResult();
    }

    /**
     * Returns the number of failed tests for this AWS Device Farm run.
     * @return
     */
    @Override
    public int getFailCount() {
        AWSDeviceFarmTestResult result = getResult();
        if (result !=null) {
            return getResult().getFailCount();
        }
        else {
            return -1;
        }
    }

    /**
     * Returns the total number of tests for this AWS Device Farm run.
     * @return
     */
    @Override
    public int getTotalCount() {
        AWSDeviceFarmTestResult result = getResult();
        if (result !=null) {
            return getResult().getTotalCount();
        }
        else {
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

    private void writeToLog(String message) {
        if (log != null) {
            log.println(String.format("[AWSDeviceFarm] %s", message));
        }
    }
}
