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

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.devicefarm.AWSDeviceFarmClientBuilder;
import com.amazonaws.services.devicefarm.AWSDeviceFarmClient;
import com.amazonaws.services.devicefarm.model.AccountSettings;
import com.amazonaws.services.devicefarm.model.ArtifactCategory;
import com.amazonaws.services.devicefarm.model.CreateUploadRequest;
import com.amazonaws.services.devicefarm.model.DevicePool;
import com.amazonaws.services.devicefarm.model.ExecutionConfiguration;
import com.amazonaws.services.devicefarm.model.GetAccountSettingsRequest;
import com.amazonaws.services.devicefarm.model.GetRunRequest;
import com.amazonaws.services.devicefarm.model.GetRunResult;
import com.amazonaws.services.devicefarm.model.GetUploadRequest;
import com.amazonaws.services.devicefarm.model.GetUploadResult;
import com.amazonaws.services.devicefarm.model.ListArtifactsRequest;
import com.amazonaws.services.devicefarm.model.ListArtifactsResult;
import com.amazonaws.services.devicefarm.model.ListDevicePoolsRequest;
import com.amazonaws.services.devicefarm.model.ListDevicePoolsResult;
import com.amazonaws.services.devicefarm.model.ListJobsRequest;
import com.amazonaws.services.devicefarm.model.ListJobsResult;
import com.amazonaws.services.devicefarm.model.ListProjectsRequest;
import com.amazonaws.services.devicefarm.model.ListProjectsResult;
import com.amazonaws.services.devicefarm.model.ListSuitesRequest;
import com.amazonaws.services.devicefarm.model.ListSuitesResult;
import com.amazonaws.services.devicefarm.model.ListTestsRequest;
import com.amazonaws.services.devicefarm.model.ListTestsResult;
import com.amazonaws.services.devicefarm.model.ListUploadsRequest;
import com.amazonaws.services.devicefarm.model.ListUploadsResult;
import com.amazonaws.services.devicefarm.model.NotFoundException;
import com.amazonaws.services.devicefarm.model.Project;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.devicefarm.model.ScheduleRunConfiguration;
import com.amazonaws.services.devicefarm.model.DeviceSelectionConfiguration;
import com.amazonaws.services.devicefarm.model.ScheduleRunRequest;
import com.amazonaws.services.devicefarm.model.ScheduleRunResult;
import com.amazonaws.services.devicefarm.model.ScheduleRunTest;
import com.amazonaws.services.devicefarm.model.Upload;
import com.amazonaws.services.devicefarm.model.UploadStatus;
import com.amazonaws.services.devicefarm.model.VPCEConfiguration;
import com.amazonaws.services.devicefarm.model.ListVPCEConfigurationsResult;
import com.amazonaws.services.devicefarm.model.ListVPCEConfigurationsRequest;
import hudson.EnvVars;
import hudson.FilePath;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jenkinsci.plugins.awsdevicefarm.test.AppiumWebJavaJUnitTest;
import org.jenkinsci.plugins.awsdevicefarm.test.AppiumWebJavaTestNGTest;
import org.jenkinsci.plugins.awsdevicefarm.test.AppiumWebPythonTest;
import org.jenkinsci.plugins.awsdevicefarm.test.AppiumWebRubyTest;
import org.jenkinsci.plugins.awsdevicefarm.test.AppiumWebNodeTest;
import org.jenkinsci.plugins.awsdevicefarm.test.AppiumJavaJUnitTest;
import org.jenkinsci.plugins.awsdevicefarm.test.AppiumJavaTestNGTest;
import org.jenkinsci.plugins.awsdevicefarm.test.AppiumPythonTest;
import org.jenkinsci.plugins.awsdevicefarm.test.AppiumRubyTest;
import org.jenkinsci.plugins.awsdevicefarm.test.AppiumNodeTest;
import org.jenkinsci.plugins.awsdevicefarm.test.CalabashTest;
import org.jenkinsci.plugins.awsdevicefarm.test.InstrumentationTest;
import org.jenkinsci.plugins.awsdevicefarm.test.UIAutomationTest;
import org.jenkinsci.plugins.awsdevicefarm.test.UIAutomatorTest;
import org.jenkinsci.plugins.awsdevicefarm.test.XCTestTest;
import org.jenkinsci.plugins.awsdevicefarm.test.XCTestUITest;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * AWS Device Farm API wrapper class.
 */
public class AWSDeviceFarm {
    private com.amazonaws.services.devicefarm.AWSDeviceFarm api;
    private PrintStream log;
    private FilePath workspace;
    private FilePath artifactsDir;
    private EnvVars env;

    private static final Integer DEFAULT_JOB_TIMEOUT_MINUTE = 60;
    private static final String APPIUM_RUBY_TEST_SPEC = "APPIUM_RUBY_TEST_SPEC";
    private static final String APPIUM_NODE_TEST_SPEC = "APPIUM_NODE_TEST_SPEC";
    private static final String APPIUM_WEB_RUBY_TEST_SPEC = "APPIUM_WEB_RUBY_TEST_SPEC";
    private static final String APPIUM_WEB_NODE_TEST_SPEC = "APPIUM_WEB_NODE_TEST_SPEC";
    private static final String CURATED = "CURATED";
    // The max timeout is set to 1 hr as roles provided as input can be chained or unchained.
    // Chained roles have a hard limit of 1 hr. After 1 hr the tokens are refreshed.
    // Requesting a session with timneout higher than 1 hr by default does not allow chained roles to be used
    private static final int MAX_ROLE_SESSION_TIMEOUT = 3600;

    //// Constructors

    /**
     * AWSDeviceFarm constructor.
     *
     * @param roleArn Role ARN to use for authentication.
     */
    public AWSDeviceFarm(String roleArn) {
        this(null, roleArn);
    }

    /**
     * AWSDeviceFarm constructor.
     *
     * @param creds AWSCredentials to use for authentication.
     */
    public AWSDeviceFarm(AWSCredentials creds) {
        this(creds, null);
    }

    /**
     * Private AWSDeviceFarm constructor. Uses the roleArn to generate STS creds if the roleArn isn't null; otherwise
     * just uses the AWSCredentials creds.
     *
     * @param creds   AWSCredentials creds to use for authentication.
     * @param roleArn Role ARN to use for authentication.
     */
    private AWSDeviceFarm(AWSCredentials creds, String roleArn) {
        AWSCredentialsProvider credProvider = null;
        if (roleArn != null) {
            credProvider = new STSAssumeRoleSessionCredentialsProvider
                    .Builder(roleArn, RandomStringUtils.randomAlphanumeric(8))
                    .withRoleSessionDurationSeconds(MAX_ROLE_SESSION_TIMEOUT)
                    .build();
        } else {
            credProvider = new AWSCredentialsProviderChain(new AWSStaticCredentialsProvider(creds));
        }

        ClientConfiguration clientConfiguration = new ClientConfiguration().withUserAgent("AWS Device Farm - Jenkins v1.0");
        AWSDeviceFarmClientBuilder builder = AWSDeviceFarmClientBuilder.standard();
        builder.setClientConfiguration(clientConfiguration);
        // Device Farm endpoint is only available in us-west-2 region
        builder.withRegion(Regions.US_WEST_2);
        builder.setCredentials(credProvider);
        api = builder.build();
        ((AWSDeviceFarmClient) api).setServiceNameIntern("devicefarm");
    }

    //// Builder Methods

    /**
     * Logger setter.
     *
     * @param logger The log print stream.
     * @return The AWSDeviceFarm object.
     */
    public AWSDeviceFarm withLogger(PrintStream logger) {
        this.log = logger;
        return this;
    }

    /**
     * Workspace setter.
     *
     * @param workspace The FilePath to the Jenkins workspace.
     * @return The AWSDeviceFarm object.
     */
    public AWSDeviceFarm withWorkspace(FilePath workspace) {
        this.workspace = workspace;
        return this;
    }

    /**
     * Artifacts directory setter.
     *
     * @param artifactsDir The FilePath to the Jenkins artifacts directory.
     * @return The AWSDeviceFarm object.
     */
    public AWSDeviceFarm withArtifactsDir(FilePath artifactsDir) {
        this.artifactsDir = artifactsDir;
        return this;
    }

    /**
     * Environment setter.
     *
     * @param env The EnvVars Jenkins environment.
     * @return The AWSDeviceFarm object.
     */
    public AWSDeviceFarm withEnv(EnvVars env) {
        this.env = env;
        return this;
    }

    //// AWS Device Farm Wrapper Methods

    /**
     * Get all Device Farm projects.
     *
     * @return A List of the Device Farm projects.
     */
    public List<Project> getProjects() {
        List<Project> projects = new ArrayList<Project>();
        ListProjectsResult result = api.listProjects(new ListProjectsRequest());
        projects.addAll(result.getProjects());
        while (result.getNextToken() != null) {
            ListProjectsRequest request = new ListProjectsRequest();
            request.setNextToken(result.getNextToken());
            result = api.listProjects(request);
            projects.addAll(result.getProjects());
        }
        return projects;
    }

    /**
     * Get Device Farm project by name.
     *
     * @param projectName String name of the Device Farm project.
     * @return The Device Farm project.
     * @throws AWSDeviceFarmException
     */
    public Project getProject(String projectName) throws AWSDeviceFarmException {
        for (Project p : getProjects()) {
            if (p.getName().equals(projectName)) {
                return p;
            }
        }
        throw new AWSDeviceFarmException(String.format("Project '%s' not found.", projectName));
    }

    public List<VPCEConfiguration> getVPCEConfigurations() {
        ListVPCEConfigurationsResult result = api.listVPCEConfigurations(new ListVPCEConfigurationsRequest());
        if (result == null) {
            return new ArrayList<VPCEConfiguration>();
        } else {
            return result.getVpceConfigurations();
        }
    }

    public VPCEConfiguration getVPCEConfiguration(String vpceServiceName) throws AWSDeviceFarmException {
        for (VPCEConfiguration v : getVPCEConfigurations()) {
            if (v.getVpceServiceName().equals(vpceServiceName)) {
                return v;
            }
        }
        throw new AWSDeviceFarmException(String.format("VPCE Service '%s' not found.", vpceServiceName));
    }

    /**
     * Get Device Farm device pools for a given Device Farm project.
     *
     * @param projectName String name of the Device Farm project.
     * @return A List of the Device Farm device pools.
     * @throws AWSDeviceFarmException
     */
    public List<DevicePool> getDevicePools(String projectName) throws AWSDeviceFarmException {
        return getDevicePools(getProject(projectName));
    }

    /**
     * Get Device Farm device pools for a given Device Farm project.
     *
     * @param project Device Farm Project.
     * @return A List of the Device Farm device pools.
     * @throws AWSDeviceFarmException
     */
    public List<DevicePool> getDevicePools(Project project) {
        ListDevicePoolsResult poolsResult = api.listDevicePools(new ListDevicePoolsRequest().withArn(project.getArn()));
        List<DevicePool> pools = poolsResult.getDevicePools();
        return pools;
    }

    /**
     * Get Device Farm TestSpecs for a given Device Farm project.
     *
     * @param projectName String name of the Device Farm project.
     * @return A List of the Device Farm TestSpecs.
     * @throws AWSDeviceFarmException
     */
    public List<Upload> getTestSpecs(String projectName) throws AWSDeviceFarmException {
        return getTestSpecs(getProject(projectName));
    }

    /**
     * Get Device Farm TestSpecs for a given Device Farm project.
     *
     * @param project Device Farm Project.
     * @return A List of the Device Farm TestSpecs.
     * @throws AWSDeviceFarmException
     */
    public List<Upload> getTestSpecs(Project project) throws AWSDeviceFarmException {
        List<Upload> allUploads = getUploads(project);
        List<Upload> testSpecUploads = new ArrayList<Upload>();
        for (Upload upload : allUploads) {
			if (upload.getType().contains("TEST_SPEC")
					&& UploadStatus.SUCCEEDED.toString().equals(upload.getStatus()) && !isRestrictedDefaultSpec(upload)) {
				testSpecUploads.add(upload);

			}
        }
        return testSpecUploads;
    }

    /**
     * Helper function to detect a default testspec file for frameworks that cannot use it.
     *
     * @param testSpec the testspec file to check
     *
     * @return true if it is a default spec file.
     * @throws AWSDeviceFarmException
     */
    public Boolean isRestrictedDefaultSpec(Upload testSpec) {
        if (testSpec != null) {
            if ((APPIUM_RUBY_TEST_SPEC.equals(testSpec.getType()) ||
                APPIUM_NODE_TEST_SPEC.equals(testSpec.getType()) ||
                APPIUM_WEB_RUBY_TEST_SPEC.equals(testSpec.getType()) ||
                APPIUM_WEB_NODE_TEST_SPEC.equals(testSpec.getType())) &&
                (CURATED.equals(testSpec.getCategory()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get Device Farm uploads for a given Device Farm project.
     *
     * @param project
     *            Device Farm Project.
     * @return A List of the Device Farm uploads.
     * @throws AWSDeviceFarmException
     */
    public List<Upload> getUploads(Project project) {

        List<Upload> uploads = new ArrayList<Upload>();
        ListUploadsResult result = api.listUploads(new ListUploadsRequest().withArn(project.getArn()));
        uploads.addAll(result.getUploads());
        while (result.getNextToken() != null) {
            ListUploadsRequest request = new ListUploadsRequest();
            request.setArn(project.getArn());
            request.setNextToken(result.getNextToken());
            result = api.listUploads(request);
            uploads.addAll(result.getUploads());
        }
        return uploads;

    }

    /**
     * Get Device Farm device pool by Device Farm project and device pool name.
     *
     * @param projectName    String name of the Device Farm project.
     * @param devicePoolName String name of the device pool.
     * @return The Device Farm device pool.
     * @throws AWSDeviceFarmException
     */
    public DevicePool getDevicePool(String projectName, String devicePoolName) throws AWSDeviceFarmException {
        return getDevicePool(getProject(projectName), devicePoolName);
    }

    /**
     * Get Device Farm device pool by Device Farm project and device pool name.
     *
     * @param project        The Device Farm project.
     * @param devicePoolName String name of the device pool.
     * @return The Device Farm device pool.
     * @throws AWSDeviceFarmException
     */
    public DevicePool getDevicePool(Project project, String devicePoolName) throws AWSDeviceFarmException {
        List<DevicePool> pools = getDevicePools(project);

        for (DevicePool dp : pools) {
            if (dp.getName().equals(devicePoolName)) {
                return dp;
            }
        }

        throw new AWSDeviceFarmException(String.format("DevicePool '%s' not found.", devicePoolName));
    }

    /**
     * Get Device Farm TestSpec by Device Farm project and TestSpec name.
     *
     * @param project        The Device Farm project.
     * @param testSpecName String name of the Device Farm testSpec.
     * @return The TestSpec.
     * @throws AWSDeviceFarmException
     */
    public Upload getTestSpec(Project project, String testSpecName) throws AWSDeviceFarmException {
        List<Upload> testSpecUploads = getTestSpecs(project);

        for (Upload upload : testSpecUploads) {
            if (upload.getName().equals(testSpecName)) {
                return upload;
            }
        }

        throw new AWSDeviceFarmException(String.format("TestSpec '%s' not found.", testSpecName));
    }

    /**
     * Upload an app to Device Farm to be tested.
     *
     * @param project     The Device Farm project to upload to.
     * @param appArtifact String path to the app to be uploaded to Device Farm.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadApp(Project project, String appArtifact) throws InterruptedException, IOException, AWSDeviceFarmException {
        AWSDeviceFarmUploadType type;
        if (appArtifact.toLowerCase().endsWith("apk")) {
            type = AWSDeviceFarmUploadType.ANDROID_APP;
        } else if (appArtifact.toLowerCase().endsWith("ipa") || appArtifact.toLowerCase().endsWith("zip")) {
            type = AWSDeviceFarmUploadType.IOS_APP;
        } else {
            throw new AWSDeviceFarmException(String.format("Unknown app artifact to upload: %s", appArtifact));
        }

        return upload(project, appArtifact, type);
    }

    /**
     * Upload an extra data file to Device Farm.
     *
     * @param project           The Device Farm project to upload to.
     * @param extraDataArtifact String path to the extra data to be uploaded to Device Farm.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadExtraData(Project project, String extraDataArtifact) throws InterruptedException, IOException, AWSDeviceFarmException {
        AWSDeviceFarmUploadType type;
        if (extraDataArtifact.toLowerCase().endsWith("zip")) {
            type = AWSDeviceFarmUploadType.EXTERNAL_DATA;
        } else {
            throw new AWSDeviceFarmException(String.format("Unknown extra data file artifact to upload: %s", extraDataArtifact));
        }

        return upload(project, extraDataArtifact, type);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, InstrumentationTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getArtifact(), AWSDeviceFarmUploadType.INSTRUMENTATION);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, CalabashTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getFeatures(), AWSDeviceFarmUploadType.CALABASH);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, UIAutomatorTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.UIAUTOMATOR);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, UIAutomationTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.UIAUTOMATION);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, XCTestTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.XCTEST);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, XCTestUITest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.XCTEST_UI);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, AppiumJavaTestNGTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.APPIUM_JAVA_TESTNG);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, AppiumJavaJUnitTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.APPIUM_JAVA_JUNIT);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, AppiumPythonTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.APPIUM_PYTHON);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, AppiumRubyTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.APPIUM_RUBY);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, AppiumNodeTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.APPIUM_NODE);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, AppiumWebJavaTestNGTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.APPIUM_WEB_JAVA_TESTNG);
    }

    /**
     * Upload a test to Device Farm.
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, AppiumWebJavaJUnitTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.APPIUM_WEB_JAVA_JUNIT);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, AppiumWebPythonTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.APPIUM_WEB_PYTHON);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, AppiumWebRubyTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.APPIUM_WEB_RUBY);
    }

    /**
     * Upload a test to Device Farm.
     *
     * @param project The Device Farm project to upload to.
     * @param test    Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, AppiumWebNodeTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.APPIUM_WEB_NODE);
    }

    /**
     * Private method to handle uploading apps and tests to Device Farm.
     *
     * @param project    The Device Farm project to upload to.
     * @param artifact   Possibly glob-y path to the file to be uploaded.
     * @param uploadType The type of upload (app/test/etc.).
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    private Upload upload(Project project, String artifact, AWSDeviceFarmUploadType uploadType) throws InterruptedException, IOException, AWSDeviceFarmException {
        if (artifact == null || artifact.isEmpty()) {
            throw new AWSDeviceFarmException("Must have an artifact path.");
        }

        File file = getArtifactFile(env.expand(artifact));
        if (file == null || !file.exists()) {
            throw new AWSDeviceFarmException(String.format("File artifact %s not found.", artifact));
        }

        return upload(file, project, uploadType);
    }

    /**
     * Private method to handle uploading apps and tests to Device Farm.
     *
     * @param file       The file to upload.
     * @param project    The Device Farm project to upload to.
     * @param uploadType The type of upload (app/test/etc.).
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    private Upload upload(File file, Project project, AWSDeviceFarmUploadType uploadType) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(file, project, uploadType, true);
    }

    /**
     * Private method to handle upload apps and tests to Device Farm.
     *
     * @param file        The file to upload.
     * @param project     TheDevice Farm project to upload to.
     * @param uploadType  The type of upload (app/test/etc.).
     * @param synchronous Whether or not to wait for the upload to complete before returning.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    private Upload upload(File file, Project project, AWSDeviceFarmUploadType uploadType, Boolean synchronous) throws InterruptedException, IOException, AWSDeviceFarmException {
        CreateUploadRequest appUploadRequest = new CreateUploadRequest()
                .withName(file.getName())
                .withProjectArn(project.getArn())
                .withContentType("application/octet-stream")
                .withType(uploadType.toString());
        Upload upload = api.createUpload(appUploadRequest).getUpload();

        CloseableHttpClient httpClient = HttpClients.createSystem();
        HttpPut httpPut = new HttpPut(upload.getUrl());
        httpPut.setHeader("Content-Type", upload.getContentType());

        FileEntity entity = new FileEntity(file);
        httpPut.setEntity(entity);

        writeToLog(String.format("Uploading %s to S3", file.getName()));
        HttpResponse response = httpClient.execute(httpPut);
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new AWSDeviceFarmException(String.format("Upload returned non-200 responses: %d", response.getStatusLine().getStatusCode()));
        }

        if (synchronous) {
            while (true) {
                GetUploadRequest describeUploadRequest = new GetUploadRequest()
                        .withArn(upload.getArn());
                GetUploadResult describeUploadResult = api.getUpload(describeUploadRequest);
                String status = describeUploadResult.getUpload().getStatus();

                if ("SUCCEEDED".equalsIgnoreCase(status)) {
                    writeToLog(String.format("Upload %s succeeded", file.getName()));
                    break;
                } else if ("FAILED".equalsIgnoreCase(status)) {
                    writeToLog(String.format("Error message from device farm: '%s'", describeUploadResult.getUpload().getMetadata()));
                    throw new AWSDeviceFarmException(String.format("Upload %s failed!", upload.getName()));
                } else {
                    try {
                        writeToLog(String.format("Waiting for upload %s to be ready (current status: %s)", file.getName(), status));
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        writeToLog(String.format("Thread interrupted while waiting for the upload to complete"));
                        throw e;
                    }
                }
            }
        }

        return upload;
    }

    /**
     * Schedule a test run on Device Farm.
     *
     * @param projectArn    The ARN of the Device Farm project to run the test on.
     * @param name          The name of the test run.
     * @param appArn        The ARN of the app to test.
     * @param devicePoolArn The ARN of the device pool to test against.
     * @param test          The run test.
     * @param configuration The run configuration.
     * @return The result of the schedle run.
     * @throws AWSDeviceFarmException
     */
    public ScheduleRunResult scheduleRun(String projectArn,
                                         String name,
                                         String appArn,
                                         String devicePoolArn,
                                         ScheduleRunTest test,
                                         Integer jobTimeoutMinutes,
                                         ScheduleRunConfiguration configuration,
                                         Boolean videoCapture,
                                         Boolean skipAppResign,
                                         DeviceSelectionConfiguration deviceSelectionConfiguration) throws AWSDeviceFarmException {
        ScheduleRunRequest request = new ScheduleRunRequest()
                .withProjectArn(projectArn)
                .withName(name)
                .withTest(test);

        ExecutionConfiguration exeConfiguration = new ExecutionConfiguration();
        if (!jobTimeoutMinutes.equals(DEFAULT_JOB_TIMEOUT_MINUTE)) {
            exeConfiguration.setJobTimeoutMinutes(jobTimeoutMinutes);
        }
        exeConfiguration.setVideoCapture(videoCapture);
        exeConfiguration.setSkipAppResign(skipAppResign);
        request.withExecutionConfiguration(exeConfiguration);

         if (deviceSelectionConfiguration != null && (deviceSelectionConfiguration.getMaxDevices() == null || deviceSelectionConfiguration.getMaxDevices() == 0)) {
             throw new IllegalArgumentException("Max devices must be set when using device selection configuration.");
        }

        if (deviceSelectionConfiguration != null) {
            request.withDeviceSelectionConfiguration(deviceSelectionConfiguration);
            writeToLog(String.format("Setting device selection config"));
        } else {
            request.withDevicePoolArn(devicePoolArn);
            writeToLog(String.format("Setting device pool"));
        }


        if (configuration != null) {
            request.withConfiguration(configuration);
        }

        if (appArn != null) {
            request.withAppArn(appArn);
        }

        return api.scheduleRun(request);
    }

    public GetRunResult describeRun(String runArn) {
        return api.getRun(new GetRunRequest()
                .withArn(runArn));
    }

    /**
     * Gets a local File instance of a glob file pattern, pulling it from a secondary node if necessary.
     *
     * @param pattern Glob pattern to find artifacts
     * @return File found by the glob.
     */
    private File getArtifactFile(String pattern) throws AWSDeviceFarmException {
        if (pattern == null || pattern.isEmpty()) {
            throw new AWSDeviceFarmException("Must have a non-empty pattern.");
        }

        try {
            // Find glob matches.
            FilePath[] matches = workspace.list(pattern);
            if (matches == null || matches.length == 0) {
                throw new AWSDeviceFarmException(String.format("No Artifacts found using pattern '%s'", pattern));
            } else if (matches.length != 1) {
                StringBuilder msg = new StringBuilder(String.format("More than one match found for pattern '%s':", pattern));
                for (FilePath fp : matches) {
                    msg.append(String.format("%n\t%s", fp.getRemote()));
                }
                throw new AWSDeviceFarmException(msg.toString());
            }

            // Now that we know it's one and only one, take it.
            FilePath artifact = matches[0];
            writeToLog(String.format("Archiving artifact '%s'", artifact.getName()));


            // Copy file (primary or secondary) to the build artifact directory on the primary node.
            FilePath localArtifact = new FilePath(artifactsDir, artifact.getName());
            artifact.copyTo(localArtifact);
            return new File(localArtifact.getRemote());
        } catch (IOException e) {
            throw new AWSDeviceFarmException(String.format("Unable to find artifact %s", e.toString()));
        } catch (InterruptedException e) {
            throw new AWSDeviceFarmException(String.format("Unable to find artifact %s", e.toString()));
        }
    }

    public ListArtifactsResult listArtifacts(String runArn, ArtifactCategory category) {
        ListArtifactsRequest request = new ListArtifactsRequest()
                .withArn(runArn)
                .withType(category);

        return api.listArtifacts(request);
    }

    public ListJobsResult listJobs(String runArn) {
        ListJobsRequest request = new ListJobsRequest()
                .withArn(runArn);

        return api.listJobs(request);
    }

    public ListSuitesResult listSuites(String jobArn) {
        ListSuitesRequest request = new ListSuitesRequest()
                .withArn(jobArn);

        return api.listSuites(request);
    }

    public ListTestsResult listTests(String suiteArn) {
        ListTestsRequest request = new ListTestsRequest().withArn(suiteArn);

        return api.listTests(request);
    }

    public int getUnmeteredDevices(String os) {
        AccountSettings accountSettings = getAccountSettings();
        if (accountSettings == null) {
            return 0;
        } else if (os.equalsIgnoreCase("ANDROID")) {
            return getAccountSettings().getUnmeteredDevices().get("ANDROID");
        } else if (os.equalsIgnoreCase("IOS")) {
            return getAccountSettings().getUnmeteredDevices().get("IOS");
        } else {
            return 0;
        }
    }

	/*
	 * Call this method for web apps. IF user has unmetered android or IOS, he
	 * should be allowed to run web tests
	 */
	public int getUnmeteredDevicesForWeb() {
		AccountSettings accountSettings = getAccountSettings();
		if (accountSettings == null) {
			return 0;
		}
		int androidCount = getAccountSettings().getUnmeteredDevices().get("ANDROID");
		int iosCount = getAccountSettings().getUnmeteredDevices().get("IOS");
		return Math.max(androidCount, iosCount);
	}

    public String getOs(String appArtifact) throws AWSDeviceFarmException {
        if (appArtifact.toLowerCase().endsWith("apk")) {
            return "Android";
        } else if (appArtifact.toLowerCase().endsWith("ipa")) {
            return "IOS";
        } else {
            throw new AWSDeviceFarmException(String.format("Unknown app artifact to upload: %s", appArtifact));
        }
    }

    public AccountSettings getAccountSettings() {
        try {
            GetAccountSettingsRequest request = new GetAccountSettingsRequest();
            return api.getAccountSettings(request).getAccountSettings();
        } catch (NotFoundException e) {
            return null;
        }
    }

    //// Helper Methods

    /**
     * Stupid log helper.
     *
     * @param message The message to log.
     */
    private void writeToLog(String message) {
        if (log != null) {
            log.println(String.format("[AWSDeviceFarm] %s", message));
        }
    }
}
