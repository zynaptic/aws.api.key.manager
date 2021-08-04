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
# This module supports the generation of CloudFormation templates that
# can be used to deploy the AWS Lambda based API key manager.
#

import json
import configuration

#
# Creates the resource template for the DynamoDB table that will be used
# for storing the API key capabilities.
#
def createApiKeyCapabilityTable(databaseName):

    # Set the provisioned billing mode using the specified number of
    # read and write capacity units.
    if (configuration.API_KEY_TABLE_READ_CAPACITY_UNITS > 0) and (
        configuration.API_KEY_TABLE_WRITE_CAPACITY_UNITS > 0
    ):
        billingMode = {
            "BillingMode": "PROVISIONED",
            "ProvisionedThroughput": {
                "ReadCapacityUnits": configuration.API_KEY_TABLE_READ_CAPACITY_UNITS,
                "WriteCapacityUnits": configuration.API_KEY_TABLE_WRITE_CAPACITY_UNITS,
            },
        }

    # Set the billing mode to use unprovisioned per-transaction billing.
    else:
        billingMode = {"BillingMode": "PAY_PER_REQUEST"}

    # Set the fixed table name if required.
    if databaseName != None:
        tableName = {"TableName": databaseName}
    else:
        tableName = {}

    # Defines the API key capability table using the API key as the
    # primary index.
    tableDefinition = {
        "ApiKeyCapabilityTable": {
            "Type": "AWS::DynamoDB::Table",
            "Properties": {
                "AttributeDefinitions": [
                    {"AttributeName": "apiKey", "AttributeType": "S"}
                ],
                "KeySchema": [{"AttributeName": "apiKey", "KeyType": "HASH"}],
                "TimeToLiveSpecification": {
                    "Enabled": True,
                    "AttributeName": "removalTimestamp",
                },
                **billingMode,
                **tableName,
            },
        }
    }
    return tableDefinition


#
# Creates the resource template for the AWS Lambda function that is
# used for API key management.
#
def createApiKeyManagementLambda(deploymentBucket, packageName):

    # Defines the Lambda function using the deployment package settings
    # taken from the configuration file.
    lambdaDefinition = {
        "ApiKeyManagementLambda": {
            "Type": "AWS::Lambda::Function",
            "Properties": {
                "Description": "API Key Management Service",
                "Runtime": "java8.al2",
                "MemorySize": 256,
                "Timeout": 30,
                "Handler": "com.zynaptic.aws.api.key.manager.ApiHandler",
                "Role": {"Fn::GetAtt": ["ApiKeyManagementLambdaRole", "Arn"]},
                "Code": {"S3Bucket": deploymentBucket, "S3Key": packageName},
                "Environment": {
                    "Variables": {
                        "AWS_API_KEY_TABLE_NAME": {"Ref": "ApiKeyCapabilityTable"},
                        "AWS_API_RESOURCE_KEY_CREATE_PATH": configuration.RESOURCE_KEY_CREATE_PATH,
                        "AWS_API_RESOURCE_KEY_ACCESS_PATH": configuration.RESOURCE_KEY_ACCESS_PATH,
                        "AWS_API_KEY_RETENTION_PERIOD": configuration.AWS_API_KEY_RETENTION_PERIOD,
                    }
                },
            },
        }
    }

    # Defines the IAM role used by the Lambda function. This supports
    # database read, write and delete operations as well as CloudWatch
    # logging.
    lambdaExecutionRole = {
        "ApiKeyManagementLambdaRole": {
            "Type": "AWS::IAM::Role",
            "Properties": {
                "AssumeRolePolicyDocument": {
                    "Statement": [
                        {
                            "Effect": "Allow",
                            "Principal": {"Service": ["lambda.amazonaws.com"]},
                            "Action": "sts:AssumeRole",
                        }
                    ]
                },
                "Policies": [
                    {
                        "PolicyName": "ApiKeyCapabilityAccessPolicy",
                        "PolicyDocument": {
                            "Statement": [
                                {
                                    "Effect": "Allow",
                                    "Action": [
                                        "dynamodb:PutItem",
                                        "dynamodb:GetItem",
                                        "dynamodb:DeleteItem",
                                        "dynamodb:UpdateItem",
                                    ],
                                    "Resource": {
                                        "Fn::GetAtt": ["ApiKeyCapabilityTable", "Arn"]
                                    },
                                }
                            ]
                        },
                    },
                    {
                        "PolicyName": "ApiKeyManagementLoggingPolicy",
                        "PolicyDocument": {
                            "Statement": [
                                {
                                    "Effect": "Allow",
                                    "Action": [
                                        "logs:CreateLogGroup",
                                        "logs:CreateLogStream",
                                        "logs:PutLogEvents",
                                    ],
                                    "Resource": {
                                        "Fn::Join": [
                                            "",
                                            [
                                                "arn:aws:logs:",
                                                {"Ref": "AWS::Region"},
                                                ":",
                                                {"Ref": "AWS::AccountId"},
                                                ":*",
                                            ],
                                        ]
                                    },
                                }
                            ]
                        },
                    },
                ],
            },
        }
    }
    return {**lambdaDefinition, **lambdaExecutionRole}


#
# Creates a set of REST API resource elements from a conventional
# resource path name.
#
def createGatewayResource(resourcePath):
    resourceSet = {}
    resourceHashSource = ""
    resourcePathSegments = resourcePath.split("/")
    resourceParent = {"Fn::GetAtt": ["ApiKeyRestGateway", "RootResourceId"]}
    if resourcePathSegments[0] != "":
        raise ("Invalid resource path : " + resourcePath)
    resourcePathSegments = resourcePathSegments[1:]
    for resourcePathSegment in resourcePathSegments:
        resourceHashSource += "/" + resourcePathSegment
        resourceId = "ApiKeyRestResource%X" % (abs(hash(resourceHashSource)))
        resourceSet[resourceId] = {
            "Type": "AWS::ApiGateway::Resource",
            "Properties": {
                "RestApiId": {"Ref": "ApiKeyRestGateway"},
                "ParentId": resourceParent,
                "PathPart": resourcePathSegment,
            },
        }
        resourceParent = {"Ref": resourceId}
    return resourceSet


#
# Creates the AWS API gateway template for REST API handling. This only
# supports regional endpoint configurations.
#
def createApiKeyRestGateway(apiGatewayName, stagingName):

    # Defines the REST gateway that will forward API requests to the
    # API key management Lambda function.
    restGatewayDefinition = {
        "ApiKeyRestGateway": {
            "Type": "AWS::ApiGateway::RestApi",
            "Properties": {
                "Name": apiGatewayName,
                "Description": "API Key Management Service (" + stagingName + ")",
                "EndpointConfiguration": {"Types": ["REGIONAL"]},
            },
        }
    }

    # Add the REST resources to the gateway definition.
    restGatewayDefinition.update(
        createGatewayResource(configuration.RESOURCE_KEY_CREATE_PATH)
    )
    restGatewayDefinition.update(
        createGatewayResource(configuration.RESOURCE_KEY_ACCESS_PATH)
    )

    # Add the REST key creation method to the gateway definition.
    restKeyCreateMethodDefinition = {
        "ApiKeyRestCreateMethod": {
            "Type": "AWS::ApiGateway::Method",
            "Properties": {
                "RestApiId": {"Ref": "ApiKeyRestGateway"},
                "ResourceId": {
                    "Ref": "ApiKeyRestResource%X"
                    % (abs(hash(configuration.RESOURCE_KEY_CREATE_PATH)))
                },
                "HttpMethod": "ANY",
                "AuthorizationType": "NONE",
                "Integration": {
                    "Type": "AWS_PROXY",
                    "IntegrationHttpMethod": "POST",
                    "Credentials": {"Fn::GetAtt": ["ApiKeyRestGatewayRole", "Arn"]},
                    "Uri": {
                        "Fn::Join": [
                            "",
                            [
                                "arn:aws:apigateway:",
                                {"Ref": "AWS::Region"},
                                ":lambda:path/2015-03-31/functions/",
                                {"Fn::GetAtt": ["ApiKeyManagementLambda", "Arn"]},
                                "/invocations",
                            ],
                        ]
                    },
                },
            },
        }
    }

    # Add the REST key access method to the gateway definition.
    restKeyAccessMethodDefinition = {
        "ApiKeyRestAccessMethod": {
            "Type": "AWS::ApiGateway::Method",
            "Properties": {
                "RestApiId": {"Ref": "ApiKeyRestGateway"},
                "ResourceId": {
                    "Ref": "ApiKeyRestResource%X"
                    % (abs(hash(configuration.RESOURCE_KEY_ACCESS_PATH)))
                },
                "HttpMethod": "ANY",
                "AuthorizationType": "NONE",
                "Integration": {
                    "Type": "AWS_PROXY",
                    "IntegrationHttpMethod": "POST",
                    "Credentials": {"Fn::GetAtt": ["ApiKeyRestGatewayRole", "Arn"]},
                    "Uri": {
                        "Fn::Join": [
                            "",
                            [
                                "arn:aws:apigateway:",
                                {"Ref": "AWS::Region"},
                                ":lambda:path/2015-03-31/functions/",
                                {"Fn::GetAtt": ["ApiKeyManagementLambda", "Arn"]},
                                "/invocations",
                            ],
                        ]
                    },
                },
            },
        }
    }

    # Defines the IAM role for invoking the Lambda function.
    restGatewayRole = {
        "ApiKeyRestGatewayRole": {
            "Type": "AWS::IAM::Role",
            "Properties": {
                "AssumeRolePolicyDocument": {
                    "Statement": [
                        {
                            "Effect": "Allow",
                            "Principal": {"Service": ["apigateway.amazonaws.com"]},
                            "Action": "sts:AssumeRole",
                        }
                    ]
                },
                "Policies": [
                    {
                        "PolicyName": "ApiKeyManagementLambdaPolicy",
                        "PolicyDocument": {
                            "Statement": [
                                {
                                    "Effect": "Allow",
                                    "Action": "lambda:InvokeFunction",
                                    "Resource": {
                                        "Fn::GetAtt": ["ApiKeyManagementLambda", "Arn"]
                                    },
                                }
                            ]
                        },
                    }
                ],
            },
        }
    }
    return {
        **restGatewayDefinition,
        **restKeyCreateMethodDefinition,
        **restKeyAccessMethodDefinition,
        **restGatewayRole,
    }


#
# Creates the API gateway deployment.
#
def createApiGatewayDeployment(stagingName):

    # Defines the API deployment information that will be used to make
    # the API available for external access.
    apiGatewayDeploymentInfo = {
        "ApiKeyRestGatewayDeployment": {
            "Type": "AWS::ApiGateway::Deployment",
            "DependsOn": ["ApiKeyRestCreateMethod", "ApiKeyRestAccessMethod"],
            "Properties": {
                "RestApiId": {"Ref": "ApiKeyRestGateway"},
                "StageName": stagingName,
            },
        }
    }
    return apiGatewayDeploymentInfo


#
# Creates the API custom domain name configuration.
#
def createApiCustomDomain(domainName, deploymentStage):

    # Specifies the custom domain to be used and the way in which it is
    # to be mapped to the API. This only supports regional endpoint
    # configurations that have previously been set up.
    customDomainDefinition = {
        "ApiKeyRestGatewayMapping": {
            "Type": "AWS::ApiGateway::BasePathMapping",
            "DependsOn": "ApiKeyRestGatewayDeployment",
            "Properties": {
                "DomainName": domainName,
                "BasePath": configuration.RESOURCE_CUSTOM_DOMAIN_BASE_PATH,
                "RestApiId": {"Ref": "ApiKeyRestGateway"},
                "Stage": deploymentStage,
            },
        }
    }
    return customDomainDefinition


#
# Creates the complete CloudFormation template using the parameters
# specified in the local configuration file.
#
def createTemplate(
    databaseName=configuration.API_KEY_TABLE_DEFAULT_NAME,
    deploymentBucket=configuration.AWS_S3_DEFAULT_DEPLOYMENT_BUCKET,
    packageName=configuration.AWS_LAMBDA_DEFAULT_PACKAGE_NAME,
    apiGatewayName="ApiKeyManager",
    deploymentStage="production",
    domainName=None,
    doFormat=False,
):
    resources = {}
    resources.update(createApiKeyCapabilityTable(databaseName))
    resources.update(createApiKeyManagementLambda(deploymentBucket, packageName))
    resources.update(createApiKeyRestGateway(apiGatewayName, deploymentStage))
    resources.update(createApiGatewayDeployment(deploymentStage))

    # Only add the domain name configuration if a custom domain name has
    # been defined.
    if domainName != None:
        resources.update(createApiCustomDomain(domainName, deploymentStage))

    # Convert Python template to JSON.
    templateData = {"Resources": resources}
    if doFormat:
        template = json.dumps(templateData, indent=4)
    else:
        template = json.dumps(templateData)
    return template


#
# Write the template to a local file if invoked directly.
#
if __name__ == "__main__":
    try:
        print("Writing CloudFormation template to cloudformation-template.json")
        with open("cloudformation-template.json", "w") as f:
            f.write(createTemplate(doFormat=True))
    except KeyboardInterrupt as e:
        exit()
    except Exception as e:
        print(e)
        exit()
