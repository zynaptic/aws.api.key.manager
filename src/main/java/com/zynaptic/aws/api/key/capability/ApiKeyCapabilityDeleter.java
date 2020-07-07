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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.DeleteItemResult;

/**
 * This class provides support for deleting API key capability sets from the API
 * key capability database.
 * 
 * @author Chris Holgate
 */
public class ApiKeyCapabilityDeleter {
  private final AmazonDynamoDBAsync dynamoDB;
  private final String awsApiKeyTableName;

  /**
   * Creates a new API key capability deleter instance using the specified
   * DynamoDB asynchronous interface and DynamoDB table name.
   * 
   * @param dynamoDB This is the DynamoDB asynchronous interface to be used for
   *   database access.
   * @param awsApiKeyTableName This is the AWS DynamoDB table name that identifies
   *   the API key table.
   */
  public ApiKeyCapabilityDeleter(AmazonDynamoDBAsync dynamoDB, String awsApiKeyTableName) {
    this.dynamoDB = dynamoDB;
    this.awsApiKeyTableName = awsApiKeyTableName;
  }

  /**
   * Deletes an API key capability set from the DynamoDB database.
   * 
   * @param apiKey This is the API key for which the associated capability set is
   *   to be removed from the database.
   * @return Returns a future boolean status value that indicates success or
   *   failure.
   */
  public Future<Boolean> deleteApiKeyCapabilitySet(String apiKey) {
    Map<String, AttributeValue> key = new HashMap<String, AttributeValue>(1);
    key.put("apiKey", new AttributeValue(apiKey));
    Future<DeleteItemResult> futureItemResult = dynamoDB.deleteItemAsync(awsApiKeyTableName, key);
    return new ApiKeyDeleteStatusFuture(futureItemResult);
  }

  /*
   * Wraps the DynamoDB future item result with the required code for converting
   * the DynamoDB response to a boolean status value.
   */
  private final class ApiKeyDeleteStatusFuture implements Future<Boolean> {
    Future<DeleteItemResult> futureItemResult;

    private ApiKeyDeleteStatusFuture(Future<DeleteItemResult> futureItemResult) {
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
      DeleteItemResult deleteItemResult = futureItemResult.get();
      return processDeleteItemResult(deleteItemResult);
    }

    /*
     * Wait on the DynamoDB future item and then process the result.
     */
    @Override
    public Boolean get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      DeleteItemResult deleteItemResult = futureItemResult.get(timeout, unit);
      return processDeleteItemResult(deleteItemResult);
    }

    /*
     * Process the asynchronous database access result, converting it into a boolean
     * status value.
     */
    private Boolean processDeleteItemResult(DeleteItemResult deleteItemResult) {
      return true;
    }
  }
}
