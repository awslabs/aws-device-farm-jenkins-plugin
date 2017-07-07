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

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.devicefarm.model.Artifact;
import com.amazonaws.services.devicefarm.model.ArtifactCategory;
import com.amazonaws.services.devicefarm.model.BillingMethod;
import com.amazonaws.services.devicefarm.model.DevicePool;
import com.amazonaws.services.devicefarm.model.Job;
import com.amazonaws.services.devicefarm.model.ListArtifactsResult;
import com.amazonaws.services.devicefarm.model.ListJobsResult;
import com.amazonaws.services.devicefarm.model.ListSuitesResult;
import com.amazonaws.services.devicefarm.model.ListTestsResult;
import com.amazonaws.services.devicefarm.model.Location;
import com.amazonaws.services.devicefarm.model.Project;
import com.amazonaws.services.devicefarm.model.Radios;
import com.amazonaws.services.devicefarm.model.ScheduleRunConfiguration;
import com.amazonaws.services.devicefarm.model.ScheduleRunResult;
import com.amazonaws.services.devicefarm.model.ScheduleRunTest;
import com.amazonaws.services.devicefarm.model.Suite;
import com.amazonaws.services.devicefarm.model.Test;
import com.amazonaws.services.devicefarm.model.TestType;
import com.amazonaws.services.devicefarm.model.Upload;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.IOUtils;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.awsdevicefarm.test.AppiumJavaJUnitTest;
import org.jenkinsci.plugins.awsdevicefarm.test.AppiumJavaTestNGTest;
import org.jenkinsci.plugins.awsdevicefarm.test.AppiumPythonTest;
import org.jenkinsci.plugins.awsdevicefarm.test.AppiumWebJavaJUnitTest;
import org.jenkinsci.plugins.awsdevicefarm.test.AppiumWebJavaTestNGTest;
import org.jenkinsci.plugins.awsdevicefarm.test.AppiumWebPythonTest;
import org.jenkinsci.plugins.awsdevicefarm.test.CalabashTest;
import org.jenkinsci.plugins.awsdevicefarm.test.InstrumentationTest;
import org.jenkinsci.plugins.awsdevicefarm.test.UIAutomationTest;
import org.jenkinsci.plugins.awsdevicefarm.test.UIAutomatorTest;
import org.jenkinsci.plugins.awsdevicefarm.test.XCTestTest;
import org.jenkinsci.plugins.awsdevicefarm.test.XCTestUITest;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Post-build step for running tests on AWS Device Farm.
 */
public class AWSDeviceFarmRecorder extends Recorder {

    ////// All of these fields have to be public so that they can be read (via reflection) by Jenkins. Probably not the
    ////// greatest thing in the world given that this is *allegedly* supposed to be an immutable class.

    //// global.jelly fields
    public String projectName;
    public String devicePoolName;
    public String appArtifact;
    public String runName;

    //// config.jelly fields
    // Radio Button Selection
    public String testToRun;
    public Boolean storeResults;
    public Boolean isRunUnmetered;

    // Built-in Fuzz
    public String eventCount;
    public String eventThrottle;
    public String seed;

    // Built-in Explorer
    public String username;
    public String password;

    // Appium Java JUnit
    public String appiumJavaJUnitTest;

    // Appium Java TestNG
    public String appiumJavaTestNGTest;

    // Appium Python
    public String appiumPythonTest;

    // Calabash
    public String calabashFeatures;
    public String calabashTags;
    public String calabashProfile;

    // Instrumentation
    public String junitArtifact;
    public String junitFilter;

    // UIAutomator
    public String uiautomatorArtifact;
    public String uiautomatorFilter;

    // uiautomation
    public String uiautomationArtifact;

    // XCTest
    public String xctestArtifact;
    public String xctestFilter;

    // XCTest UI
    public String xctestUiArtifact;
    public String xctestUiFilter;

    // Fields not populated by the JSON binder.
    public PrintStream log;

    // ignore device farm run errors
    public Boolean ignoreRunError;

    // web app
    public Boolean ifWebApp;

    // The Appium version for the Appium Tests.
    public String appiumVersionJunit;
    public String appiumVersionPython;
    public String appiumVersionTestng;

    private static final String APPIUM_VERSION_1_6_3 = "1.6.3";
    private static final String APPIUM_VERSION_1_6_5 = "1.6.5";
    private static final String APPIUM_VERSION_1_4_16 = "1.4.16";

    // Device States Specification
    public Boolean extraData;
    public String extraDataArtifact;

    public Boolean deviceLocation;
    public Double deviceLatitude;
    public Double deviceLongitude;

    public Boolean radioDetails;
    public Boolean ifWifi;
    public Boolean ifGPS;
    public Boolean ifNfc;
    public Boolean ifBluetooth;

    public Integer jobTimeoutMinutes;

    private static final int MIN_EXECUTION_TIMEOUT = 5;

    private static final int MAX_EXECUTION_TIMEOUT = 600;

    //E xecution Configuration
    public Boolean ifVideoRecording;
    public Boolean ifAppPerformanceMonitoring;

    /**
     * The Device Farm recorder class for running post-build steps on Jenkins.
     *
     * @param projectName                   The name of the Device Farm project.
     * @param devicePoolName                The name of the Device Farm device pool.
     * @param appArtifact                   The path to the app to be tested.
     * @param runName                       The name of the run.
     * @param testToRun                     The type of test to be run.
     * @param storeResults                  Download the results to a local archive.
     * @param eventCount                    The number of fuzz events to run.
     * @param eventThrottle                 The the fuzz event throttle count.
     * @param seed                          The initial seed of fuzz events.
     * @param username                      Username to use if explorer encounters a login form.
     * @param password                      Password to use if explorer encounters a login form.
     * @param appiumJavaJUnitTest           The path to the Appium Java Junit tests.
     * @param appiumJavaTestNGTest          The path to the Appium Java tests.
     * @param appiumPythonTest              The path to the Appium python tests.
     * @param calabashFeatures              The path to the Calabash tests to be run.
     * @param calabashTags                  Calabash tags to attach to the test.
     * @param calabashProfile               Calabash Profile to attach to the test.
     * @param junitArtifact                 The path to the Instrumentation JUnit tests.
     * @param junitFilter                   The filter to apply to the Instrumentation JUnit tests.
     * @param uiautomatorArtifact           The path to the UI Automator tests to be run.
     * @param uiautomatorFilter             The filter to apply to the UI Automator tests.
     * @param uiautomationArtifact          The path to the UI Automation tests to be run.
     * @param xctestArtifact                The path to the XCTest tests to be run.
     * @param xctestFilter                  The filter to apply to the XCTest tests.
     * @param xctestUiArtifact              The path to the XCTest UI tests to be run.
     * @param xctestUiFilter                The filter to apply to the XCTest UI tests.
     * @param appiumVersionJunit            The version of the Appium used for Appium Junit tests.
     * @param appiumVersionPython           The version of the Appium used for Appium Python tests.
     * @param appiumVersionTestng           The version of the Appium used for Appoum Testing tests.
     * @param ifWebApp                      Whether it is a web app.
     * @param extraData                     Whether it has extra data.
     * @param extraDataArtifact             The path to the extra data.
     * @param deviceLocation                True when device location is specified.
     * @param deviceLatitude                The specified device latitude.
     * @param deviceLongitude               The specified device longitude.
     * @param radioDetails                  Whether the radio details would be specified.
     * @param ifWifi
     * @param ifNfc
     * @param ifGPS
     * @param ifBluetooth
     * @param jobTimeoutMinutes            The max execution time per job.
     * @param ifAppPerformanceMonitoring   Whether the performance would be monitored.
     * @param ifVideoRecording             Whether the video would be recorded.
     *
     */
    @DataBoundConstructor
    @SuppressWarnings("unused")
    public AWSDeviceFarmRecorder(String projectName,
                                 String devicePoolName,
                                 String appArtifact,
                                 String runName,
                                 String testToRun,
                                 Boolean storeResults,
                                 Boolean isRunUnmetered,
                                 String eventCount,
                                 String eventThrottle,
                                 String seed,
                                 String username,
                                 String password,
                                 String appiumJavaJUnitTest,
                                 String appiumJavaTestNGTest,
                                 String appiumPythonTest,
                                 String calabashFeatures,
                                 String calabashTags,
                                 String calabashProfile,
                                 String junitArtifact,
                                 String junitFilter,
                                 String uiautomatorArtifact,
                                 String uiautomatorFilter,
                                 String uiautomationArtifact,
                                 String xctestArtifact,
                                 String xctestFilter,
                                 String xctestUiArtifact,
                                 String xctestUiFilter,
                                 String appiumVersionJunit,
                                 String appiumVersionPython,
                                 String appiumVersionTestng,
                                 Boolean ifWebApp,
                                 Boolean extraData,
                                 String extraDataArtifact,
                                 Boolean deviceLocation,
                                 Double deviceLatitude,
                                 Double deviceLongitude,
                                 Boolean radioDetails,
                                 Boolean ifBluetooth,
                                 Boolean ifWifi,
                                 Boolean ifGPS,
                                 Boolean ifNfc,
                                 Integer jobTimeoutMinutes,
                                 Boolean ifVideoRecording,
                                 Boolean ifAppPerformanceMonitoring,
                                 Boolean ignoreRunError ) {
        this.projectName = projectName;
        this.devicePoolName = devicePoolName;
        this.appArtifact = appArtifact;
        this.runName = runName;
        this.storeResults = storeResults;
        this.isRunUnmetered = isRunUnmetered;
        this.eventCount = eventCount;
        this.eventThrottle = eventThrottle;
        this.seed = seed;
        this.username = username;
        this.password = password;
        this.appiumJavaJUnitTest = appiumJavaJUnitTest;
        this.appiumJavaTestNGTest = appiumJavaTestNGTest;
        this.appiumPythonTest = appiumPythonTest;
        this.calabashFeatures = calabashFeatures;
        this.calabashTags = calabashTags;
        this.calabashProfile = calabashProfile;
        this.junitArtifact = junitArtifact;
        this.junitFilter = junitFilter;
        this.uiautomatorArtifact = uiautomatorArtifact;
        this.uiautomatorFilter = uiautomatorFilter;
        this.uiautomationArtifact = uiautomationArtifact;
        this.xctestArtifact = xctestArtifact;
        this.xctestFilter = xctestFilter;
        this.xctestUiArtifact = xctestUiArtifact;
        this.xctestUiFilter = xctestUiFilter;
        this.ignoreRunError = ignoreRunError;
        this.appiumVersionJunit = appiumVersionJunit;
        this.appiumVersionPython = appiumVersionPython;
        this.appiumVersionTestng = appiumVersionTestng;
        this.extraData = extraData;
        this.extraDataArtifact = extraDataArtifact;
        this.deviceLocation = deviceLocation;
        this.deviceLatitude = deviceLatitude;
        this.deviceLongitude = deviceLongitude;
        this.radioDetails = radioDetails;
        this.ifWifi = ifWifi;
        this.ifGPS = ifGPS;
        this.ifBluetooth = ifBluetooth;
        this.ifNfc = ifNfc;
        this.jobTimeoutMinutes = jobTimeoutMinutes;
        this.ifVideoRecording = ifVideoRecording;
        this.ifAppPerformanceMonitoring = ifAppPerformanceMonitoring;
        this.ifWebApp = ifWebApp;
        this.testToRun = transformTestToRunForWebApp(testToRun);

        // This is a hack because I have to get the service icon locally, but it's copy-righted. So I pull it when I need it.
        Path pluginIconPath = Paths.get(System.getenv("HOME"), "plugins", "aws-device-farm", "service-icon.svg").toAbsolutePath();
        File pluginIcon = new File(pluginIconPath.toString());
        if (!(pluginIcon.exists() && !pluginIcon.isDirectory())) {
            System.out.println("Downloading service icon!");
            try {
                FileUtils.copyURLToFile(new URL("http://g-ecx.images-amazon.com/images/G/01/aws-device-farm/service-icon.svg"), pluginIcon);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Convert the test type String to TestType.
     *
     * @param testTypeName The String representation of the test type.
     * @return The TestType.
     */
    private TestType stringToTestType(String testTypeName) {
        return TestType.valueOf(testTypeName.toUpperCase());
    }

    /**
     * Transform the test type parameter as the requirement for AWS devicefarm API for appium tests of web apps.
     *
     * @param testToRun The String representation of the test type.
     * @return The String should bu input as the parameter of the devicefarm API.
     */
    public String transformTestToRunForWebApp(String testToRun) {
        if (ifWebApp) {
            if (testToRun.equalsIgnoreCase("APPIUM_PYTHON")) return"APPIUM_WEB_PYTHON";
            else if (testToRun.equalsIgnoreCase("APPIUM_JAVA_JUNIT")) return"APPIUM_WEB_JAVA_JUNIT";
            else if (testToRun.equalsIgnoreCase("APPIUM_JAVA_TESTNG")) return"APPIUM_WEB_JAVA_TESTNG";
        }
        return testToRun;
    }

    /**
     * Test if the test type names match (for marking the radio button).
     *
     * @param testTypeName The String representation of the test type.
     * @return Whether or not the test type string matches.
     */
    public String isTestType(String testTypeName) {
        testTypeName = transformTestToRunForWebApp(testTypeName);
        return this.testToRun.equalsIgnoreCase(testTypeName) ? "true" : "";
    }

    /**
     * Perform the post-build test action.
     *
     * @param build    The build to follow.
     * @param launcher The launcher.
     * @param listener The build launcher.
     * @return Whether or not the post-build action succeeded.
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        // Check if the build result set from a previous build step.
        // A null build result indicates that the build is still ongoing and we're
        // likely being run as a build step by the "Any Build Step Plugin".
        Result buildResult = build.getResult();
        if (buildResult != null && buildResult.isWorseOrEqualTo(Result.FAILURE)) {
            return false;
        }

        EnvVars env = build.getEnvironment(listener);
        Map<String, String> parameters = build.getBuildVariables();

        log = listener.getLogger();

        // Artifacts location for this build on master.
        FilePath artifactsDir = new FilePath(build.getArtifactsDir());

        // Workspace (potentially remote if using slave).
        FilePath workspace = build.getWorkspace();

        // Validate user selection & input values.
        boolean isValid = validateConfiguration() && validateTestConfiguration();
        if (!isValid) {
            writeToLog("Invalid configuration.");
            return false;
        }

        // Create & configure the AWSDeviceFarm client.
        AWSDeviceFarm adf = getAWSDeviceFarm()
                .withLogger(listener.getLogger())
                .withWorkspace(workspace)
                .withArtifactsDir(artifactsDir)
                .withEnv(env);

        try {
            // Accept 'ADF_PROJECT' build parameter as an overload from job configuration.
            String projectNameParameter = parameters.get("AWSDEVICEFARM_PROJECT");
            if (projectNameParameter != null && !projectNameParameter.isEmpty()) {
                writeToLog(String.format("Using overloaded project '%s' from build parameters", projectNameParameter));
                projectName = projectNameParameter;
            }

            // check for Unmetered Devices on Account
            if (isRunUnmetered != null && isRunUnmetered) {
                String os = adf.getOs(appArtifact);
                int unmeteredDeviceCount = adf.getUnmeteredDevices(os);
                if (unmeteredDeviceCount <= 0) {
                    throw new AWSDeviceFarmException(String.format("Your account does not have unmetered %s device slots. Please change "
                            + "your build settings to run on metered devices.", os));
                }
            }

            // Get AWS Device Farm project from user provided name.
            writeToLog(String.format("Using Project '%s'", projectName));
            Project project = adf.getProject(projectName);

            // Accept 'ADF_DEVICE_POOL' build parameter as an overload from job configuration.
            String devicePoolParameter = parameters.get("AWSDEVICEFARM_DEVICE_POOL");
            if (devicePoolParameter != null) {
                writeToLog(String.format("Using overloaded device pool '%s' from build parameters", devicePoolParameter));
                devicePoolName = devicePoolParameter;
            }

            // Get AWS Device Farm device pool from user provided name.
            writeToLog(String.format("Using DevicePool '%s'", devicePoolName));
            DevicePool devicePool = adf.getDevicePool(project, devicePoolName);

            // Upload app.
            String appArn = null;
            if (ifWebApp != null && ifWebApp){
                writeToLog("Tesing a Web App.");

            }
            else {
                writeToLog(String.format("Using App '%s'", env.expand(appArtifact)));
                Upload appUpload = adf.uploadApp(project, appArtifact);
                appArn = appUpload.getArn();
            }

            String deviceFarmRunName = null;
            if (StringUtils.isBlank(runName)) {
                deviceFarmRunName = String.format("%s", env.get("BUILD_TAG"));
            } else {
                deviceFarmRunName = String.format("%s", env.expand(runName));
            }

            // Upload test content.
            writeToLog("Getting test to schedule.");
            ScheduleRunTest testToSchedule = getScheduleRunTest(env, adf, project);

            if (ifVideoRecording != null && !ifVideoRecording) {
                testToSchedule.addParametersEntry("video_recording", "false");
            }
            if (ifAppPerformanceMonitoring != null && !ifAppPerformanceMonitoring) {
                testToSchedule.addParametersEntry("app_performance_monitoring", "false");
            }


            // State the Appium Version.
            if (testToRun.equalsIgnoreCase("APPIUM_JAVA_JUNIT")) writeToLog(String.format("Using appium version: %s", appiumVersionJunit));
            else if (testToRun.equalsIgnoreCase("APPIUM_WEB_JAVA_JUNIT")) writeToLog(String.format("Using appium version: %s", appiumVersionJunit));
            else if (testToRun.equalsIgnoreCase("APPIUM_JAVA_TESTNG")) writeToLog(String.format("Using appium version: %s", appiumVersionTestng));
            else if (testToRun.equalsIgnoreCase("APPIUM_WEB_JAVA_TESTNG")) writeToLog(String.format("Using appium version: %s", appiumVersionTestng));
            else if (testToRun.equalsIgnoreCase("APPIUM_PYTHON")) writeToLog(String.format("Using appium version: %s", appiumVersionPython));
            else if (testToRun.equalsIgnoreCase("APPIUM_WEB_PYTHON")) writeToLog(String.format("Using appium version: %s", appiumVersionPython));


            // Upload the extra data.
            String extraDataArn = null;
            if (extraData != null && extraData) {
                writeToLog(String.format("Using Extra Data '%s'", env.expand(extraDataArtifact)));
                Upload extraDataUpload = adf.uploadExtraData(project, extraDataArtifact);
                extraDataArn = extraDataUpload.getArn();
            }

            // Schedule test run.
            TestType testType = TestType.fromValue(testToSchedule.getType());
            writeToLog(String.format("Scheduling '%s' run '%s'", testType, deviceFarmRunName));

            ScheduleRunConfiguration configuration = getScheduleRunConfiguration(isRunUnmetered, deviceLocation, radioDetails);
            configuration.setExtraDataPackageArn(extraDataArn);

            ScheduleRunResult run = adf.scheduleRun(project.getArn(), deviceFarmRunName, appArn, devicePool.getArn(), testToSchedule, jobTimeoutMinutes, configuration);

            String runArn = run.getRun().getArn();
            try {
                writeToLog(String.format("View the %s run in the AWS Device Farm Console: %s", testType, AWSDeviceFarmUtils.getRunUrlFromArn(runArn)));
            } catch (ArrayIndexOutOfBoundsException e) {
                writeToLog(String.format("Could not parse project ID and run ID from run ARN: %s", runArn));
            }

            // Attach AWS Device Farm action to poll periodically and update results UI.
            AWSDeviceFarmTestResultAction action = new AWSDeviceFarmTestResultAction(build, null, log);
            build.addAction(action);

            // Wait for test result to complete will updating status periodically.
            writeToLog("Waiting for test run to complete.");
            action.waitForRunCompletion(adf, run);
            writeToLog("Test run is complete.");


            // Download results archive and store it.
            if (storeResults) {
                // Create results storage directory which will contain the unzip logs/screenshots pulled from AWS Device Farm.
                FilePath resultsDir = new FilePath(artifactsDir, "AWS Device Farm Results");
                resultsDir.mkdirs();
                writeToLog(String.format("Storing AWS Device Farm results in directory %s", resultsDir));

                Map<String, FilePath> jobs = getJobs(adf, run, resultsDir);
                Map<String, FilePath> suites = getSuites(adf, run, jobs);
                Map<String, FilePath> tests = getTests(adf, run, suites);

                writeToLog("Downloading AWS Device Farm results archive...");
                // Iterating over all values in the Enum.
                for (ArtifactCategory category : new ArrayList<ArtifactCategory>(Arrays.asList(ArtifactCategory.values()))) {
                    ListArtifactsResult result = adf.listArtifacts(run.getRun().getArn(), category);
                    for (Artifact artifact : result.getArtifacts()) {
                        String arn = artifact.getArn().split(":")[6];
                        String testArn = arn.substring(0, arn.lastIndexOf("/"));
                        String id = arn.substring(arn.lastIndexOf("/") + 1);
                        String extension = artifact.getExtension().replaceFirst("^\\.", "");
                        FilePath artifactFilePath = new FilePath(tests.get(testArn), String.format("%s-%s.%s", artifact.getName(), id, extension));
                        URL url = new URL(artifact.getUrl());
                        artifactFilePath.write().write(IOUtils.toByteArray(url.openStream()));
                    }
                }
                writeToLog(String.format("Results archive saved in %s", artifactsDir.getName()));
            }

            // Set Jenkins build result based on AWS Device Farm test result.
            build.setResult(action.getBuildResult(ignoreRunError));
        } catch (AWSDeviceFarmException e) {
            writeToLog(e.getMessage());
            return false;
        }

        return true;
    }

    private Location getScheduleRunConfigurationLocation(Boolean deviceLocation) {
        Location location = new Location();
        if (deviceLocation != null && deviceLocation){
            location.setLatitude(deviceLatitude);
            location.setLongitude(deviceLongitude);
        }
        else {
            location.setLatitude(47.6204);
            location.setLongitude(-122.3491);
        }
        return location;
    }

    private Radios getScheduleRunConfigurationRadio(Boolean radioDetails) {
        Radios radio = new Radios();
        if (radioDetails != null && radioDetails){
            radio.setBluetooth(ifBluetooth);
            radio.setGps(ifGPS);
            radio.setNfc(ifNfc);
            radio.setWifi(ifWifi);
        }
        else {
            radio.setBluetooth(true);
            radio.setGps(true);
            radio.setNfc(true);
            radio.setWifi(true);
        }
        return radio;

    }

    private ScheduleRunConfiguration getScheduleRunConfiguration(Boolean isRunUnmetered, Boolean deviceLocation, Boolean radioDetails) {
        ScheduleRunConfiguration configuration = new ScheduleRunConfiguration();
        if (isRunUnmetered != null && isRunUnmetered) {
            configuration.setBillingMethod(BillingMethod.UNMETERED);
        } else {
            configuration.setBillingMethod(BillingMethod.METERED);
        }

        // set a bunch of other default values as Device Farm expect these
        configuration.setAuxiliaryApps(new ArrayList<String>());
        configuration.setLocale("en_US");

        Location location = getScheduleRunConfigurationLocation(deviceLocation);
        configuration.setLocation(location);

        Radios radio = getScheduleRunConfigurationRadio(radioDetails);
        configuration.setRadios(radio);

        return configuration;
    }

    private Map<String, FilePath> getSuites(AWSDeviceFarm adf, ScheduleRunResult run, Map<String, FilePath> jobs) throws IOException, InterruptedException {
        Map<String, FilePath> suites = new HashMap<String, FilePath>();
        String runArn = run.getRun().getArn();
        String components[] = runArn.split(":");
        // constructing job ARN for each job using the run ARN
        components[5] = "job";
        for (Map.Entry<String, FilePath> jobEntry : jobs.entrySet()) {
            String jobArn = jobEntry.getKey();
            components[6] = jobArn;
            String fullJobArn = StringUtils.join(components, ":");
            ListSuitesResult result = adf.listSuites(fullJobArn);
            for (Suite suite : result.getSuites()) {
                String arn = suite.getArn().split(":")[6];
                suites.put(arn, new FilePath(jobs.get(jobArn), suite.getName()));
                suites.get(arn).mkdirs();
            }
        }
        return suites;
    }

    private Map<String, FilePath> getTests(AWSDeviceFarm adf, ScheduleRunResult run, Map<String, FilePath> suites) throws IOException, InterruptedException {
        Map<String, FilePath> tests = new HashMap<String, FilePath>();
        String runArn = run.getRun().getArn();
        String components[] = runArn.split(":");
        // constructing suite ARN for each job using the run ARN
        components[5] = "suite";
        for (Map.Entry<String, FilePath> suiteEntry : suites.entrySet()) {
            String suiteArn = suiteEntry.getKey();
            components[6] = suiteArn;
            String fullsuiteArn = StringUtils.join(components, ":");
            ListTestsResult result = adf.listTests(fullsuiteArn);
            for (Test test : result.getTests()) {
                String arn = test.getArn().split(":")[6];
                tests.put(arn, new FilePath(suites.get(suiteArn), test.getName()));
                tests.get(arn).mkdirs();
            }
        }
        return tests;
    }

    private Map<String, FilePath> getJobs(AWSDeviceFarm adf, ScheduleRunResult run, FilePath resultsDir) throws IOException, InterruptedException {
        Map<String, FilePath> jobs = new HashMap<String, FilePath>();
        ListJobsResult result = adf.listJobs(run.getRun().getArn());
        for (Job job : result.getJobs()) {
            String arn = job.getArn().split(":")[6];
            String jobId = arn.substring(arn.lastIndexOf("/") + 1);
            // Two jobs can have same name. Appending Os version information to job name
            String osVersion = null;
            if (job.getDevice() != null) {
                osVersion = job.getDevice().getOs();
            }
            jobs.put(arn, new FilePath(resultsDir, job.getName() + "-" + (osVersion != null ? osVersion : jobId)));
            jobs.get(arn).mkdirs();
        }
        return jobs;
    }

    /**
     * Schedule a test run.
     *
     * @param env     The Jenkins environment to use.
     * @param adf     The AWS Device Farm.
     * @param project The project.
     * @return A list of all of the run tests scheduled.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    private ScheduleRunTest getScheduleRunTest(EnvVars env, AWSDeviceFarm adf, Project project) throws InterruptedException, IOException, AWSDeviceFarmException {
        ScheduleRunTest testToSchedule = null;
        TestType testType = stringToTestType(testToRun);

        switch (testType) {
            case BUILTIN_FUZZ: {
                Map<String, String> parameters = new HashMap<String, String>();

                if (eventCount != null && !eventCount.isEmpty()) {
                    parameters.put("event_count", eventCount);
                }

                if (eventThrottle != null && !eventThrottle.isEmpty()) {
                    parameters.put("throttle", eventThrottle);
                }

                if (seed != null && !seed.isEmpty()) {
                    parameters.put("seed", seed);
                }

                testToSchedule = new ScheduleRunTest()
                        .withType(testType)
                        .withParameters(parameters);
                break;
            }

            case BUILTIN_EXPLORER: {
                Map<String, String> parameters = new HashMap<String, String>();

                if (username != null && !username.isEmpty()) {
                    parameters.put("username", username);
                }

                if (password != null && !password.isEmpty()) {
                    parameters.put("password", password);
                }

                testToSchedule = new ScheduleRunTest()
                        .withType(testType)
                        .withParameters(parameters);
                break;
            }

            case APPIUM_JAVA_JUNIT: {
                AppiumJavaJUnitTest test = new AppiumJavaJUnitTest.Builder()
                        .withTests(env.expand(appiumJavaJUnitTest))
                        .build();

                Upload upload = adf.uploadTest(project, test);

                testToSchedule = new ScheduleRunTest()
                        .withType(testType)
                        .withTestPackageArn(upload.getArn());

                testToSchedule.addParametersEntry("appium_version", appiumVersionJunit);
                break;
            }

            case APPIUM_JAVA_TESTNG: {
                AppiumJavaTestNGTest test = new AppiumJavaTestNGTest.Builder()
                        .withTests(env.expand(appiumJavaTestNGTest))
                        .build();

                Upload upload = adf.uploadTest(project, test);

                testToSchedule = new ScheduleRunTest()
                        .withType(testType)
                        .withTestPackageArn(upload.getArn());

                testToSchedule.addParametersEntry("appium_version", appiumVersionTestng);
                break;
            }

            case APPIUM_PYTHON: {
                AppiumPythonTest test = new AppiumPythonTest.Builder()
                        .withTests(env.expand(appiumPythonTest))
                        .build();

                Upload upload = adf.uploadTest(project, test);

                testToSchedule = new ScheduleRunTest()
                        .withType(testType)
                        .withTestPackageArn(upload.getArn());

                testToSchedule.addParametersEntry("appium_version", appiumVersionPython);
                break;
            }

            case APPIUM_WEB_JAVA_JUNIT: {
                AppiumWebJavaJUnitTest test = new AppiumWebJavaJUnitTest.Builder()
                        .withTests(env.expand(appiumJavaJUnitTest))
                        .build();

                Upload upload = adf.uploadTest(project, test);

                testToSchedule = new ScheduleRunTest()
                        .withType(testType)
                        .withTestPackageArn(upload.getArn());

                testToSchedule.addParametersEntry("appium_version", appiumVersionJunit);
                break;
            }

            case APPIUM_WEB_JAVA_TESTNG: {
                AppiumWebJavaTestNGTest test = new AppiumWebJavaTestNGTest.Builder()
                        .withTests(env.expand(appiumJavaTestNGTest))
                        .build();

                Upload upload = adf.uploadTest(project, test);

                testToSchedule = new ScheduleRunTest()
                        .withType(testType)
                        .withTestPackageArn(upload.getArn());

                testToSchedule.addParametersEntry("appium_version", appiumVersionTestng);
                break;
            }

            case APPIUM_WEB_PYTHON: {
                AppiumWebPythonTest test = new AppiumWebPythonTest.Builder()
                        .withTests(env.expand(appiumPythonTest))
                        .build();

                Upload upload = adf.uploadTest(project, test);

                testToSchedule = new ScheduleRunTest()
                        .withType(testType)
                        .withTestPackageArn(upload.getArn());

                testToSchedule.addParametersEntry("appium_version", appiumVersionPython);
                break;
            }

            case CALABASH: {
                CalabashTest test = new CalabashTest.Builder()
                        .withFeatures(env.expand(calabashFeatures))
                        .withTags(env.expand(calabashTags))
                        .withProfile(calabashProfile)
                        .build();

                Upload upload = adf.uploadTest(project, test);

                Map<String, String> parameters = new HashMap<String, String>();
                if (test.getTags() != null && !test.getTags().isEmpty()) {
                    parameters.put("tags", test.getTags());
                }
                if (test.getProfile() != null && !test.getProfile().isEmpty()) {
                    parameters.put("profile", test.getProfile());
                }
                testToSchedule = new ScheduleRunTest()
                        .withType(testType)
                        .withTestPackageArn(upload.getArn())
                        .withParameters(parameters);
                break;
            }

            case INSTRUMENTATION: {
                InstrumentationTest test = new InstrumentationTest.Builder()
                        .withArtifact(env.expand(junitArtifact))
                        .withFilter(junitFilter)
                        .build();

                Upload upload = adf.uploadTest(project, test);

                testToSchedule = new ScheduleRunTest()
                        .withType(testType)
                        .withTestPackageArn(upload.getArn())
                        .withParameters(new HashMap<String, String>())
                        .withFilter(test.getFilter());
                break;
            }

            case UIAUTOMATOR: {
                UIAutomatorTest test = new UIAutomatorTest.Builder()
                        .withTests(env.expand(uiautomatorArtifact))
                        .withFilter(uiautomatorFilter)
                        .build();

                Upload upload = adf.uploadTest(project, test);

                testToSchedule = new ScheduleRunTest()
                        .withType(testType)
                        .withTestPackageArn(upload.getArn())
                        .withParameters(new HashMap<String, String>())
                        .withFilter(test.getFilter());

                break;
            }

            case UIAUTOMATION: {
                UIAutomationTest test = new UIAutomationTest.Builder()
                        .withTests(uiautomationArtifact)
                        .build();

                Upload upload = adf.uploadTest(project, test);

                testToSchedule = new ScheduleRunTest()
                        .withType(testType)
                        .withTestPackageArn(upload.getArn());
                break;
            }

            case XCTEST: {
                XCTestTest test = new XCTestTest.Builder()
                        .withTests(xctestArtifact)
                        .withFilter(xctestFilter)
                        .build();

                Upload upload = adf.uploadTest(project, test);

                testToSchedule = new ScheduleRunTest()
                        .withType(testType)
                        .withFilter(test.getFilter())
                        .withTestPackageArn(upload.getArn());
                break;
            }

            case XCTEST_UI: {
                XCTestUITest test = new XCTestUITest.Builder()
                        .withTests(xctestUiArtifact)
                        .withFilter(xctestUiFilter)
                        .build();

                Upload upload = adf.uploadTest(project, test);

                testToSchedule = new ScheduleRunTest()
                        .withType(testType)
                        .withFilter(test.getFilter())
                        .withTestPackageArn(upload.getArn());
                break;
            }

        }

        return testToSchedule;
    }

    /**
     * Validate top level configuration values.
     *
     * @return Whether or not the configuration is valid.
     */
    private boolean validateConfiguration() {
        String roleArn = getRoleArn();
        String akid = getAkid();
        String skid = getSkid();

        // [Required]: Auth Credentials
        if ((roleArn == null || roleArn.isEmpty()) && (akid == null || akid.isEmpty() || skid == null || skid.isEmpty())) {
            writeToLog("Either IAM Role ARN or AKID/SKID must be set.");
            return false;
        }

        // [Required]: Project
        if (projectName == null || projectName.isEmpty()) {
            writeToLog("Project must be set.");
            return false;
        }
        // [Required]: DevicePool
        if (devicePoolName == null || devicePoolName.isEmpty()) {
            writeToLog("DevicePool must be set.");
            return false;
        }
        // [Required]: App Artifact
        if (!ifWebApp && (appArtifact == null || appArtifact.isEmpty())) {
            writeToLog("Application Artifact must be set.");
            return false;
        }
        // [Required]: At least one test.
        if (testToRun == null || stringToTestType(testToRun) == null) {
            writeToLog("A test type must be set.");
            return false;
        }
        return true;
    }

    /**
     * Validate user selected test type and additional configuration values.
     *
     * @return Whether or not the test configuration is valid.
     */
    private boolean validateTestConfiguration() {
        TestType testType = stringToTestType(testToRun);

        switch (testType) {
            case BUILTIN_FUZZ: {
                // [Optional]: EventCount (int)
                if (eventCount != null && !eventCount.isEmpty()) {
                    if (!eventCount.matches("^\\d+$")) {
                        writeToLog("EventCount must be a number.");
                        return false;
                    }
                }
                // [Optional]: Seed (int)
                if (seed != null && !seed.isEmpty()) {
                    if (!seed.matches("^\\d+$")) {
                        writeToLog("Seed must be a number.");
                        return false;
                    }
                }

                break;
            }

            case BUILTIN_EXPLORER: {
                break;
            }

            case APPIUM_JAVA_JUNIT: {
                if (appiumJavaJUnitTest == null || appiumJavaJUnitTest.isEmpty()) {
                    writeToLog("Appium Java Junit test must be set.");
                    return false;
                }

                break;
            }

            case APPIUM_JAVA_TESTNG: {
                if (appiumJavaTestNGTest == null || appiumJavaTestNGTest.isEmpty()) {
                    writeToLog("Appium Java TestNG test must be set.");
                    return false;
                }

                break;
            }

            case APPIUM_PYTHON: {
                if (appiumPythonTest == null || appiumPythonTest.isEmpty()) {
                    writeToLog("Appium Python test must be set.");
                    return false;
                }

                break;
            }

            case APPIUM_WEB_JAVA_JUNIT: {
                if (appiumJavaJUnitTest == null || appiumJavaJUnitTest.isEmpty()) {
                    writeToLog("Appium Java Junit test for the web application must be set.");
                    return false;
                }

                break;
            }

            case APPIUM_WEB_JAVA_TESTNG: {
                if (appiumJavaTestNGTest == null || appiumJavaTestNGTest.isEmpty()) {
                    writeToLog("Appium Java TestNG test for the web application must be set.");
                    return false;
                }

                break;
            }

            case APPIUM_WEB_PYTHON: {
                if (appiumPythonTest == null || appiumPythonTest.isEmpty()) {
                    writeToLog("Appium Python test for the web application must be set.");
                    return false;
                }

                break;
            }

            case CALABASH: {
                // [Required]: Features Path
                if (calabashFeatures == null || calabashFeatures.isEmpty()) {
                    writeToLog("Calabash Features must be set.");
                    return false;
                }
                // [Required]: Features.zip
                if (!calabashFeatures.endsWith(".zip")) {
                    writeToLog("Calabash content must be of type .zip");
                    return false;
                }

                break;
            }

            case INSTRUMENTATION: {
                // [Required]: Tests Artifact
                if (junitArtifact == null || junitArtifact.isEmpty()) {
                    writeToLog("JUnit tests Artifact must be set.");
                    return false;
                }

                break;
            }

            case UIAUTOMATOR: {
                if (uiautomatorArtifact == null || uiautomatorArtifact.isEmpty()) {
                    writeToLog("UI Automator tests artifact must be set.");
                    return false;
                }

                break;
            }

            case UIAUTOMATION: {
                if (uiautomationArtifact == null || uiautomationArtifact.isEmpty()) {
                    writeToLog("UI Automation tests artifact must be set.");
                    return false;
                }

                break;
            }

            case XCTEST: {
                if (xctestArtifact == null || xctestArtifact.isEmpty()) {
                    writeToLog("XC tests artifact must be set.");
                    return false;
                }

                break;
            }

            case XCTEST_UI: {
                if (xctestUiArtifact == null || xctestUiArtifact.isEmpty()) {
                    writeToLog("XCTest UI tests artifact must be set.");
                    return false;
                }

                break;
            }

            default: {
                writeToLog("Must select a test type to run.");
                return false;
            }
        }

        return true;
    }

    /**
     * Helper method for writing entries to the Jenkins log.
     *
     * @param msg The message to be written to the Jenkins log.
     */
    private void writeToLog(String msg) {
        log.println(String.format("[AWSDeviceFarm] %s", msg));
    }

    /**
     * Role ARN getter.
     *
     * @return The role ARN.
     */
    public String getRoleArn() {
        return getDescriptor().roleArn;
    }

    /**
     * Access key ID getter.
     *
     * @return The access key ID.
     */
    public String getAkid() {
        return getDescriptor().akid;
    }

    /**
     * Secret key ID getter.
     *
     * @return The secret key ID.
     */
    public String getSkid() {
        return getDescriptor().skid;
    }

    /**
     * Getter for the Device Farm API.
     *
     * @return The Device Farm API.
     */
    public AWSDeviceFarm getAWSDeviceFarm() {
        return getDescriptor().getAWSDeviceFarm();
    }

    /**
     * Getter for the Device Farm descriptor.
     *
     * @return The Device Farm descriptor.
     */
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Return collection of all Jenkins actions to be attached to this project.
     *
     * @param project The AWS Device Farm Jenkins project.
     * @return The AWS Device Farm project action collection.
     */
    @Override
    public Collection<Action> getProjectActions(AbstractProject<?, ?> project) {
        return new ArrayList<Action>(Arrays.asList(new AWSDeviceFarmProjectAction(project)));
    }

    /**
     * In a concurrent environment, this MUST run after the has completed.
     *
     * @return The BuildStepMonitor.
     */
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    /**
     * Descriptor for AWSDeviceFarmRecorder.
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public String roleArn;
        public String akid;
        public String skid;

        private List<String> projectsCache = new ArrayList<String>();
        private Map<String, List<String>> poolsCache = new HashMap<String, List<String>>();

        public DescriptorImpl() {
            load();
        }

        /**
         * roleArn Setter
         *
         * @param roleArnValue
         */
        protected final void setRoleArn(String roleArnValue){
            roleArn = roleArnValue;
        }

        /**
         * akid Setter
         *
         * @param akidValue
         */
        protected final void setAkid(String akidValue){
            akid = akidValue;
        }

        /**
         * skid Setter
         *
         * @param skidValue
         */
        protected final void setSkid(String skidValue){
            skid = skidValue;
        }

        /**
         * Return configured instance of the AWS Device Farm client.
         *
         * @return The AWS Device Farm API object.
         */
        public AWSDeviceFarm getAWSDeviceFarm() {
            AWSDeviceFarm adf;
            if (roleArn == null || roleArn.isEmpty()) {
                adf = new AWSDeviceFarm(new BasicAWSCredentials(akid, skid));
            } else {
                adf = new AWSDeviceFarm(roleArn);
            }
            return adf;
        }

        /**
         * Validate the user account role ARN.
         *
         * @param roleArn The AWS IAM role ARN.
         * @return Whether or not the form was OK.
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckRoleArn(@QueryParameter String roleArn) {
            if ((roleArn == null || roleArn.isEmpty()) && (akid == null || akid.isEmpty() || skid == null || skid.isEmpty())) {
                return FormValidation.error("Required if AKID/SKID isn't present!");
            }

            boolean isValidArn = false;
            if (roleArn != null) {
                isValidArn = roleArn.matches("^arn:aws:iam:[^:]*:[0-9]{12}:role/.*");
            }

            if (!isValidArn && (akid == null || akid.isEmpty() || skid == null || skid.isEmpty())) {
                return FormValidation.error("Doesn't look like a valid IAM Role ARN (e.g. 'arn:aws:iam::123456789012:role/jenkins')!");
            }

            if (roleArn != null && !roleArn.isEmpty() && akid != null && !akid.isEmpty() && skid != null && !skid.isEmpty()) {
                return FormValidation.error("Must specify either IAM Role ARN *OR* AKID/SKID!");
            }
            return FormValidation.ok();
        }

        /**
         * Validate the user account AKID.
         *
         * @param akid The AWS access key ID.
         * @return Whether or not the form was ok.
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckAkid(@QueryParameter String akid) {
            if ((roleArn == null || roleArn.isEmpty()) && (akid == null || akid.isEmpty())) {
                return FormValidation.error("Required if IAM Role ARN isn't present!");
            }
            if ((roleArn == null || roleArn.isEmpty()) && (akid.length() != 20)) {
                return FormValidation.error("AWS AKIDs are 20 characters long.");
            }
            if (roleArn != null && !roleArn.isEmpty() && akid != null && !akid.isEmpty() && skid != null && !skid.isEmpty()) {
                return FormValidation.error("Must specify either IAM Role ARN *OR* AKID/SKID!");
            }
            return FormValidation.ok();
        }

        /**
         * Validate the user account SKID.
         *
         * @param skid The AWS secret key ID.
         * @return Whether or not the form was ok.
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckSkid(@QueryParameter String skid) {
            if ((roleArn == null || roleArn.isEmpty()) && (skid == null || skid.isEmpty())) {
                return FormValidation.error("Required if IAM Role ARN isn't present!");
            }
            if ((roleArn == null || roleArn.isEmpty()) && (skid.length() != 40)) {
                return FormValidation.error("AWS SKIDs are 40 characters long.");
            }
            if (roleArn != null && !roleArn.isEmpty() && akid != null && !akid.isEmpty() && skid != null && !skid.isEmpty()) {
                return FormValidation.error("Must specify either IAM Role ARN *OR* AKID/SKID!");
            }
            return FormValidation.ok();
        }

        /**
         * Validate the user selected project.
         *
         * @param projectName The currently selected project name.
         * @return Whether or not the form was ok.
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckProjectName(@QueryParameter String projectName) {
            if (projectName == null || projectName.isEmpty()) {
                return FormValidation.error("Required!");
            }
            return FormValidation.ok();
        }

        /**
         * Validate the user selected device pool.
         *
         * @param devicePoolName The currently selected device pool name.
         * @return Whether or not the form was ok.
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckDevicePoolName(@QueryParameter String devicePoolName) {
            if (devicePoolName == null || devicePoolName.isEmpty()) {
                return FormValidation.error("Required!");
            }
            return FormValidation.ok();
        }

        /**
         * Validate the user entered artifact for the application.
         *
         * @param appArtifact The String of the application artifact.
         * @return Whether or not the form was ok.
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckAppArtifact(@QueryParameter  Boolean ifWebApp, @QueryParameter String appArtifact) {
            if (!ifWebApp && (appArtifact == null || appArtifact.isEmpty())) {
                return FormValidation.error("Required!");
            }
            return FormValidation.ok();
        }

        /**
         * Validate the user entered artifact for Appium Java jUnit test content.
         *
         * @param appiumJavaJUnitTest The path to the test file.
         * @return Whether or not the form was ok.
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckAppiumJavaJUnitTest(@QueryParameter String appiumJavaJUnitTest) {
            if (appiumJavaJUnitTest == null || appiumJavaJUnitTest.isEmpty()) {
                return FormValidation.error("Required!");
            }
            return FormValidation.ok();
        }

        /**
         * Validate the user entered artifact for Appium Java TestNG test content.
         *
         * @param appiumJavaTestNGTest The path to the test file.
         * @return Whether or not the form was ok.
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckAppiumJavaTestNGTest(@QueryParameter String appiumJavaTestNGTest) {
            if (appiumJavaTestNGTest == null || appiumJavaTestNGTest.isEmpty()) {
                return FormValidation.error("Required!");
            }
            return FormValidation.ok();
        }

        /**
         * Validate the user entered artifact for Appium Python test content.
         *
         * @param appiumPythonTest The path to the test file.
         * @return Whether or not the form was ok.
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckAppiumPythonTest(@QueryParameter String appiumPythonTest) {
            if (appiumPythonTest == null || appiumPythonTest.isEmpty()) {
                return FormValidation.error("Required!");
            }
            return FormValidation.ok();
        }


        /**
         * Validate the user entered file path to local Calabash features.
         *
         * @param calabashFeatures The String of the Calabash features.
         * @return Whether or not the form was ok.
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckCalabashFeatures(@QueryParameter String calabashFeatures) {
            if (calabashFeatures == null || calabashFeatures.isEmpty()) {
                return FormValidation.error("Required!");
            }
            return FormValidation.ok();
        }

        /**
         * Validate the user entered artifact for JUnit/Robotium test content.
         *
         * @param junitArtifact The String of the jUnit artifact.
         * @return Whether or not the form was ok.
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckJunitArtifact(@QueryParameter String junitArtifact) {
            if (junitArtifact == null || junitArtifact.isEmpty()) {
                return FormValidation.error("Required!");
            }
            return FormValidation.ok();
        }

        /**
         * Validate the user entered artifact for uiautomator.
         *
         * @param uiautomatorArtifact The String of the MonkeyTalk artifact.
         * @return Whether or not the form was ok.
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckUiautomatorArtifact(@QueryParameter String uiautomatorArtifact) {
            if (uiautomatorArtifact == null || uiautomatorArtifact.isEmpty()) {
                return FormValidation.error("Required");
            }
            return FormValidation.ok();
        }

        /**
         * Validate the user entered artifact for XCTest.
         *
         * @param xctestArtifact The String of the XCTest artifact.
         * @return Whether or not the form was ok.
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckXctestArtifact(@QueryParameter String xctestArtifact) {
            if (xctestArtifact == null || xctestArtifact.isEmpty()) {
                return FormValidation.error("Required");
            }
            return FormValidation.ok();
        }

        /**
         * Validate the user entered artifact for XCTest UI.
         *
         * @param xctestUiArtifact The String of the XCTest UI artifact.
         * @return Whether or not the form was ok.
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckXctestUiArtifact(@QueryParameter String xctestUiArtifact) {
            if (xctestUiArtifact == null || xctestUiArtifact.isEmpty()) {
                return FormValidation.error("Required");
            }
            return FormValidation.ok();
        }

        /**
         * Validate if user account has unmetered devices
         *
         * @param isRunUnmetered
         * @param appArtifact
         * @return
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckIsRunUnmetered(@QueryParameter Boolean isRunUnmetered, @QueryParameter String appArtifact) {
            if (isRunUnmetered != null && isRunUnmetered) {
                AWSDeviceFarm adf = getAWSDeviceFarm();
                String os = null;
                try {
                    os = adf.getOs(appArtifact);
                } catch (AWSDeviceFarmException e) {
                    return FormValidation.error(e.getMessage());
                }
                if (adf.getUnmeteredDevices(os) <= 0) {
                    return FormValidation.error(String.format("Your account does not have unmetered %s device slots.", os));
                }
            }
            return FormValidation.ok();
        }

        /**
         * Validate the user entered execution time.
         *
         * @param jobTimeoutMinutes The Int of the limit execute time the user typed.
         * @return Whether or not the form was ok.
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckJobTimeoutMinutes(@QueryParameter Integer jobTimeoutMinutes) {
            if (jobTimeoutMinutes == null) {
                return FormValidation.error("Required");
            } else if (jobTimeoutMinutes < MIN_EXECUTION_TIMEOUT || jobTimeoutMinutes > MAX_EXECUTION_TIMEOUT) {
                return FormValidation.error("The maximum execution timeout per device should not be greater than 600 or smaller than 5 minutes.");
            }
            return FormValidation.ok();
        }

        /**
         * Refresh button clicked, clear the project and device pool caches
         * so the next click on the drop-down will get fresh content from the API.
         *
         * @return Whether or not the form was ok.
         */
        @SuppressWarnings("unused")
        public FormValidation doRefresh() {
            if (roleArn != null && !roleArn.isEmpty() && akid != null && !akid.isEmpty() && skid != null && !skid.isEmpty()) {
                return FormValidation.error("AWS Device Farm IAM Role ARN *OR* AKID/SKID must be set!");
            }

            // Clear local caches
            projectsCache.clear();
            poolsCache.clear();
            return FormValidation.ok();
        }

        /**
         * Populate the project drop-down from the AWS Device Farm API or local cache.
         *
         * @return The ListBoxModel for the UI.
         */
        @SuppressWarnings("unused")
        public ListBoxModel doFillProjectNameItems(@QueryParameter String currentProjectName) {
            // Create ListBoxModel from all projects for this AWS Device Farm account.
            List<ListBoxModel.Option> entries = new ArrayList<ListBoxModel.Option>();
            System.out.print("getting projects");
            List<String> projectNames = getAWSDeviceFarmProjects();
            System.out.print(String.format("project length = %d", projectNames.size()));
            for (String projectName : projectNames) {
                // We don't ignore case because these *should* be unique.
                entries.add(new ListBoxModel.Option(projectName, projectName, projectName.equals(currentProjectName)));
            }
            return new ListBoxModel(entries);
        }

        /**
         * Populate the device pool drop-down from AWS Device Farm API or local cache.
         * based on the selected project.
         *
         * @param projectName           Name of the project selected.
         * @param currentDevicePoolName Name of the device pool selected.
         * @return The ListBoxModel for the UI.
         */
        @SuppressWarnings("unused")
        public ListBoxModel doFillDevicePoolNameItems(@QueryParameter String projectName, @QueryParameter String currentDevicePoolName) {
            List<ListBoxModel.Option> entries = new ArrayList<ListBoxModel.Option>();
            List<String> devicePoolNames = getAWSDeviceFarmDevicePools(projectName);
            if (devicePoolNames == null) {
                return new ListBoxModel();
            }
            for (String devicePoolName : devicePoolNames) {
                // We don't ignore case because these *should* be unique.
                entries.add(new ListBoxModel.Option(devicePoolName, devicePoolName, devicePoolName.equals(currentDevicePoolName)));
            }
            return new ListBoxModel(entries);
        }

        /**
         * Populate the project drop-down Appium Version.
         *
         * @return The ListBoxModel for the UI.
         */
        @SuppressWarnings("unused")
        public ListBoxModel doFillAppiumVersionJunitItems(@QueryParameter String currentAppiumVersion) {
            List<ListBoxModel.Option> entries = new ArrayList<ListBoxModel.Option>();
            ArrayList<String> appiumVersions = new ArrayList<String>();
            appiumVersions.add(APPIUM_VERSION_1_6_3);
            appiumVersions.add(APPIUM_VERSION_1_6_5);
            appiumVersions.add(APPIUM_VERSION_1_4_16);
            for (String appiumVersion: appiumVersions) {
                entries.add(new ListBoxModel.Option(appiumVersion, appiumVersion, appiumVersion.equals(currentAppiumVersion)));
            }
            return new ListBoxModel(entries);
        }

        /**
         * Populate the project drop-down Appium Version.
         *
         * @return The ListBoxModel for the UI.
         */
        @SuppressWarnings("unused")
        public ListBoxModel doFillAppiumVersionTestngItems(@QueryParameter String currentAppiumVersion) {
            List<ListBoxModel.Option> entries = new ArrayList<ListBoxModel.Option>();
            ArrayList<String> appiumVersions = new ArrayList<String>();
            appiumVersions.add(APPIUM_VERSION_1_6_3);
            appiumVersions.add(APPIUM_VERSION_1_6_5);
            appiumVersions.add(APPIUM_VERSION_1_4_16);
            for (String appiumVersion: appiumVersions) {
                // We don't ignore case because these *should* be unique.
                entries.add(new ListBoxModel.Option(appiumVersion, appiumVersion, appiumVersion.equals(currentAppiumVersion)));
            }
            return new ListBoxModel(entries);
        }

        /**
         * Populate the project drop-down Appium Version.
         *
         * @return The ListBoxModel for the UI.
         */
        @SuppressWarnings("unused")
        public ListBoxModel doFillAppiumVersionPythonItems(@QueryParameter String currentAppiumVersion) {
            // Create ListBoxModel from all projects for this AWS Device Farm account.
            List<ListBoxModel.Option> entries = new ArrayList<ListBoxModel.Option>();
            //System.out.print("getting appium version");
            ArrayList<String> appiumVersions = new ArrayList<String>();
            appiumVersions.add(APPIUM_VERSION_1_6_3);
            appiumVersions.add(APPIUM_VERSION_1_6_5);
            appiumVersions.add(APPIUM_VERSION_1_4_16);
            for (String appiumVersion: appiumVersions) {
                entries.add(new ListBoxModel.Option(appiumVersion, appiumVersion, appiumVersion.equals(currentAppiumVersion)));
            }
            return new ListBoxModel(entries);
        }

        /**
         * Get all projects for the AWS Device Farm account tied to the API Key
         * and store them in a local cache.
         *
         * @return The List of AWS Device Farm project names.
         */
        private synchronized List<String> getAWSDeviceFarmProjects() {
            if (projectsCache.isEmpty()) {
                AWSDeviceFarm adf = getAWSDeviceFarm();

                for (Project project : adf.getProjects()) {
                    projectsCache.add(project.getName());
                }

                Collections.sort(projectsCache, String.CASE_INSENSITIVE_ORDER);
            }
            return projectsCache;
        }

        /**
         * Get all device pools for the selected project and store them
         * in a local cache.
         *
         * @param projectName The name of the currently selected project.
         * @return The List of device pool names associated with that project.
         */
        private synchronized List<String> getAWSDeviceFarmDevicePools(String projectName) {
            List<String> poolNames = poolsCache.get(projectName);

            if (poolNames == null || poolNames.isEmpty()) {
                AWSDeviceFarm adf = getAWSDeviceFarm();
                try {
                    List<DevicePool> pools = adf.getDevicePools(projectName);
                    poolNames = new ArrayList<String>();
                    for (DevicePool pool : pools) {
                        poolNames.add(pool.getName());
                    }

                    Collections.sort(poolNames, String.CASE_INSENSITIVE_ORDER);
                } catch (AWSDeviceFarmException e) {
                }

                poolsCache.put(projectName, poolNames);
            }
            return poolNames;
        }

        /**
         * Bind descriptor object to capture global plugin settings from 'Manage Jenkins'.
         *
         * @param req  The StaplerRequest.
         * @param json The JSON to bind this to.
         * @return Always true, for whatever reason.
         */
        @Override
        public boolean configure(StaplerRequest req, JSONObject json) {
            req.bindJSON(this, json);
            save();
            return true;
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Run Tests on AWS Device Farm";
        }
    }
}
