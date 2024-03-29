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
# This file specifies the deployment configuration options that will be
# used when generating the AWS CloudFormation files.
#

#
# This option sets the default name of the API key capability table. Set
# to 'None' if CloudFormation should generate a unique table name.
#
API_KEY_TABLE_DEFAULT_NAME = "ApiKeyTable"

#
# This option sets the read capacity limit for the API key capability
# table. Reads are required each time an API key is used to authorise
# a transaction. Set to zero to use pay per request billing mode.
#
API_KEY_TABLE_READ_CAPACITY_UNITS = 1

#
# This option sets the write capacity limit for the API key capability
# table. Writes are required each time a new API key is generated. Set
# to zero to used pay per request billing mode.
#
API_KEY_TABLE_WRITE_CAPACITY_UNITS = 1

#
# This option sets the default name of the AWS S3 deployment bucket that
# will be used to store the AWS Lambda function deployment package.
#
AWS_S3_DEFAULT_DEPLOYMENT_BUCKET = "com-zynaptic-aws-deployment"

#
# This option sets the default file name of the AWS Lambda function
# deployment package. This will typically be replaced by the package
# name generated during the Maven build process.
#
AWS_LAMBDA_DEFAULT_PACKAGE_NAME = "aws-api-key-manager-1.0.0.jar"

#
# This option sets the default name of the AWS CloudFormation stack.
#
AWS_CLOUD_FORMATION_DEFAULT_STACK_NAME = "aws-api-key-manager"

#
# This option sets the default name of the AWS CloudFormation DNS
# configuration stack.
#
AWS_CLOUD_FORMATION_DEFAULT_DNS_STACK_NAME = "aws-api-dns-configuration"

#
# This option specifies the base resource path to be used when accessing
# this service via a custom domain.
#
RESOURCE_CUSTOM_DOMAIN_BASE_PATH = "ApiKeyManager"

#
# This option sets the resource path that is used for API key creation.
# It will be combined with the custom domain base path if required
# to give an overall path such as /ApiKeyManager/Create
#
RESOURCE_KEY_CREATE_PATH = "/Create"

#
# This option sets the resource path that is used for API key accesses.
# It will be combined with the custom domain base path if required
# to give an overall path such as /ApiKeyManager/Keys/{apiKey}
#
RESOURCE_KEY_ACCESS_PATH = "/Keys/{apiKey}"

#
# This option specifies the size of generated API keys as an integer
# number of random bytes.
#
API_KEY_GENERATION_SIZE = 30

#
# This option specifies the minimum delay between the expiry of an API
# key and its removal from the API key table, expressed as an integer
# number of seconds. The default value corresponds to 30 days.
#
AWS_API_KEY_RETENTION_PERIOD = 2592000

#
# This option specifies the name of the API key capability which should
# be used to control key read access to the API.
#
API_KEY_READ_CAPABILITY_NAME = "com.zynaptic.aws.api.key.read"

#
# This option specifies the name of the API key capability which should
# be used to control key creation access to the API.
#
API_KEY_CREATE_CAPABILITY_NAME = "com.zynaptic.aws.api.key.create"

#
# This option specifies the name of the API key capability which should
# be used to control key deletion access to the API.
#
API_KEY_DELETE_CAPABILITY_NAME = "com.zynaptic.aws.api.key.delete"

#
# This option specifies the name of the API key capability which should
# be used to control key renewal access to the API.
#
API_KEY_RENEWAL_CAPABILITY_NAME = "com.zynaptic.aws.api.key.renew"
