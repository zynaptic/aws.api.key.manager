/*
 * Capability based API key management service for AWS Lambda with DynamoDB.
 *
 * Copyright (c) 2020, Zynaptic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * Please visit www.zynaptic.com or contact reaction@zynaptic.com if you need
 * additional information or have any questions.
 */

package com.zynaptic.aws.api.key.manager;

import java.util.Map;

import com.amazonaws.regions.Regions;

/**
 * This class encapsulates a range of compile time configurable options. Each of
 * the options may be modified to support custom deployments of the API key
 * manager.
 * 
 * @author Chris Holgate
 */
final class ApiConfiguration {

  /**
   * This is a default option that specifies the path to the API key creation
   * resource on the API endpoint.
   */
  private static final String AWS_API_RESOURCE_KEY_CREATE_PATH = "/ApiKeyManager";

  /**
   * This is a default option that specifies the access path to individual API key
   * resources on the API endpoint. The {apiKey} path element is parameterised
   * using the public facing API key identifier.
   */
  private static final String AWS_API_RESOURCE_KEY_ACCESS_PATH = "/ApiKeyManager/{apiKey}";

  /**
   * This is a compile time configurable option that specifies the CORS origin
   * domain which is authorised to access the API.
   */
  private static final String CORS_ORIGIN_DOMAIN = "*";

  /**
   * This is a default option that specifies the AWS region in which the various
   * required AWS infrastructure components are hosted.
   */
  private static final Regions AWS_DEFAULT_HOST_REGION = Regions.EU_WEST_1;

  /**
   * This is a compile time configurable option that specifies the custom header
   * which is used to transfer the API authorisation key. This should be specified
   * using lower case characters only.
   */
  private static final String AWS_API_KEY_HEADER_NAME = "x-zynaptic-api-key";

  /**
   * This is a compile time configurable option that specifies the size of API
   * keys to use, expressed as an integer number of random bytes. Keys are encoded
   * as URL safe Base64 text strings, so key sizes that are divisible by 6 are
   * recommended.
   */
  private static final int AWS_API_KEY_SIZE = 30;

  /**
   * This is a default option that specifies the minimum delay between the expiry
   * of an API key and its removal from the API key table, expressed as an integer
   * number of seconds. The default value corresponds to 30 days.
   */
  private static final int AWS_API_KEY_RETENTION_PERIOD = 2592000;

  /**
   * This is a default option that specifies the name of the DynamoDB table that
   * is used for holding the API key table. The table is used to map API keys onto
   * API capabilities.
   */
  private static final String AWS_API_KEY_TABLE_NAME = "ApiKeyTable";

  /**
   * This is a compile time configurable option that specifies the name of the API
   * key capability which should be used to control key read access to the API.
   */
  private static final String AWS_API_KEY_READ_CAPABILITY_NAME = "com.zynaptic.aws.api.key.read";

  /**
   * This is a compile time configurable option that specifies the name of the API
   * key capability which should be used to control key creation access to the
   * API.
   */
  private static final String AWS_API_KEY_CREATE_CAPABILITY_NAME = "com.zynaptic.aws.api.key.create";

  /**
   * This is a compile time configurable option that specifies the name of the API
   * key capability which should be used to control key deletion access to the
   * API.
   */
  private static final String AWS_API_KEY_DELETE_CAPABILITY_NAME = "com.zynaptic.aws.api.key.delete";

  /**
   * This is a compile time configurable option that specifies the name of the API
   * key capability which should be used to control key renewal access to the API.
   */
  private static final String AWS_API_KEY_RENEWAL_CAPABILITY_NAME = "com.zynaptic.aws.api.key.renew";

  // Specify the configuration options loaded from the execution environment.
  private final Regions awsHostRegion;
  private final int awsApiKeyRetentionPeriod;
  private final String awsApiKeyTableName;
  private final String resourceCreatePath;
  private final String resourceAccessPath;

  /**
   * The standard constructor is used to map execution environment variables to
   * configuration options.
   */
  ApiConfiguration() {
    Map<String, String> env = System.getenv();

    // Use the AWS region reported by the environment.
    if (env.containsKey("AWS_REGION")) {
      awsHostRegion = Regions.fromName(env.get("AWS_REGION"));
    } else {
      awsHostRegion = AWS_DEFAULT_HOST_REGION;
    }

    // Use the API key retention period specified during deployment.
    Integer requestedRetentionPeriod = null;
    if (env.containsKey("AWS_API_KEY_RETENTION_PERIOD")) {
      try {
        requestedRetentionPeriod = Integer.valueOf(env.get("AWS_API_KEY_RETENTION_PERIOD"));
      } catch (NumberFormatException error) {
        // Reverts to the default value on error.
      }
    }
    if (requestedRetentionPeriod != null) {
      awsApiKeyRetentionPeriod = (requestedRetentionPeriod > 0) ? requestedRetentionPeriod : 0;
    } else {
      awsApiKeyRetentionPeriod = AWS_API_KEY_RETENTION_PERIOD;
    }

    // Use the DynamoDB table name generated during deployment.
    if (env.containsKey("AWS_API_KEY_TABLE_NAME")) {
      awsApiKeyTableName = env.get("AWS_API_KEY_TABLE_NAME");
    } else {
      awsApiKeyTableName = AWS_API_KEY_TABLE_NAME;
    }

    // Use a custom key creation resource path if requested.
    if (env.containsKey("AWS_API_RESOURCE_KEY_CREATE_PATH")) {
      resourceCreatePath = env.get("AWS_API_RESOURCE_KEY_CREATE_PATH");
    } else {
      resourceCreatePath = AWS_API_RESOURCE_KEY_CREATE_PATH;
    }

    // Use a custom key access resource path if requested.
    if (env.containsKey("AWS_API_RESOURCE_KEY_ACCESS_PATH")) {
      resourceAccessPath = env.get("AWS_API_RESOURCE_KEY_ACCESS_PATH");
    } else {
      resourceAccessPath = AWS_API_RESOURCE_KEY_ACCESS_PATH;
    }
  }

  /**
   * Accesses the resource path which corresponds to the API key creation
   * endpoint.
   * 
   * @return Returns the configured API key creation resource path.
   */
  String getResourceCreatePath() {
    return resourceCreatePath;
  }

  /**
   * Accesses the resource path which corresponds to the individual API key access
   * endpoint.
   * 
   * @return Returns the configured API key access resource path.
   */
  String getResourceAccessPath() {
    return resourceAccessPath;
  }

  /**
   * Accesses the CORS origin domain which specifies the permitted origin domain
   * for accessing the API.
   * 
   * @return Returns the permitted CORS origin domain.
   */
  String getCorsOriginDomain() {
    return CORS_ORIGIN_DOMAIN;
  }

  /**
   * Accesses the identifier for the AWS region that is used to host the required
   * AWS infrastructure components.
   * 
   * @return Returns the AWS region identifier for the required infrastructure
   *   components.
   */
  Regions getAwsHostRegion() {
    return awsHostRegion;
  }

  /**
   * Accesses the API key header name to be used when extracting API keys from API
   * requests.
   * 
   * @return Returns the API key header name as a lower case string.
   */
  String getAwsApiKeyHeaderName() {
    return AWS_API_KEY_HEADER_NAME;
  }

  /**
   * Accesses the API key size to be used when generating new API keys.
   * 
   * @return Returns the number of random bytes to be used when generating new API
   *   keys.
   */
  int getAwsApiKeySize() {
    return AWS_API_KEY_SIZE;
  }

  /**
   * Accesses the API key retention period to be used when performing periodic
   * table cleanup operations.
   * 
   * @return Returns the minimum period for which an expired API key will be
   *   retained in the key table.
   */
  int getAwsApiKeyRetentionPeriod() {
    return awsApiKeyRetentionPeriod;
  }

  /**
   * Accesses the name of the AWS DynamoDB table that is used for mapping API keys
   * to API capabilities.
   * 
   * @return Returns the name of the API key table.
   */
  String getAwsApiKeyTableName() {
    return awsApiKeyTableName;
  }

  /**
   * Accesses the name of the capability that should be used for authorising key
   * read operations. No data items for the capability are currently defined.
   */
  String getAwsApiKeyReadCapabilityName() {
    return AWS_API_KEY_READ_CAPABILITY_NAME;
  }

  /**
   * Accesses the name of the capability that should be used for authorising key
   * creation operations. No data items for the capability are currently defined.
   */
  String getAwsApiKeyCreateCapabilityName() {
    return AWS_API_KEY_CREATE_CAPABILITY_NAME;
  }

  /**
   * Accesses the name of the capability that should be used for authorising key
   * deletion operations. No data items for the capability are currently defined.
   */
  String getAwsApiKeyDeleteCapabilityName() {
    return AWS_API_KEY_DELETE_CAPABILITY_NAME;
  }

  /**
   * Accesses the name of the capability that should be used for authorising key
   * renewal operations. No data items for the capability are currently defined.
   */
  String getAwsApiKeyRenewalCapabilityName() {
    return AWS_API_KEY_RENEWAL_CAPABILITY_NAME;
  }
}
