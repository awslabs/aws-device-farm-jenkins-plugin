AWS Device Farm Jenkins Plugin
------------------------------

AWS Device Farm integration with Jenkins CI

This plugin provides AWS Device Farm functionality from your own Jenkins CI server:

![main-page](https://raw.github.com/awslabs/aws-device-farm-jenkins-plugin/master/ext/main-page.png)

![configuration](https://raw.github.com/awslabs/aws-device-farm-jenkins-plugin/master/ext/configuration.png)

It also provides the device state details specification if the checkboxs are checked. Otherwise, the default settings will be used:
![device-state-specification](https://raw.github.com/awslabs/aws-device-farm-jenkins-plugin/master/ext/device-state-specification.png)

It also can pull down all of the test artifacts (logs, screenshots, etc.) locally: 

![build-artifacts](https://raw.github.com/awslabs/aws-device-farm-jenkins-plugin/master/ext/build-artifacts.png)

Usage
=====

## Building the plugin:

1. Clone the GitHub repository.
2. Import the Maven project into your favorite IDE (Eclipse, IntelliJ, etc.).
3. Build the plugin using the Makefile (`make clean compile`).
4. The plugin is created at `target/aws-device-farm.hpi`.

## Installing the plugin:

### Manual install:

1. Copy the `hpi` file to your Jenkins build server and place it in the Jenkins plugin directory (usually `/var/lib/jenkins/plugins`).
2. Ensure that the plugin is owned by the `jenkins` user.
3. Restart Jenkins.

### Web UI install:

1. Log into your Jenkins web UI.
2. On the left-hand side of the screen, click “Manage Jenkins”.
3. Click “Manage Plugins”.
4. Near the top of the screen, click on the “Advanced” tab.
5. Under the “Upload Plugin”, click “Choose File” and select the AWS Device Farm Jenkins plugin that you previously downloaded.
6. Click “Upload”.
7. Check the “Restart Jenkins when installation is complete and no jobs are running” checkbox.
8. Wait for Jenkins to restart.

## Generating a proper IAM user:
Note : Follow these steps if you are using Secret and Access key to access Device Farm.
If you want to access Device Farm using IAM Role, refer to "Generating a proper IAM role" section

1. Log into your AWS web console UI.
2. Click “Identity & Access Management”.
3. On the left-hand side of the screen, click “Users”.
4. Click “Create New Users”.
5. Enter a user name of your choice.
6. Leave the “Generate an access key for each user” checkbox checked.
7. Click “Create”.
8. View or optionally download the User security credentials that were created; you will them them later.
9. Click “Close” to return to the IAM screen.
10. Click your user name in the list.
11. Under the Inline Policies header, click the “AWS IAM role ARN for your AWS Device Farm account” link to create a new inline policy.
12. Select the “Custom Policy” radio button.
13. Click “Select”.
14. Give your policy a name under “Policy Name”.
15. Copy/paste the following policy into “Policy Document”:
```
    {
        "Version": "2012-10-17",
        "Statement": [
            {
                "Sid": "DeviceFarmAll",
                "Effect": "Allow",
                "Action": [ "devicefarm:*" ],
                "Resource": [ "*" ]
            }
        ]
    }
```
16. Click “Apply Policy”.

## Generating a proper IAM role
Note : Only applies when you want to access Device Farm through an IAM Role.

You need to create one Device Farm access role to access the Device Farm Resource and one User or Role depending upon how you are running Jenkins.

- Creating Device Farm Role.
1. Log into your AWS web console UI.
2. Click “Identity & Access Management”.
3. On the left-hand side of the screen, click “Roles”.
4. Click “Create Role”.
5. In the AWS service section select Ec2 and click on Next.
6. Under the Inline Policies header, search and click the “AWSDeviceFarmFullAccess ” policy.This will give complete device farm access.
7. Go the the summary page of the role and click on edit next to "Maximum CLI/API session duration" and select 8hrs.
This sets the expiration of the session associated with the IAM role to 8 hrs.
8. Now you want to give the access to the role/user to assume this role
Open the new role you have created in console and then click on "Trust Relationships" tab. Click on "Edit Trust Relationship" and enter the following policy :
```
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "AWS": "To be replace by Arn of user/role running on the Jenkins machine"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
```

- Setting up Role/User running Jenkins

1. We need to also give access 
2. Go to the user or role that you want to add policy to.
3. In the permission tab click on Create Policy.
4. Select STS in the service name, search for assume role in actions and select it.
5. In the Resources section enter the arn for the device farm role.
6. Click on review policy and then enter name and description and click on Create policy.

## First-time configuration instructions:

1. Log into your Jenkins web UI.
2. On the left-hand side of the screen, click “Manage Jenkins”
3. Click “Configure System”.
4. Scroll down to the “AWS Device Farm” header.
5. If you are using IAM Role to configure the Jenkins Plugin, add the ARN for the role here.
6. If not, Copy/paste your AKID and SKID you created previously into their respective boxes.
7. Click “Save”.

## Using the plugin in Jenkins job:

1. Log into your Jenkins web UI.
2. Click on the job you wish to edit.
3. On the left-hand side of the screen, click “Configure”.
4. Scroll down to the “Post-build Actions” header.
5. Click “Add post-build action” and select “Run Tests on AWS Device Farm”.
6. Select the project you would like to use.
7. Select the device pool you would like to use.
8. Select if you'd like to have the test artifacts (such as the logs and screenshots) archived locally.
9. In “Application”, fill in the path to your compiled application for testing native or hybrid app. Check "It is a web application." for testing web app.
10. Choose the test framework, provide the path to your test package location and other relavent details.
11. Configure device state parameters like radio details, extra data and device locations.
12. Configure the maximum execution timeout. The default execution timeout is 60 minutes.
13. Set the execution configuration parameters: video recording and app performance monitoring.
14. Click “Save”.

## Using the plugin in Jenkins Pipeline

1. Go to Job > Pipeline Syntax > Snippet Generator
2. Select "devicefarm" sample step or "step: General Build Step" > "Run Tests on AWS Device Farm"
3. Input the [Device Farm Run Configuration](https://docs.aws.amazon.com/devicefarm/latest/developerguide/test-runs.html#test-runs-configuration)
4. Click "Generate Pipeline Script"

## TroubleShooting

# "Invalid Credentials" Error while validating Credentials

We validate two things: 1) If the credentials are valid, 2) and if they have access to AWS Device Farm

1. Log in to your Jenkins host.
2. If using access and secret key,
Run "aws devicefarm list-projects" on the host running Jenkins using the AWS CLI.
If this fails, first check you credentials. If they are correct, refer to the "Generating a proper IAM user" section to ensure that you have given Device Farm necessary credentials.
3. If you are using role ARN
Run "aws sts assume-role --role-arn "EnterRoleArnHere --duration-seconds 28800" using the AWS CLI.
If this fails, refer to the "Generating a proper IAM role" section to verify if the role has correct assume role permissions.
Run "aws devicefarm list-projects" using the AWS CLI.
If this fails, your role doesn't have access to device farm. Please re-verify the steps above.

# Project section not being populated with the latest data

1. Verify that the Project you are looking for is visible by logging in to the Device Farm console.
2. If yes, go to the Jenkins -> Manage Jenkins -> Configure System -> AWS Device Farm section and click on Validate.

Dependencies
============

* AWS SDK 1.11.126 or later.
