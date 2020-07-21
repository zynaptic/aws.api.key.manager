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
# AWS DNS setup script. This script makes use of the AWS command line
# utilities on the local host to set up the AWS Route53 DNS service
# when using a custom domain for API endpoints. The default AWS command
# line credentials and option settings will be used.
#
# The DNS setup is implemented as an independent CloudFormation stack
# since the same DNS configuration can be shared by multiple API
# endpoints hosted on the same 'server'. Only regional deployment is
# supported.
#

import os
import argparse
import json
import configuration

#
# Extract the command line arguments.
#
def parseCommandLine():

    parser = argparse.ArgumentParser(
        description="This script is used to deploy the API custom domain "
        + "configuration to AWS Route53. It will use the default AWS "
        + "command line credentials and option settings.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )

    # Derive the default AWS region from the configuration options.
    with os.popen("aws configure get region", "r") as f:
        awsRegion = f.read().strip()

    # Parse the optional parameters.
    parser.add_argument(
        "--domain_name",
        required=True,
        help="the custom domain name to be used by the AWS API gateway",
    )
    parser.add_argument(
        "--region", default=awsRegion, help="the name of the AWS region to use"
    )
    parser.add_argument(
        "--stack_name",
        default=configuration.AWS_CLOUD_FORMATION_DEFAULT_DNS_STACK_NAME,
        help="the name of the AWS CloudFormation stack to use",
    )
    args = parser.parse_args()
    return args


#
# Use the AWS command line tools to check that the specified domain name
# is managed by AWS Route53. This also extracts the Route53 hosted zone
# ID for use when creating the TLS certificate.
#
def checkDnsRegistration(domainName):
    with os.popen("aws route53 list-hosted-zones") as f:
        hostedZoneInfo = json.loads(f.read())

    # Perform a subdomain match to find a matching hosted zone.
    fullyQualifiedDomainName = domainName + "."
    for hostedZone in hostedZoneInfo["HostedZones"]:
        if fullyQualifiedDomainName.endswith("." + hostedZone["Name"]):
            hostedZoneId = hostedZone["Id"]
            break

    # Remove the path component from the hosted zone ID.
    assert hostedZoneId != None, (
        "Failed to find matching Route53 entry for " + domainName
    )
    hostedZoneId = hostedZoneId.split("/")[-1]
    print("Resolved hosted zone ID for " + domainName + " as: " + hostedZoneId)
    return hostedZoneId


#
# Creates the API custom domain name configuration.
#
def createApiCustomDomain(domainName, dnsHostedZoneId):

    # Defines the TLS certificate to be used by the domain. This assumes
    # the domain is fully managed by AWS Route53 so that this can be
    # automatically configured using DNS authorisation.
    customDomainCert = {
        "ApiGatewayCert": {
            "Type": "AWS::CertificateManager::Certificate",
            "Properties": {
                "DomainName": domainName,
                "ValidationMethod": "DNS",
                "DomainValidationOptions": [
                    {"DomainName": domainName, "HostedZoneId": dnsHostedZoneId}
                ],
            },
        }
    }

    # Defines the custom domain to be used.
    customDomainDefinition = {
        "ApiGatewayDomain": {
            "Type": "AWS::ApiGateway::DomainName",
            "Properties": {
                "DomainName": domainName,
                "RegionalCertificateArn": {"Ref": "ApiGatewayCert"},
                "EndpointConfiguration": {"Types": ["REGIONAL"]},
                "SecurityPolicy": "TLS_1_2",
            },
        }
    }

    # Insert a DNS 'A' record for the DNS name alias.
    aliasTarget = {
        "DNSName": {"Fn::GetAtt": ["ApiGatewayDomain", "RegionalDomainName"]},
        "HostedZoneId": {"Fn::GetAtt": ["ApiGatewayDomain", "RegionalHostedZoneId"]},
    }
    dnsRecordDefinition = {
        "ApiGatewayDnsRecord": {
            "Type": "AWS::Route53::RecordSet",
            "DependsOn": "ApiGatewayDomain",
            "Properties": {
                "Type": "A",
                "AliasTarget": aliasTarget,
                "HostedZoneId": dnsHostedZoneId,
                "Name": domainName,
            },
        }
    }
    return {**customDomainCert, **customDomainDefinition, **dnsRecordDefinition}


#
# Initiates the CloudFormation stack creation process.
#
def createStack(awsRegion, stackName, templateName):
    createStackCommand = (
        "aws cloudformation create-stack --region "
        + awsRegion
        + " --stack-name "
        + stackName
        + " --template-body file://"
        + templateName
    )
    with os.popen(createStackCommand, "r") as f:
        status = json.loads(f.read())
    stackId = status["StackId"]
    print("Creating stack : " + stackId)
    os.system(
        "aws cloudformation wait stack-create-complete --region "
        + awsRegion
        + " --stack-name "
        + stackId
    )
    print("Stack creation complete")


#
# Provide main entry point.
#
def main(params):

    # Perform pre-deployment checks.
    dnsHostedZoneId = checkDnsRegistration(params.domain_name)

    # Create the CloudFormation template.
    resources = {}
    resources.update(createApiCustomDomain(params.domain_name, dnsHostedZoneId))
    templateData = {"Resources": resources}
    template = json.dumps(templateData, indent=4)

    # Create the CloudFormation stack.
    templateName = params.stack_name + "-template.json"
    print("Writing CloudFormation template to " + templateName)
    with open(templateName, "w") as f:
        f.write(template)
    createStack(params.region, params.stack_name, templateName)


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
