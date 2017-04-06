package org.jenkinsci.plugins.awsdevicefarm;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.devicefarm.AWSDeviceFarmClient;
import com.amazonaws.services.devicefarm.model.*;
import hudson.EnvVars;
import hudson.FilePath;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jenkinsci.plugins.awsdevicefarm.test.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

/**
 * AWS Device Farm API wrapper class.
 */
public class AWSDeviceFarm {
    private AWSDeviceFarmClient api;
    private PrintStream log;
    private FilePath workspace;
    private FilePath artifactsDir;
    private EnvVars env;

    //// Constructors

    /**
     * AWSDeviceFarm constructor.
     * @param roleArn Role ARN to use for authentication.
     */
    public AWSDeviceFarm(String roleArn) {
        this(null, roleArn);
    }

    /**
     * AWSDeviceFarm constructor.
     * @param creds AWSCredentials to use for authentication.
     */
    public AWSDeviceFarm(AWSCredentials creds) { this(creds, null); }

    /**
     * Private AWSDeviceFarm constructor. Uses the roleArn to generate STS creds if the roleArn isn't null; otherwise
     * just uses the AWSCredentials creds.
     * @param creds AWSCredentials creds to use for authentication.
     * @param roleArn Role ARN to use for authentication.
     */
    private AWSDeviceFarm(AWSCredentials creds, String roleArn) {
        if (roleArn != null) {
            STSAssumeRoleSessionCredentialsProvider sts = new STSAssumeRoleSessionCredentialsProvider
                    .Builder(roleArn, RandomStringUtils.randomAlphanumeric(8))
                    .build();
            creds = sts.getCredentials();
        }

        ClientConfiguration clientConfiguration = new ClientConfiguration().withUserAgent("AWS Device Farm - Jenkins v1.0");
        api = new AWSDeviceFarmClient(creds, clientConfiguration);
        api.setServiceNameIntern("devicefarm");
    }

    //// Builder Methods

    /**
     * Logger setter.
     * @param logger The log print stream.
     * @return The AWSDeviceFarm object.
     */
    public AWSDeviceFarm withLogger(PrintStream logger) {
        this.log = logger;
        return this;
    }

    /**
     * Workspace setter.
     * @param workspace The FilePath to the Jenkins workspace.
     * @return The AWSDeviceFarm object.
     */
    public AWSDeviceFarm withWorkspace(FilePath workspace) {
        this.workspace = workspace;
        return this;
    }

    /**
     * Artifacts directory setter.
     * @param artifactsDir The FilePath to the Jenkins artifacts directory.
     * @return The AWSDeviceFarm object.
     */
    public AWSDeviceFarm withArtifactsDir(FilePath artifactsDir) {
        this.artifactsDir = artifactsDir;
        return this;
    }

    /**
     * Environment setter.
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
     * @return A List of the Device Farm projects.
     */
    public List<Project> getProjects() {
        ListProjectsResult result = api.listProjects(new ListProjectsRequest());
        if (result == null) {
            return new ArrayList<Project>();
        }
        else {
            return result.getProjects();
        }
    }

    /**
     * Get Device Farm project by name.
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

    /**
     * Get Device Farm device pools for a given Device Farm project.
     * @param projectName String name of the Device Farm project.
     * @return A List of the Device Farm device pools.
     * @throws AWSDeviceFarmException
     */
    public List<DevicePool> getDevicePools(String projectName) throws AWSDeviceFarmException {
        return getDevicePools(getProject(projectName));
    }

    /**
     * Get Device Farm device pools for a given Device Farm project.
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
     * Get Device Farm device pool by Device Farm project and device pool name.
     * @param projectName String name of the Device Farm project.
     * @param devicePoolName String name of the device pool.
     * @return The Device Farm device pool.
     * @throws AWSDeviceFarmException
     */
    public DevicePool getDevicePool(String projectName, String devicePoolName) throws AWSDeviceFarmException {
        return getDevicePool(getProject(projectName), devicePoolName);
    }

    /**
     * Get Device Farm device pool by Device Farm project and device pool name.
     * @param project The Device Farm project.
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
     * Upload an app to Device Farm to be tested.
     * @param project The Device Farm project to upload to.
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
        }
        else {
            throw new AWSDeviceFarmException(String.format("Unknown app artifact to upload: %s", appArtifact));
        }

        return upload(project, appArtifact, type);
    }

    /**
     * Upload a test to Device Farm.
     * @param project The Device Farm project to upload to.
     * @param test Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, InstrumentationTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getArtifact(), AWSDeviceFarmUploadType.INSTRUMENTATION);
    }

    /**
     * Upload a test to Device Farm.
     * @param project The Device Farm project to upload to.
     * @param test Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, CalabashTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getFeatures(), AWSDeviceFarmUploadType.CALABASH);
    }

    /**
     * Upload a test to Device Farm.
     * @param project The Device Farm project to upload to.
     * @param test Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, UIAutomatorTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.UIAUTOMATOR);
    }
    
    /**
     * Upload a test to Device Farm.
     * @param project The Device Farm project to upload to.
     * @param test Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, UIAutomationTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.UIAUTOMATION);
    }
    
    /**
     * Upload a test to Device Farm.
     * @param project The Device Farm project to upload to.
     * @param test Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, XCTestTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.XCTEST);
    }
    
    /**
     * Upload a test to Device Farm.
     * @param project The Device Farm project to upload to.
     * @param test Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, XCTestUITest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.XCTEST_UI);
    }

    /**
     * Upload a test to Device Farm.
     * @param project The Device Farm project to upload to.
     * @param test Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, AppiumJavaTestNGTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.APPIUM_JAVA_TESTNG);
    }

    /**
     * Upload a test to Device Farm.
     * @param project The Device Farm project to upload to.
     * @param test Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, AppiumJavaJUnitTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.APPIUM_JAVA_JUNIT);
    }
    
    /**
     * Upload a test to Device Farm.
     * @param project The Device Farm project to upload to.
     * @param test Test object containing relevant test information.
     * @return The Device Farm Upload object.
     * @throws IOException
     * @throws AWSDeviceFarmException
     */
    public Upload uploadTest(Project project, AppiumPythonTest test) throws InterruptedException, IOException, AWSDeviceFarmException {
        return upload(project, test.getTests(), AWSDeviceFarmUploadType.APPIUM_PYTHON);
    }

    /**
     * Private method to handle uploading apps and tests to Device Farm.
     * @param project The Device Farm project to upload to.
     * @param artifact Possibly glob-y path to the file to be uploaded.
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
     * @param file The file to upload.
     * @param project The Device Farm project to upload to.
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
     * @param file The file to upload.
     * @param project TheDevice Farm project to upload to.
     * @param uploadType The type of upload (app/test/etc.).
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
            throw new AWSDeviceFarmException(String.format("Upload returned non-200 responses: %i", response.getStatusLine().getStatusCode()));
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
                }
                else if ("FAILED".equalsIgnoreCase(status)) {
                    throw new AWSDeviceFarmException(String.format("Upload %s failed!", upload.getName()));
                }
                else {
                    try {
                        writeToLog(String.format("Waiting for upload %s to be ready (current status: %s)", file.getName(), status));
                        Thread.sleep(5000);
                    }
                    catch (InterruptedException e) {
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
     * @param projectArn The ARN of the Device Farm project to run the test on.
     * @param name The name of the test run.
     * @param appArn The ARN of the app to test.
     * @param devicePoolArn The ARN of the device pool to test against.
     * @param test The run test.
     * @param configuration The run configuration.
     * @return The result of the schedle run.
     */
    public ScheduleRunResult scheduleRun(String projectArn,
                                         String name,
                                         String appArn,
                                         String devicePoolArn,
                                         ScheduleRunTest test,
                                         ScheduleRunConfiguration configuration) {
        ScheduleRunRequest request = new ScheduleRunRequest()
                .withProjectArn(projectArn)
                .withName(name)
                .withAppArn(appArn)
                .withDevicePoolArn(devicePoolArn)
                .withTest(test);

        if (configuration != null) {
            request.withConfiguration(configuration);
        }

        return api.scheduleRun(request);
    }

    public GetRunResult describeRun(String runArn) {
        return api.getRun(new GetRunRequest()
                .withArn(runArn));
    }

    /**
     * Gets a local File instance of a glob file pattern, pulling it from a slave if necessary.
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
            }
            else if (matches.length != 1) {
                StringBuilder msg = new StringBuilder(String.format("More than one match found for pattern '%s':", pattern));
                for (FilePath fp : matches) {
                    msg.append(String.format("\n\t%s", fp.getRemote()));
                }
                throw new AWSDeviceFarmException(msg.toString());
            }

            // Now that we know it's one and only one, take it.
            FilePath artifact = matches[0];
            writeToLog(String.format("Archiving artifact '%s'", artifact.getName()));

            // Copy file (master or slave) to the build artifact directory on the master.
            FilePath localArtifact = new FilePath(artifactsDir, artifact.getName());
            artifact.copyTo(localArtifact);
            return new File(localArtifact.getRemote());
        }
        catch (IOException e) {
            throw new AWSDeviceFarmException(String.format("Unable to find artifact %s", e.toString()));
        }
        catch (InterruptedException e) {
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

    public String getOs(String appArtifact) throws AWSDeviceFarmException  {
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
     * @param message The message to log.
     */
    private void writeToLog(String message) {
        if (log != null) {
            log.println(String.format("[AWSDeviceFarm] %s", message));
        }
    }
}
