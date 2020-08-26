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

package com.zynaptic.aws.api.key.capability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * This class provides support for reading API key capability sets from the API
 * key capability database.
 * 
 * @author Chris Holgate
 */
public final class ApiKeyCapabilityReader {
  private final AmazonDynamoDBAsync dynamoDB;
  private final String awsApiKeyTableName;
  private final Map<String, ApiKeyCapabilityParser> capabilityParsers;
  private ApiKeyCapabilityParser defaultCapabilityParser = null;

  /**
   * Creates a new API key capability reader instance using the specified DynamoDB
   * asynchronous interface and DynamoDB table name.
   * 
   * @param dynamoDB This is the DynamoDB asynchronous interface to be used for
   *   database access.
   * @param awsApiKeyTableName This is the name of the DynamoDB table that holds
   *   the API key capability sets.
   */
  public ApiKeyCapabilityReader(AmazonDynamoDBAsync dynamoDB, String awsApiKeyTableName) {
    this.dynamoDB = dynamoDB;
    this.awsApiKeyTableName = awsApiKeyTableName;
    this.capabilityParsers = new HashMap<String, ApiKeyCapabilityParser>();
  }

  /**
   * Sets the default capability parser. If a default capability parser is present
   * all capabilities not handled by the dedicated capability parsers will be
   * passed to the default parser for subsequent processing.
   * 
   * @param capabilityParser This is the new default capability parser that will
   *   be used for processing capabilities that do not have a dedicated parser.
   */
  public void setDefaultCapabilityParser(ApiKeyCapabilityParser capabilityParser) {
    defaultCapabilityParser = capabilityParser;
  }

  /**
   * Adds a new dedicated capability parser to the API key capability reader. Each
   * parser added in this manner will be used to process a single named capability
   * within the overall capability set.
   * 
   * @param capabilityParser This is the new capability parser that will be used
   *   for processing capabilities that match the capability parser name.
   */
  public void addCapabilityParser(ApiKeyCapabilityParser capabilityParser) {
    capabilityParsers.put(capabilityParser.getCapabilityName(), capabilityParser);
  }

  /**
   * Reads the API key capability set for a specific API key from the API key
   * database.
   * 
   * @param apiKey This is the API key for which the corresponding capability set
   *   is to be read from the API key capability database.
   * @return Returns a future API key capability set that is associated with the
   *   specified API key, or a future null reference if a valid key capability set
   *   is not present in the database.
   */
  public Future<ApiKeyCapabilitySet> getApiKeyCapabilitySet(String apiKey) {
    Map<String, AttributeValue> key = new HashMap<String, AttributeValue>(1);
    key.put("apiKey", new AttributeValue(apiKey));
    Future<GetItemResult> futureItemResult = dynamoDB.getItemAsync(awsApiKeyTableName, key);
    return new ApiKeyCapabilitySetFuture(apiKey, futureItemResult);
  }

  /**
   * Performs an asynchronous check for the existence of a given API key
   * capability.
   * 
   * @param apiKey This is the API key for which the capability data is being
   *   requested.
   * @param capabilityName This is the name of the API key capability that is
   *   being checked.
   * @return Returns a future API key capability status object which encapsulates
   *   the capability status information and any associated capability data.
   */
  public Future<ApiKeyCapabilityStatus> getApiKeyCapabilityStatus(String apiKey, String capabilityName) {
    Map<String, AttributeValue> key = new HashMap<String, AttributeValue>(1);
    key.put("apiKey", new AttributeValue(apiKey));
    Future<GetItemResult> futureItemResult = dynamoDB.getItemAsync(awsApiKeyTableName, key);
    return new ApiKeyCapabilityStatusFuture(apiKey, capabilityName, futureItemResult);
  }

  /*
   * Process the asynchronous database access result, converting it into an API
   * key capability set object.
   */
  private ApiKeyCapabilitySet processKeyCapabilitySet(String apiKey, GetItemResult getItemResult) {
    try {
      Map<String, AttributeValue> attributeMap = getItemResult.getItem();

      // Read the expiry timestamp. Throws exception if invalid.
      long expiryTimestamp = Long.parseLong(attributeMap.get("expiryTimestamp").getN());

      // Read the description string if present.
      String description = null;
      AttributeValue descriptionAttr = attributeMap.get("description");
      if (descriptionAttr != null) {
        description = descriptionAttr.getS();
      }

      // Read the authority keys. Return null reference if invalid.
      List<AttributeValue> authorityKeyAttrs = attributeMap.get("authorityKeys").getL();
      if (authorityKeyAttrs == null) {
        return null;
      }
      List<String> authorityKeys = new ArrayList<String>(authorityKeyAttrs.size());
      for (AttributeValue authorityKeyAttr : authorityKeyAttrs) {
        String authorityKey = authorityKeyAttr.getS();
        if (authorityKey == null) {
          return null;
        }
        authorityKeys.add(authorityKey);
      }

      // Parse the nested capabilities. Throws exception if invalid.
      ApiKeyCapabilitySet capabilitySet = new ApiKeyCapabilitySet(apiKey, authorityKeys, expiryTimestamp, description);
      Map<String, AttributeValue> capabilityMap = attributeMap.get("capabilitySet").getM();
      for (Map.Entry<String, AttributeValue> capabilityMapEntry : capabilityMap.entrySet()) {
        ApiKeyCapabilityParser capabilityParser = capabilityParsers.get(capabilityMapEntry.getKey());
        if (capabilityParser != null) {
          ApiKeyCapability capability = capabilityParser.parseCapability(null, capabilityMapEntry.getValue());
          if (capability != null) {
            capabilitySet.addCapability(capability);
          }
        } else if (defaultCapabilityParser != null) {
          ApiKeyCapability capability = defaultCapabilityParser.parseCapability(capabilityMapEntry.getKey(),
              capabilityMapEntry.getValue());
          if (capability != null) {
            capabilitySet.addCapability(capability);
          }
        }
      }
      return capabilitySet;
    } catch (Exception error) {
      return null;
    }
  }

  /*
   * Process the asynchronous capability set access result, converting it into an
   * API key capability status object.
   */
  private ApiKeyCapabilityStatus processKeyCapabilityStatus(ApiKeyCapabilitySet capabilitySet, String capabilityName) {

    // Handle unknown or expired API keys.
    if (capabilitySet == null) {
      return new ApiKeyCapabilityStatus(capabilityName, null, "API authorisation key not found");
    } else if (capabilitySet.grantExpired()) {
      return new ApiKeyCapabilityStatus(capabilityName, null, "API authorisation key grant has expired");
    } else if (!capabilitySet.hasCapability(capabilityName)) {
      return new ApiKeyCapabilityStatus(capabilityName, null,
          "API authorisation key does not have the required capability");
    }

    // Extract the capability data.
    ObjectNode capabilityData = capabilitySet.getCapability(capabilityName).getCapabilityJsonData();
    return new ApiKeyCapabilityStatus(capabilityName, capabilityData, "API authorisation key is valid");
  }

  /*
   * Wraps the DynamoDB future item result with the required code for converting
   * the DynamoDB data to an API key capability set object.
   */
  private final class ApiKeyCapabilitySetFuture implements Future<ApiKeyCapabilitySet> {
    private final String apiKey;
    private final Future<GetItemResult> futureItemResult;

    private ApiKeyCapabilitySetFuture(String apiKey, Future<GetItemResult> futureItemResult) {
      this.apiKey = apiKey;
      this.futureItemResult = futureItemResult;
    }

    /*
     * Delegate to the DynamoDB future item.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return futureItemResult.cancel(mayInterruptIfRunning);
    }

    /*
     * Delegate to the DynamoDB future item.
     */
    @Override
    public boolean isCancelled() {
      return futureItemResult.isCancelled();
    }

    /*
     * Delegate to the DynamoDB future item.
     */
    @Override
    public boolean isDone() {
      return futureItemResult.isDone();
    }

    /*
     * Wait on the DynamoDB future item and then process the result.
     */
    @Override
    public ApiKeyCapabilitySet get() throws InterruptedException, ExecutionException {
      GetItemResult getItemResult = futureItemResult.get();
      return processKeyCapabilitySet(apiKey, getItemResult);
    }

    /*
     * Wait on the DynamoDB future item and then process the result.
     */
    @Override
    public ApiKeyCapabilitySet get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      GetItemResult getItemResult = futureItemResult.get(timeout, unit);
      return processKeyCapabilitySet(apiKey, getItemResult);
    }
  }

  /*
   * Wraps the DynamoDB future item result with the required code for converting
   * the DynamoDB data to an API key capability status object.
   */
  private final class ApiKeyCapabilityStatusFuture implements Future<ApiKeyCapabilityStatus> {
    private final String apiKey;
    private final String capabilityName;
    private final Future<GetItemResult> futureItemResult;

    private ApiKeyCapabilityStatusFuture(String apiKey, String capabilityName, Future<GetItemResult> futureItemResult) {
      this.apiKey = apiKey;
      this.capabilityName = capabilityName;
      this.futureItemResult = futureItemResult;
    }

    /*
     * Delegate to the DynamoDB future item.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return futureItemResult.cancel(mayInterruptIfRunning);
    }

    /*
     * Delegate to the DynamoDB future item.
     */
    @Override
    public boolean isCancelled() {
      return futureItemResult.isCancelled();
    }

    /*
     * Delegate to the DynamoDB future item.
     */
    @Override
    public boolean isDone() {
      return futureItemResult.isDone();
    }

    /*
     * Wait on the DynamoDB future item and then process the result.
     */
    @Override
    public ApiKeyCapabilityStatus get() throws InterruptedException, ExecutionException {
      GetItemResult getItemResult = futureItemResult.get();
      ApiKeyCapabilitySet capabilitySet = processKeyCapabilitySet(apiKey, getItemResult);
      return processKeyCapabilityStatus(capabilitySet, capabilityName);
    }

    /*
     * Wait on the DynamoDB future item and then process the result.
     */
    @Override
    public ApiKeyCapabilityStatus get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      GetItemResult getItemResult = futureItemResult.get(timeout, unit);
      ApiKeyCapabilitySet capabilitySet = processKeyCapabilitySet(apiKey, getItemResult);
      return processKeyCapabilityStatus(capabilitySet, capabilityName);
    }
  }
}
