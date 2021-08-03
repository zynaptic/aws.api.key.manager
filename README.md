# A Capability Based API Key Management Service

This repository provides a capability based API key management service
utilising AWS Lambda and AWS DynamoDB. Key management is carried out
via an Internet facing REST API, which may also be used for key
capability checking. For AWS hosted services, key capability checking
may also be carried out by directly accessing the DynamoDB table that
is used for key storage.

## Service Deployment

Service deployment is carried out using a set of locally executed
Python scripts, which generate the required AWS CloudFormation
templates and then deploy them to AWS using the AWS CLI tool in
conjunction with the current AWS user configuration and credentials.
The AWS `configure` command may be used to check for a valid
configuration as follows:

    $ aws configure list

Configuring the AWS CLI tool prior to use is described in more detail
in the [official documentation](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-configure.html).

### Configuring Default Options

Prior to running the setup scripts, it is possible to change a number of
the default configuration options by editing the `configuration.py` file
in the `tools` directory. The various options are documented in more
detail in the configuration file itself.

### Running DNS Setup

By default, the API key management service will be deployed using the
HTTP endpoint that maps directly to the AWS API gateway service. This
will be a fairly obscure HTTP URL in the standard AWS DNS domain that
will change with successive service deployments. While this may be
adequate for development use, for production deployment a fixed URL on
an independent DNS domain is recommended.

The DNS setup process is implemented independently from the main service
setup, since multiple additional AWS API gateway services may share the
same DNS configuration. In this case, the DNS setup process only needs
to be carried out once in order to support all the associated AWS API
gateway services.

To configure the API key management service for use with an independent
DNS domain, the required domain name should already have been registered
as a hosted zone using the Amazon Route53 service. Further information
about registering a DNS domain using Amazon Route53 is provided in the
[official documentation](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/Welcome.html).

Once an independent DNS domain (such as 'example.com') has been
registered with Amazon Route53, a DNS entry for the required API
endpoint needs to be added to the routing table. This can be carried out
automatically using the `aws-dns-setup.py` script in the `tools`
directory. The script also generates the certificates required to
support HTTPS access to the API endpoint. The DNS setup script takes
the following arguments:

* `--region` This is the AWS region to which the DNS configuration is to
    be deployed. It defaults to the region specified in the local AWS
    CLI configuration file.
* `--domain_name` This is the DNS sub-domain that is to be configured
    for use as the API endpoint. For example 'api.example.com'.
* `--deployment_stage` This is the deployment staging name to use. For
    production use this should be set to 'production'. The default
    value is 'testing'.
* `--stack_name` This is the base name of the CloudFormation stack to
    be used during deployment. The default value is set in the
    `configuration.py` file.

The script supports multiple deployment stages for development use.
Instead of 'production', an arbitrary deployment staging name may be
used. In this case the the API endpoint is changed to reflect the
staging name, so a 'testing' deployment would be deployed to
'testing.api.example.com' instead of 'api.example.com'. The stack name
and stack resource names are also modified when a non-production staging
is used in order to avoid resource naming conflicts.

### Running Service Deployment

The API key management service may be deployed by using the
`aws-deploy.py` script in the `tools` directory. This generates the
required CloudFormation template and deploys it to AWS. The key
management setup script takes the following arguments:

* `--region` This is the AWS region to which the API key management
    service is to be deployed. It defaults to the region specified in
    the local AWS CLI configuration file.
* `--domain_name` This is the DNS sub-domain that was previously
    configured using the DNS setup script. The default value is 'None'
    which implies that a dedicated DNS domain is not being used.
* `--deployment_stage` This is the deployment staging name to use. For
    production use this should be set to 'production'. The default
    value is set to 'testing'.
* `--stack_name` This is the base name of the CloudFormation stack to
    be used during deployment. The default value is set in the
    `configuration.py` file.
* `--deployment_bucket` This is the AWS S3 bucket that is used as interim
    storage for the generated CloudFormation template. The default value
    is set in the `configuration.py` file.
* `--database_name` This is the base name of the DynamoDB table that is to
    be used for storing the API keys. The default value is set in the
    `configuration.py` file.
* `--capability_file` This is an optional JSON file that contains the
    set of capabilities that should be assigned to the API root key.
    If omitted, the API root key is assigned default API key generation
    rights.
* `--api_gateway_name` This is the default base name for the AWS API
    gateway service. The default value is 'ApiKeyManager'.

On successful completion, the setup script should report the supported
API endpoints and the root API key that may be used for generating
additional application specific API keys. A typical example of the
script output is shown below for a 'testing' deployment to the
api.example.com subdomain.

    --------------------------------------------------------------------------------

    API key create URL    : https://****.amazonaws.com/testing/Create
    API key access URL    : https://****.amazonaws.com/testing/Keys/{apiKey}
    Public key create URL : https://testing.api.example.com/ApiKeyManager/Create
    Public key access URL : https://testing.api.example.com/ApiKeyManager/Keys/{apiKey}

    API key management root key: Sf6g....Vrlf

    --------------------------------------------------------------------------------

    Reading key from https://****.amazonaws.com/testing/Keys/Sf6g....Vrlf
    {
      "expiryDate": "9999-12-31T00:00:00Z",
      "description": "API key manager root capability set",
      "capabilitySet": {
        "com.zynaptic.aws.api.key.read": {},
        "com.zynaptic.aws.api.key.renew": {},
        "com.zynaptic.aws.api.key.create": {
            "capabilityLock": false
        },
        "com.zynaptic.aws.api.key.delete": {}
      }
    }
    Reading key from https://testing.api.example.com/ApiKeyManager/Keys/Sf6g....Vrlf
    {
      "expiryDate": "9999-12-31T00:00:00Z",
      "description": "API key manager root capability set",
      "capabilitySet": {
        "com.zynaptic.aws.api.key.read": {},
        "com.zynaptic.aws.api.key.renew": {},
        "com.zynaptic.aws.api.key.create": {
            "capabilityLock": false
        },
        "com.zynaptic.aws.api.key.delete": {}
      }
    }

    --------------------------------------------------------------------------------

## API Key Storage Format

The API key management service provides a mechanism for mapping unique
API keys to associated sets of API capabilities. This mapping is stored
in a DynamoDB table that is indexed by the API key values and which has
the following general format:

    {
      "apiKey": "fZ6U...MB8L",
      "authorityKeys": [
        "Sf6g....Vrlf", "xpzc....8LpM"
      ],
      "capabilitySet": {
        "com.example.service.foo": {
          "fooData": "someData"
        },
        "com.example.service.bar": {
          "barData": 123
        }
      },
      "description": "An example capability set",
      "expiryTimestamp": 253401234500,
      "removalTimestamp": 253406789000
    }

The `apiKey` field is the unique API key that is used as the table
index. It is a Base64-URL encoded string generated from a sequence of
randomly generated bytes, using the Java `SecureRandom` class as the
random number source.

The `authorityKeys` list restricts the set of API keys that may be
used to access the API key record. It starts with the root key that
was generated during service setup and ends with the API key that was
used to create the API key record. Any intermediate keys define the
hierarchical path of API key creation between the two.

The `expiryTimestamp` field specifies the number of seconds after the
UNIX epoch at which the API key will be treated as invalid. The
`removalTimestamp` field specifies the number of seconds after the UNIX
epoch after which the database entry may be automatically deleted
using the DynamoDB 'time to live' option.

The `capabilitySet` field is a map of capability names to associated
capability data. Capability names are typically specified in reverse
domain name form to avoid potential naming conflicts. The associated
capability data will be application specific and will generally be used
to parameterise the capability for a given API client.

## Key Management API

In most cases, API key management tasks will be carried out using the
Internet facing REST API, rather than direct manipulation of the
DynamoDB table. The RESP API supports the creation, deletion and renewal
of API keys and the ability to read back the associated capability sets.

### The Authority Key Header

Any access to the key management API needs to be authorised by including
a suitable authority key as am HTTP request header. The HTTP header name
is a compile time option that may be set by editing the `ApiConfiguration`
class in the `com.zynaptic.aws.api.key.manager` Java package. The
default header name is `x-zynaptic-api-key`. When accessing an API
key record using the REST API, the operation will only be authorised if
the supplied authority key is in the authority key list of the API key
being accessed or if it is the same as the API key being accessed.

### Creating New API Keys

API key creation is carried out using the REST API endpoint reported
after the service has been deployed. If an independent DNS domain is
being used for production staging, this will be of the following
form:

    https://api.example.com/ApiKeyManager/Create

Alternatively, if the AWS resource URL is being used, the REST API
endpoint will have the following form instead:

    https://****.amazonaws.com/production/Create

In order to create a new API key, the requested key capability set needs
to be sent to the key creation endpoint using the HTTP POST method. An
authority key needs to be provided in the authority key header that
supports the `com.zynaptic.aws.api.key.create` capability. If the
`capabilityLock` data item for this capability is absent or set to
`false`, then it is possible to create a new key with any arbitrary
capabilities. On the other hand, if the `capabilityLock` data item is
set to `true`, it will only be possible to create a new key with a set
of capabilities that is a subset of those supported by the authority
key.

When the key creation capability lock is not in effect, any capability
data items specified in the API key creation request will take priority.
Otherwise, if the capability lock is in effect, the capability data
items specified in the authority key will override those in the request.

The request payload is formatted as a JSON object, where the requested
capability set is a nested JSON object named `capabilitySet`. An
additional field named `lifetime` may also be used to specify the
requested lifetime of the API token as an integer number of seconds.
This will be added to the current UNIX epoch time to give the desired
expiry timestamp. If the lifetime is omitted or it exceeds the expiry
timestamp of the authority token, the expiry timestamp of the newly
generated token will be taken from the authority token. Finally, a
`description` field may be included in order to provide a human readable
description of the requested API token. A typical API key request
would be as follows:

    {
      "capabilitySet": {
        "com.example.service.foo": {
          "fooData": "someData"
        },
        "com.example.service.bar": {
          "barData": 123
        }
      },
      "description": "An example capability set",
      "lifetime": 123456
    }

For testing purposes, API key create requests can be generated using the
`curl` command line tool as follows:

    echo '{"lifetime":300,"capabilitySet":{"com.example.service.foo":{}}}' | \
        curl -H x-zynaptic-api-key:Sf6g....Vrlf -v -d @- \
        https://api.example.com/ApiKeyManager/Create

On successful completion, the API endpoint will return the HTTP OK
status (200) and a JSON object as the response payload body. The payload
will contain a status response message and a newly generated API key
using the following format:

    {
      "message": "Created new API key",
      "apiKey": "X6Zv....FWg1"
    }

### Reading API Key Capabilities

The capabilities associated with a given API key can be read back from
the key specific REST API endpoint. If an independent DNS domain is
being used for production staging, this will be of the following
form:

    https://api.example.com/ApiKeyManager/Keys/{apiKey}

Alternatively, if the AWS resource URL is being used, the REST API
endpoint will have the following form instead:

    https://****.amazonaws.com/production/Keys/{apiKey}

In order to read the API key capabilities, an HTTP GET request needs to
be issued to the key specific REST API endpoint. An authority key needs
to be provided in the authority key header that supports the
`com.zynaptic.aws.api.key.read` capability. If the requested API key is
known to support this capability, the requested API key may also be used
as the authority key.

For testing purposes, API key read requests can be generated using the
`curl` command line tool as follows:

    curl -v -H x-zynaptic-api-key:Sf6g....Vrlf \
        https://api.example.com/ApiKeyManager/Keys/AmoP....NC0T

On successful completion, the API endpoint will return the HTTP OK
status (200) and a JSON object as the response payload body. The payload
will contain the capability set for the API key, together with the
key expiry date expressed using the standard UTC ISO format. The
description string will also be included if present. The response
payload will have the following overall format:

    {
      "capabilitySet": {
        "com.example.service.foo": {
          "fooData": "someData"
        }
      },
      "description": "An example capability set",
      "expiryDate": "2021-08-03T23:44:34Z"
    }

Note that not all of the capabilities included in the associated
DynamoDB table entry will necessarily be included in the API endpoint
response. Only those capabilities that are also defined for the
authorisation key will be reported. Capabilities that are not defined
for the authorisation key will not be visible in the API request.
Furthermore, an expired API key will always return an empty capability
set.

### Deleting Unused API Keys

If an API key is not longer in use, it may be deleted from the set of
active keys by issuing an HTTP DELETE request to the key specific
API endpoint. The API endpoint will be the same one as previously used
for reading the API key capability set.

When issuing an API key deletion request, the provided authority key
must support the `com.zynaptic.aws.api.key.read` capability. If the
requested API key is known to support this capability, the requested API
key may also be used as the authority key.

For testing purposes, API key deletion requests can be generated using
the `curl` command line tool as follows:

    curl -X DELETE -v -H x-zynaptic-api-key:Sf6g....Vrlf \
        https://api.example.com/ApiKeyManager/Keys/AmoP....NC0T

On successful completion, the API endpoint will return the HTTP OK
status (200) and a JSON object as the response payload body. The payload
will contain a status response message using the following format:

    {
      "message":"Deleted API key"
    }

### Renewing Existing API Keys

In normal use, an API key will be marked as expired after the API key
lifetime requested during key creation has elapsed. An expired key can
no longer be used, as it will always report an empty capability set.
However, the key entry will be retained in the API key table for a
given retention period. The retention period may be configured at
deployment by editing the `configuration.py` file, and is set to 30 days
by default. At some point after the retention period has elapsed, the
expired key entry will be removed from the API key table and will no
longer be accessible.

It is possible to renew existing API keys at any point prior to expiry
and also within the specified retention period. API key renewal allows
a new API key lifetime to be assigned, which resets the expiry and
removal timestamps for the API key. This is achieved by issuing an HTTP
POST request to the key specific API endpoint. The API endpoint will be
the same one as previously used for reading the API key capability set.

The payload data posted to the API endpoint is formatted as a JSON
object with a single `lifetime` entry expressed as an integer number of
seconds. This will be added to the current UNIX epoch time to give the
updated expiry timestamp. If the lifetime exceeds the expiry timestamp
of the authority token, the updated expiry timestamp will be taken from
the authority token instead. A typical key renewal request will have the
following format:

    {
      "lifetime": 123456
    }

For testing purposes, API key renewal requests can be generated using
the `curl` command line tool as follows:

    echo '{"lifetime":300}' | \
        curl -H x-zynaptic-api-key:Sf6g....Vrlf -v -d @- \
        https://api.example.com/ApiKeyManager/Keys/AmoP....NC0T

On successful completion, the API endpoint will return the HTTP OK
status (200) and a JSON object as the response payload body. The payload
will contain a status response message using the following format:

    {
      "message":"Renewed API key until 2021-08-04T00:53:16Z"
    }
