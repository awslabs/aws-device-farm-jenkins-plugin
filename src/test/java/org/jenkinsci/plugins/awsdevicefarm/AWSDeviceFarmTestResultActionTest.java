//
// Copyright 2018 CloudBees, Inc.
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

import com.amazonaws.services.devicefarm.model.Counters;
import com.amazonaws.services.devicefarm.model.DeviceMinutes;
import com.amazonaws.services.devicefarm.model.ExecutionResult;
import com.amazonaws.services.devicefarm.model.NetworkProfile;
import com.amazonaws.services.devicefarm.model.Run;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.util.LogTaskListener;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.logging.Level;
import java.util.logging.Logger;

@For(AWSDeviceFarmTestResultAction.class)
public class AWSDeviceFarmTestResultActionTest {

    private static final Logger LOGGER = Logger.getLogger(AWSDeviceFarmTestResultActionTest.class.getName());

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-50483")
    public void roundtrip() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild build = j.buildAndAssertSuccess(p);

        // Here we set a run with all fields which are potentially not allowed in JEP-200
        // TODO: to make the test more robust, it is possible to iterate through fields and use Setters with default constructors. Looks like it's fine for StructuredPojo
        Run awsRun = new Run();
        awsRun.setArn("a:b:c:d:e:f:foo/bar");
        awsRun.setResult(ExecutionResult.SKIPPED);
        awsRun.setNetworkProfile(new NetworkProfile());
        awsRun.setDeviceMinutes(new DeviceMinutes());
        Counters counters = new Counters();
        counters.setPassed(0);
        counters.setFailed(0);
        counters.setSkipped(1);
        counters.setErrored(0);
        counters.setStopped(0);
        counters.setWarned(0);
        counters.setTotal(1);
        awsRun.setCounters(counters);

        // We intentionally use the deprecated constructor to check whether logger is retained
        AWSDeviceFarmTestResult res = new AWSDeviceFarmTestResult(build, awsRun);
        AWSDeviceFarmTestResultAction a = new AWSDeviceFarmTestResultAction(build, res, new LogTaskListener(LOGGER, Level.SEVERE).getLogger());
        build.addAction(a);

        // Check that the action is still there after reload
        build.save();
        build.reload();
        Assert.assertNotNull("AWSDeviceFarmTestResultAction should be retained after the restart", build.getAction(AWSDeviceFarmTestResultAction.class));
    }

}
