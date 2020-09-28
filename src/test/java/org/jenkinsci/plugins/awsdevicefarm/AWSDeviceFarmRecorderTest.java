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

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

@For(AWSDeviceFarmRecorder.class)
public class AWSDeviceFarmRecorderTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-50483")
    public void dataSerializationSmokeTest() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        AWSDeviceFarmRecorder rec = new AWSDeviceFarmRecorder(
                "TestProjectName", "TestDevicePool", null, null,
                null, null, "APPIUM_JAVA_JUNIT", false, false, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, false, false,
                null, null, null, null, null, null,
                false, false, false, 10, false, false,
                false, false, false, null, true, "", "A, B"
        );
        p.getPublishersList().add(rec);

        // Try to build it. It is not supposed to work, but we will filter out the JEP-200 exception
        final QueueTaskFuture<FreeStyleBuild> future = p.scheduleBuild2(0);
        final FreeStyleBuild build = future.get();
        j.assertBuildStatus(Result.FAILURE, build);
        // Currently it fails with Either IAM Role ARN or AKID/SKID must be set and does not report the serialization issue after that

        // No matter what, we should be able to save the project even after the failure
        p.save();
        build.save();
    }
}
