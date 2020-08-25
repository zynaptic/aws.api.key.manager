#!/usr/bin/env python3

#
# Capability based API key management service for AWS Lambda with DynamoDB.
#
# Copyright (c) 2020, Zynaptic Limited.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
#
# Please visit www.zynaptic.com or contact reaction@zynaptic.com if you need
# additional information or have any questions.
#

#
# AWS deployment script. This script makes use of the AWS command line
# utilities on the local host to deploy the API key management service.
# The default AWS command line credentials and option settings will be
# used.
#

import os
import argparse
import json
import base64
import secrets
import datetime
import configuration
import cloudformation

from pathlib import Path

#
# Extract the command line arguments.
#
def parseCommandLine():
    parser = argparse.ArgumentParser(
        description="This script is used to deploy the capability based "
        + "API key management service to AWS. It will use the default "
        + "AWS command line credentials and option settings.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )

    # Derive the default AWS region from the configuration options.
    with os.popen("aws configure get region", "r") as f:
        awsRegion = f.read().strip()

    # Parse the optional parameters.
    parser.add_argument(
        "--region", default=awsRegion, help="the name of the AWS region to use"
    )
    parser.add_argument(
        "--deployment_bucket",
        default=configuration.AWS_S3_DEFAULT_DEPLOYMENT_BUCKET,
        help="the name of the AWS S3 deployment bucket to use",
    )
    parser.add_argument(
        "--database_name",
        default=configuration.API_KEY_TABLE_DEFAULT_NAME,
        help="the name of the AWS DynamoDB database to use",
    )
    parser.add_argument(
        "--stack_name",
        default=configuration.AWS_CLOUD_FORMATION_DEFAULT_STACK_NAME,
        help="the name of the AWS CloudFormation stack to use",
    )
    parser.add_argument(
        "--capability_file",
        default=None,
        help="the file containing the root set of capabilities",
    )
    parser.add_argument(
        "--api_gateway_name",
        default="ApiKeyManager",
        help="the API gateway name used by the AWS API gateway service",
    )
    parser.add_argument(
        "--deployment_stage",
        default="testing",
        help="the API deployment staging name to use",
    )
    parser.add_argument(
        "--domain_name",
        default=None,
        help="the custom domain name to be used by the AWS API gateway",
    )
    args = parser.parse_args()
    return args


#
# Use the AWS command line tools to check whether the API key database
# is already present. The deployment process is halted if an active
# key database is currently in use.
#
def checkKeyDatabase(awsRegion, databaseName):
    with os.popen(
        "aws dynamodb list-tables --region " + awsRegion + " --no-paginate", "r"
    ) as f:
        tableList = json.loads(f.read())
    for tableName in tableList["TableNames"]:
        assert tableName != databaseName, (
            "API key database '%s' is already configured for this AWS region."
            % tableName
        )


#
# Use the AWS command line tools to check whether the S3 deployment
# bucket is present and create it if required.
#
def checkDeploymentBucket(awsRegion, bucketName):
    with os.popen(
        "aws s3api list-buckets --region " + awsRegion + " --no-paginate", "r"
    ) as f:
        bucketList = json.loads(f.read())
    for bucketInfo in bucketList["Buckets"]:
        if bucketInfo["Name"] == bucketName:
            print("AWS S3 deployment bucket '%s' already configured." % bucketName)
            return
    createBucketCommand = (
        "aws s3api create-bucket --bucket "
        + bucketName
        + " --region "
        + awsRegion
        + " --create-bucket-configuration LocationConstraint="
        + awsRegion
    )
    with os.popen(createBucketCommand, "r") as f:
        status = json.loads(f.read())
    print("Created AWS S3 deployment bucket: %s" % status["Location"])


#
# Use the AWS command line tools to check that the specified domain name
# is configured for use with the AWS API gateway.
#
def checkDnsRegistration(awsRegion, domainName):

    # Read the list of domain names associated with the API gateway.
    readDomainNameInfoCommand = (
        "aws apigateway get-domain-names --region  " + awsRegion + " --no-paginate"
    )
    with os.popen(readDomainNameInfoCommand, "r") as f:
        domainNameInfo = json.loads(f.read())

    # Search for a matching domain name.
    matchedDomain = None
    for domainNameItem in domainNameInfo["items"]:
        if domainNameItem["domainName"] == domainName:
            matchedDomain = domainNameItem
            break
    assert matchedDomain != None, (
        "Failed to find configured domain name for '" + domainName + "'"
    )

    # Check that the domain name is configured as a regional domain.
    assert "regionalDomainName" in matchedDomain, (
        "Matched domain name '" + domainName + "' is not a regional endpoint"
    )


#
# Use the AWS command line tools to upload a file to the S3 deployment
# bucket.
#
def uploadDeploymentFile(awsRegion, bucketName, packageName, packageFile):
    uploadFileCommand = (
        "aws s3api put-object --region "
        + awsRegion
        + " --bucket "
        + bucketName
        + " --key "
        + packageName
        + " --body "
        + str(packageFile)
    )
    with os.popen(uploadFileCommand, "r") as f:
        status = json.loads(f.read())
    packageTag = status["ETag"]
    print("Uploaded %s with S3 tag %s" % (packageName, packageTag))


#
# Initiates the CloudFormation stack creation process.
#
def createStack(awsRegion, bucketName, stackName, templateName):
    createStackCommand = (
        "aws cloudformation create-stack --stack-name "
        + stackName
        + " --template-url https://"
        + bucketName
        + ".s3."
        + awsRegion
        + ".amazonaws.com/"
        + templateName
        + " --capabilities CAPABILITY_IAM"
    )
    with os.popen(createStackCommand, "r") as f:
        status = json.loads(f.read())
    stackId = status["StackId"]
    print("Creating stack : " + stackId)
    os.system("aws cloudformation wait stack-create-complete --stack-name " + stackId)
    print("Stack creation complete")


#
# Formats a data item in DynamoDB attribute format.
#
def formatDynamoAttr(value):
    if isinstance(value, str):
        return {"S": value}
    elif isinstance(value, bool):
        return {"BOOL": value}
    elif isinstance(value, (int, float)):
        return {"N": str(value)}
    elif isinstance(value, dict):
        return {"M": mapToDynamoAttrs(value)}
    elif isinstance(value, list):
        attrList = []
        for listItem in value:
            attrList.append(formatDynamoAttr(listItem))
        return {"L": attrList}


#
# Converts a native dictionary to DynamoDB attribute format.
#
def mapToDynamoAttrs(pythonDict):
    dynamoAttrs = {}
    for item in pythonDict.items():
        dynamoAttrs[item[0]] = formatDynamoAttr(item[1])
    return dynamoAttrs


#
# Loads the root key into the API key database.
#
def loadRootKey(awsRegion, databaseName, capabilitySet):
    rootKeyBytes = secrets.token_bytes(configuration.API_KEY_GENERATION_SIZE)
    rootKey = base64.urlsafe_b64encode(rootKeyBytes).decode("utf-8")
    expiryDate = datetime.datetime(9999, 12, 31, 0, 0)
    expiryTimestamp = int(expiryDate.timestamp() * 1000)

    # Format the table entry and then map to DynamoDB attributes.
    tableEntry = {
        "apiKey": rootKey,
        "authorityKeys": [],
        "description": "API key manager root capability set",
        "expiryTimestamp": expiryTimestamp,
        "capabilitySet": capabilitySet,
    }
    tableEntryAttrs = mapToDynamoAttrs(tableEntry)
    tableEntryJson = json.dumps(tableEntryAttrs)

    # Write the DynamoDB entry via the AWS CLI.
    writeEntryCommand = (
        "aws dynamodb put-item --region "
        + awsRegion
        + " --table-name "
        + databaseName
        + " --item '"
        + tableEntryJson
        + "' --return-consumed-capacity TOTAL"
    )
    with os.popen(writeEntryCommand, "r") as f:
        status = json.loads(f.read())
    print("Loaded root key into " + status["ConsumedCapacity"]["TableName"])
    return rootKey


#
# Derives the base URL for the API.
#
def deriveApiBaseUrl(awsRegion, stackName, deploymentStage):

    # Read the deployed API ID from CloudFormation.
    readApiMetadataCommand = (
        "aws cloudformation describe-stack-resource --stack-name "
        + stackName
        + " --logical-resource-id ApiKeyRestGateway"
    )
    with os.popen(readApiMetadataCommand, "r") as f:
        status = json.loads(f.read())
    awsResourceId = status["StackResourceDetail"]["PhysicalResourceId"]

    # Build the base URL for the API.
    return (
        "https://"
        + awsResourceId
        + ".execute-api."
        + awsRegion
        + ".amazonaws.com/"
        + deploymentStage
    )


#
# Derives the public URL for the API when a custom domain is in use.
#
def derivePublicBaseUrl(domainName):
    if domainName == None:
        return None
    else:
        return (
            "https://"
            + domainName
            + "/"
            + configuration.RESOURCE_CUSTOM_DOMAIN_BASE_PATH
        )


#
# Runs a test read on the root key URL.
#
def testReadRootKey(baseUrl, rootKey):
    rootKeyUrl = baseUrl + configuration.RESOURCE_KEY_ACCESS_PATH
    rootKeyUrl = rootKeyUrl.replace("{apiKey}", rootKey)
    print("Reading key from " + rootKeyUrl)
    curlCommand = (
        "curl --no-progress-meter -H x-zynaptic-api-key:" + rootKey + " " + rootKeyUrl
    )
    with os.popen(curlCommand, "r") as f:
        status = json.loads(f.read())
    print(json.dumps(status, indent=4))


#
# Provide main entry point.
#
def main(params):

    # Use the location of this script to determine the repository root
    # directory.
    rootDir = Path(__file__).resolve().parent.parent

    # Prepend deployment stage name to domain name for non-production
    # deployments.
    if params.deployment_stage == "production":
        stackName = params.stack_name
        databaseName = params.database_name
        apiGatewayName = params.api_gateway_name
        domainName = params.domain_name
    else:
        stackName = params.stack_name + "-" + params.deployment_stage
        databaseName = params.database_name + "-" + params.deployment_stage
        apiGatewayName = params.api_gateway_name + "-" + params.deployment_stage
        if params.domain_name != None:
            domainName = params.deployment_stage + "." + params.domain_name
        else:
            domainName = None

    # Perform pre-deployment checks.
    checkKeyDatabase(params.region, databaseName)
    checkDeploymentBucket(params.region, params.deployment_bucket)
    if domainName != None:
        checkDnsRegistration(params.region, domainName)

    # Run the Maven build and then upload the deployment package. The
    # deployment package name is inferred by looking for the only .jar
    # file in the build directory.
    os.system("cd %s && mvn install" % (rootDir))
    targetPath = rootDir / "target"
    jarFiles = list(targetPath.glob("*.jar"))
    assert (
        len(jarFiles) == 1
    ), "Maven build should produce exactly one target .jar file."
    deploymentPackageFile = jarFiles[0]
    deploymentPackageName = deploymentPackageFile.name
    uploadDeploymentFile(
        params.region,
        params.deployment_bucket,
        deploymentPackageName,
        deploymentPackageFile,
    )

    # Create the CloudFormation template and upload it to the deployment
    # bucket.
    templateName = stackName + "-template.json"
    template = cloudformation.createTemplate(
        databaseName=databaseName,
        deploymentBucket=params.deployment_bucket,
        packageName=deploymentPackageName,
        apiGatewayName=apiGatewayName,
        deploymentStage=params.deployment_stage,
        domainName=domainName,
        doFormat=True,
    )
    print("Writing CloudFormation template to " + templateName)
    with open(templateName, "w") as f:
        f.write(template)
    uploadDeploymentFile(
        params.region, params.deployment_bucket, templateName, templateName
    )

    # Run the CloudFormation stack creation process.
    createStack(params.region, params.deployment_bucket, stackName, templateName)

    # Load the root capability set if specified.
    if params.capability_file == None:
        capabilitySet = {}
    else:
        with open(params.capability_file, "r") as f:
            capabilitySet = json.loads(f.read())

    # Include required capabilities for API key management.
    if configuration.API_KEY_CREATE_CAPABILITY_NAME not in capabilitySet:
        capabilitySet[configuration.API_KEY_CREATE_CAPABILITY_NAME] = {
            "capabilityLock": False
        }
    if configuration.API_KEY_READ_CAPABILITY_NAME not in capabilitySet:
        capabilitySet[configuration.API_KEY_READ_CAPABILITY_NAME] = {}
    if configuration.API_KEY_DELETE_CAPABILITY_NAME not in capabilitySet:
        capabilitySet[configuration.API_KEY_DELETE_CAPABILITY_NAME] = {}

    # Load the root key to the key database.
    rootKey = loadRootKey(params.region, databaseName, capabilitySet)

    # Derive and report the base URI to use when accessing the API.
    apiBaseUrl = deriveApiBaseUrl(params.region, stackName, params.deployment_stage)
    publicBaseUrl = derivePublicBaseUrl(domainName)
    print(
        "\n--------------------------------------------------------------------------------\n"
    )
    print(
        "API key create URL    : " + apiBaseUrl + configuration.RESOURCE_KEY_CREATE_PATH
    )
    print(
        "API key access URL    : " + apiBaseUrl + configuration.RESOURCE_KEY_ACCESS_PATH
    )
    if publicBaseUrl != None:
        print(
            "Public key create URL : "
            + publicBaseUrl
            + configuration.RESOURCE_KEY_CREATE_PATH
        )
        print(
            "Public key access URL : "
            + publicBaseUrl
            + configuration.RESOURCE_KEY_ACCESS_PATH
        )
    print("\nAPI key management root key: " + rootKey)
    print(
        "\n--------------------------------------------------------------------------------\n"
    )

    # Test access to the API using CURL.
    testReadRootKey(apiBaseUrl, rootKey)
    if publicBaseUrl != None:
        testReadRootKey(publicBaseUrl, rootKey)
    print(
        "\n--------------------------------------------------------------------------------"
    )


#
# Run the script with the provided command line options.
#
try:
    params = parseCommandLine()
    main(params)
except KeyboardInterrupt as e:
    exit()
except Exception as e:
    print(e)
    exit()
