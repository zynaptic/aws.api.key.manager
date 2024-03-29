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
import com.amazonaws.services.dynamodbv2.model.AttributeAction;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.AttributeValueUpdate;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.UpdateItemResult;

/**
 * This class provides support for writing API key capability sets to the API
 * key capability database.
 * 
 * @author Chris Holgate
 */
public class ApiKeyCapabilityWriter {
  private final AmazonDynamoDBAsync dynamoDB;
  private final String awsApiKeyTableName;
  private final int awsApiKeyRetentionPeriod;

  /**
   * Creates a new API key capability writer instance using the specified DynamoDB
   * asynchronous interface and DynamoDB table name.
   * 
   * @param dynamoDB This is the DynamoDB asynchronous interface to be used for
   *   database access.
   * @param awsApiKeyTableName This is the AWS DynamoDB table name that identifies
   *   the API key table.
   * @param awsApiKeyRetentionPeriod This is the minimum delay after key expiry
   *   before a key may be removed from the API key table.
   */
  public ApiKeyCapabilityWriter(AmazonDynamoDBAsync dynamoDB, String awsApiKeyTableName, int awsApiKeyRetentionPeriod) {
    this.dynamoDB = dynamoDB;
    this.awsApiKeyTableName = awsApiKeyTableName;
    this.awsApiKeyRetentionPeriod = awsApiKeyRetentionPeriod;
  }

  /**
   * Writes an API key capability set to the API key database.
   * 
   * @param apiKeyCapabilitySet This is the API key capability set that is to be
   *   written to the API key database.
   * @return Returns a future boolean status value that indicates success or
   *   failure.
   */
  public Future<Boolean> writeApiKeyCapabilitySet(ApiKeyCapabilitySet apiKeyCapabilitySet) {

    // Derive the expiry and removal timestamps.
    long expiryTimestamp = apiKeyCapabilitySet.getExpiryTimestamp();
    long removalTimestamp = expiryTimestamp + awsApiKeyRetentionPeriod;

    // Construct the set of DynamoDB attribute values to be used in the write
    // operation.
    Map<String, AttributeValue> writeAttributes = new HashMap<String, AttributeValue>();
    writeAttributes.put("apiKey", new AttributeValue(apiKeyCapabilitySet.getApiKey()));
    writeAttributes.put("expiryTimestamp", new AttributeValue().withN(Long.toString(expiryTimestamp)));
    writeAttributes.put("removalTimestamp", new AttributeValue().withN(Long.toString(removalTimestamp)));
    if (apiKeyCapabilitySet.getDescription() != null) {
      writeAttributes.put("description", new AttributeValue(apiKeyCapabilitySet.getDescription()));
    }

    // Add the list of authority keys.
    List<String> authorityKeys = apiKeyCapabilitySet.getAuthorityKeys();
    List<AttributeValue> authorityKeyAttrs = new ArrayList<AttributeValue>(authorityKeys.size());
    for (String authorityKey : authorityKeys) {
      authorityKeyAttrs.add(new AttributeValue(authorityKey));
    }
    writeAttributes.put("authorityKeys", new AttributeValue().withL(authorityKeyAttrs));

    // Add the capability set to the DynamoDB write attributes.
    Map<String, AttributeValue> capabilities = new HashMap<String, AttributeValue>();
    for (ApiKeyCapability capability : apiKeyCapabilitySet.getAllCapabilities()) {
      capabilities.put(capability.getCapabilityName(), capability.getCapabilityAttrData());
    }
    writeAttributes.put("capabilitySet", new AttributeValue().withM(capabilities));

    // Initiate a DynamoDB write request.
    Future<PutItemResult> futureItemResult = dynamoDB.putItemAsync(awsApiKeyTableName, writeAttributes);
    return new ApiKeyWriteStatusFuture(futureItemResult);
  }

  /**
   * Renews an API key capability set in the API key database by updating the
   * expiry and removal timestamps.
   * 
   * @param apiKey This is the API key for which the expiry and removal timestamps
   *   are to be updated.
   * @return Returns a future boolean status value that indicates success or
   *   failure.
   */
  public Future<Boolean> renewApiKeyCapabilitySet(String apiKey, long expiryTimestamp) {
    long removalTimestamp = expiryTimestamp + awsApiKeyRetentionPeriod;

    // Create the table key.
    Map<String, AttributeValue> keyAttributes = new HashMap<String, AttributeValue>();
    keyAttributes.put("apiKey", new AttributeValue(apiKey));

    // Create the table entry update attributes.
    Map<String, AttributeValueUpdate> updateAttributes = new HashMap<String, AttributeValueUpdate>();
    updateAttributes.put("expiryTimestamp",
        new AttributeValueUpdate(new AttributeValue().withN(Long.toString(expiryTimestamp)), AttributeAction.PUT));
    updateAttributes.put("removalTimestamp",
        new AttributeValueUpdate(new AttributeValue().withN(Long.toString(removalTimestamp)), AttributeAction.PUT));

    // Initiate a DynamoDB update request.
    Future<UpdateItemResult> futureItemResult = dynamoDB.updateItemAsync(awsApiKeyTableName, keyAttributes,
        updateAttributes);
    return new ApiKeyUpdateStatusFuture(futureItemResult);
  }

  /*
   * Wraps the DynamoDB future item write result with the required code for
   * converting the DynamoDB response to a boolean status value.
   */
  private final class ApiKeyWriteStatusFuture implements Future<Boolean> {
    private final Future<PutItemResult> futureItemResult;

    private ApiKeyWriteStatusFuture(Future<PutItemResult> futureItemResult) {
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
    public Boolean get() throws InterruptedException, ExecutionException {
      PutItemResult putItemResult = futureItemResult.get();
      return processPutItemResult(putItemResult);
    }

    /*
     * Wait on the DynamoDB future item and then process the result.
     */
    @Override
    public Boolean get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      PutItemResult putItemResult = futureItemResult.get(timeout, unit);
      return processPutItemResult(putItemResult);
    }

    /*
     * Process the asynchronous database access result, converting it into a boolean
     * status value.
     */
    private Boolean processPutItemResult(PutItemResult putItemResult) {
      return true;
    }
  }

  /*
   * Wraps the DynamoDB future item update result with the required code for
   * converting the DynamoDB response to a boolean status value.
   */
  private final class ApiKeyUpdateStatusFuture implements Future<Boolean> {
    private final Future<UpdateItemResult> futureItemResult;

    private ApiKeyUpdateStatusFuture(Future<UpdateItemResult> futureItemResult) {
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
    public Boolean get() throws InterruptedException, ExecutionException {
      UpdateItemResult updateItemResult = futureItemResult.get();
      return processUpdateItemResult(updateItemResult);
    }

    /*
     * Wait on the DynamoDB future item and then process the result.
     */
    @Override
    public Boolean get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      UpdateItemResult updateItemResult = futureItemResult.get(timeout, unit);
      return processUpdateItemResult(updateItemResult);
    }

    /*
     * Process the asynchronous database access result, converting it into a boolean
     * status value.
     */
    private Boolean processUpdateItemResult(UpdateItemResult updateItemResult) {
      return true;
    }
  }
}
