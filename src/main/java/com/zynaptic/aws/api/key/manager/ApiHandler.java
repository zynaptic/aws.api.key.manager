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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zynaptic.aws.api.key.capability.ApiKeyBasicCapability;
import com.zynaptic.aws.api.key.capability.ApiKeyBasicCapabilityParser;
import com.zynaptic.aws.api.key.capability.ApiKeyCapability;
import com.zynaptic.aws.api.key.capability.ApiKeyCapabilityDeleter;
import com.zynaptic.aws.api.key.capability.ApiKeyCapabilityParser;
import com.zynaptic.aws.api.key.capability.ApiKeyCapabilityReader;
import com.zynaptic.aws.api.key.capability.ApiKeyCapabilitySet;
import com.zynaptic.aws.api.key.capability.ApiKeyCapabilityWriter;

/**
 * This class implements the main AWS Lambda entry point for the API key
 * management function. It should be used in conjunction with the AWS API
 * gateway service in Lambda proxy mode.
 * 
 * @author Chris Holgate
 */
public class ApiHandler implements RequestStreamHandler {
  private final ApiConfiguration apiConfiguration;
  private final ApiKeyFactory apiKeyFactory;
  private final ApiKeyCapabilityReader apiKeyCapabilityReader;
  private final ApiKeyCapabilityWriter apiKeyCapabilityWriter;
  private final ApiKeyCapabilityDeleter apiKeyCapabilityDeleter;
  private final ApiKeyCapabilityParser defaultCapabilityParser;

  /**
   * Constructs a new API handler instance on an AWS Lambda function cold start.
   */
  public ApiHandler() {
    apiConfiguration = new ApiConfiguration();
    apiKeyFactory = new ApiKeyFactory(apiConfiguration.getAwsApiKeySize());
    AmazonDynamoDBAsync dynamoDB = AmazonDynamoDBAsyncClientBuilder.standard()
        .withRegion(apiConfiguration.getAwsHostRegion()).build();
    apiKeyCapabilityReader = new ApiKeyCapabilityReader(dynamoDB, apiConfiguration.getAwsApiKeyTableName());
    apiKeyCapabilityWriter = new ApiKeyCapabilityWriter(dynamoDB, apiConfiguration.getAwsApiKeyTableName());
    apiKeyCapabilityDeleter = new ApiKeyCapabilityDeleter(dynamoDB, apiConfiguration.getAwsApiKeyTableName());
    defaultCapabilityParser = new ApiKeyBasicCapabilityParser("DEFAULT");

    // Use a basic API key capability parser to wrap the read, write and delete
    // capabilities.
    apiKeyCapabilityReader
        .addCapabilityParser(new ApiKeyBasicCapabilityParser(apiConfiguration.getAwsApiKeyReadCapabilityName()));
    apiKeyCapabilityReader
        .addCapabilityParser(new ApiKeyBasicCapabilityParser(apiConfiguration.getAwsApiKeyCreateCapabilityName()));
    apiKeyCapabilityReader
        .addCapabilityParser(new ApiKeyBasicCapabilityParser(apiConfiguration.getAwsApiKeyDeleteCapabilityName()));
    apiKeyCapabilityReader.setDefaultCapabilityParser(defaultCapabilityParser);
  }

  /**
   * Implements the outer API request handler that processes an inbound API
   * request and then dispatches it to the appropriate request handler.
   */
  @Override
  public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {

    // Read the request input into a single JSON string.
    BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
    StringBuilder request = new StringBuilder();
    String inputLine = in.readLine();
    while (inputLine != null) {
      request.append(inputLine);
      inputLine = in.readLine();
    }
    in.close();

    // Parse the input JSON string. Do not check parsing exceptions since the
    // auto-generated request should always be valid.
    ObjectMapper objectMapper = new ObjectMapper();
    JsonFactory jsonFactory = objectMapper.getFactory();
    JsonParser requestParser = jsonFactory.createParser(request.toString());
    JsonNode jsonRequest = objectMapper.readTree(requestParser);

    // Extract the resource path and method type.
    JsonNode methodNode = jsonRequest.get("httpMethod");
    JsonNode resourceNode = jsonRequest.get("resource");
    if ((methodNode == null) || !(methodNode.isTextual()) || (resourceNode == null) || !(resourceNode.isTextual())) {
      processError(HttpURLConnection.HTTP_BAD_REQUEST, null, "Request method or resource not valid", outputStream);
      return;
    }
    String httpMethod = methodNode.asText();
    String resourcePath = resourceNode.asText();

    // Extract the access key from the path parameters.
    String apiAccessKey = null;
    if (resourcePath.equals(apiConfiguration.getResourceAccessPath())) {
      JsonNode apiAccessKeyNode = null;
      JsonNode pathParametersNode = jsonRequest.get("pathParameters");
      if ((pathParametersNode != null) && (pathParametersNode.isObject())) {
        apiAccessKeyNode = pathParametersNode.get("apiKey");
      }
      if ((apiAccessKeyNode == null) || !(apiAccessKeyNode.isTextual())) {
        processError(HttpURLConnection.HTTP_NOT_FOUND, null, "API access key not valid", outputStream);
        return;
      }
      apiAccessKey = apiAccessKeyNode.asText();
    }

    // Check that the API authorisation key header contains a valid key.
    String apiAuthKey = null;
    JsonNode headersNode = jsonRequest.get("headers");
    if ((headersNode != null) && (headersNode.isObject())) {
      Iterator<Map.Entry<String, JsonNode>> headerEntryIter = headersNode.fields();
      while (headerEntryIter.hasNext()) {
        Map.Entry<String, JsonNode> headerEntry = headerEntryIter.next();
        if (headerEntry.getKey().toLowerCase().equals(apiConfiguration.getAwsApiKeyHeaderName())) {
          if (headerEntry.getValue().isTextual()) {
            apiAuthKey = headerEntry.getValue().asText();
            break;
          }
        }
      }
    }
    if (apiAuthKey == null) {
      processError(HttpURLConnection.HTTP_BAD_REQUEST, null, "API authorisation key header not found", outputStream);
      return;
    }

    // TODO: Perform additional header checks on the request. In particular, ensure
    // that the request comes from the correct client domain for CORS enforcement.

    // Initiate concurrent database accesses.
    Future<ApiKeyCapabilitySet> futureApiAuthKeyCapabilitySet = apiKeyCapabilityReader
        .getApiKeyCapabilitySet(apiAuthKey);
    Future<ApiKeyCapabilitySet> futureApiAccessKeyCapabilitySet = null;
    if ((apiAccessKey != null) && (httpMethod.equals("GET"))) {
      futureApiAccessKeyCapabilitySet = apiKeyCapabilityReader.getApiKeyCapabilitySet(apiAccessKey);
    }

    // Get the capability set object associated with the API authorisation key and
    // check that it has not expired.
    ApiKeyCapabilitySet apiAuthKeyCapabilitySet;
    try {
      apiAuthKeyCapabilitySet = futureApiAuthKeyCapabilitySet.get();
    } catch (Exception error) {
      processError(HttpURLConnection.HTTP_UNAUTHORIZED, null, "API authorisation key access failed", outputStream);
      return;
    }
    if (apiAuthKeyCapabilitySet == null) {
      processError(HttpURLConnection.HTTP_UNAUTHORIZED, null, "API authorisation key not found", outputStream);
      return;
    }
    if (apiAuthKeyCapabilitySet.grantExpired()) {
      processError(HttpURLConnection.HTTP_UNAUTHORIZED, null, "API authorisation key grant has expired", outputStream);
      return;
    }

    // Process API key create requests after checking the authorisation.
    if (resourcePath.equals(apiConfiguration.getResourceCreatePath())) {
      ApiKeyCapability capability = apiAuthKeyCapabilitySet
          .getCapability(apiConfiguration.getAwsApiKeyCreateCapabilityName());
      if (capability == null) {
        processError(HttpURLConnection.HTTP_UNAUTHORIZED, null, "Required API key create capability not found",
            outputStream);
      } else if (httpMethod.equals("OPTIONS")) {
        processSuccess(HttpURLConnection.HTTP_OK, "POST, OPTIONS", null, outputStream);
      } else if (httpMethod.equals("POST")) {
        processCreateRequest(jsonRequest, apiAuthKeyCapabilitySet, outputStream);
      } else {
        processError(HttpURLConnection.HTTP_BAD_METHOD, "POST, OPTIONS", "Unsupported HTTP method: " + httpMethod,
            outputStream);
      }
    }

    // Process API access key requests after checking the authorisation.
    else if (resourcePath.equals(apiConfiguration.getResourceAccessPath())) {
      ApiKeyCapability readCapability = apiAuthKeyCapabilitySet
          .getCapability(apiConfiguration.getAwsApiKeyReadCapabilityName());
      ApiKeyCapability deleteCapability = apiAuthKeyCapabilitySet
          .getCapability(apiConfiguration.getAwsApiKeyDeleteCapabilityName());

      // Indicate the authorised methods based on the available capabilities.
      String authorisedMethods = null;
      if ((readCapability != null) && (deleteCapability != null)) {
        authorisedMethods = "GET, DELETE, OPTIONS";
      } else if (readCapability != null) {
        authorisedMethods = "GET, OPTIONS";
      } else if (deleteCapability != null) {
        authorisedMethods = "DELETE, OPTIONS";
      }
      if (authorisedMethods == null) {
        processError(HttpURLConnection.HTTP_UNAUTHORIZED, null, "Required API key access capability not found",
            outputStream);
      }

      // Handle the response for the authorised methods.
      else if (httpMethod.equals("OPTIONS")) {
        processSuccess(HttpURLConnection.HTTP_OK, authorisedMethods, null, outputStream);
      } else if ((httpMethod.equals("GET")) && (readCapability != null)) {
        processReadRequest(apiAuthKeyCapabilitySet, futureApiAccessKeyCapabilitySet, authorisedMethods, outputStream);
      } else if ((httpMethod.equals("DELETE")) && (deleteCapability != null)) {
        processDeleteRequest(apiAuthKeyCapabilitySet, apiAccessKey, authorisedMethods, outputStream);
      } else {
        processError(HttpURLConnection.HTTP_BAD_METHOD, authorisedMethods, "Unsupported HTTP method: " + httpMethod,
            outputStream);
      }
    }

    // Unknown resource name.
    else {
      processError(HttpURLConnection.HTTP_NOT_FOUND, null, "Gateway resource not found", outputStream);
    }
  }

  /**
   * Implements API key creation. This method is called when all prerequisites for
   * creating a new API key have been met by the outer API request handler.
   * 
   * @param jsonRequest This is the original API request forwarded by the AWS API
   *   gateway.
   * @param apiAuthKeyCapabilitySet This is the authorisation key capability set
   *   that is associated with the API authorisation key passed in the HTTP
   *   request header.
   * @param outputStream This is the AWS Lambda function output stream to which
   *   the API response message should be written.
   */
  private void processCreateRequest(JsonNode jsonRequest, ApiKeyCapabilitySet apiAuthKeyCapabilitySet,
      OutputStream outputStream) {

    // Get the capability lock flag to determine whether the create request is
    // allowed to create new capabilities.
    ApiKeyBasicCapability capability = (ApiKeyBasicCapability) apiAuthKeyCapabilitySet
        .getCapability(apiConfiguration.getAwsApiKeyCreateCapabilityName());
    Boolean capabilityLock = capability.getBooleanData("capabilityLock");
    if (capabilityLock == null) {
      capabilityLock = false;
    }

    // Extract the request body. This includes exception handling for invalid JSON
    // in the request body.
    JsonNode commandNode;
    JsonNode bodyNode = jsonRequest.get("body");
    if ((bodyNode != null) && (bodyNode.isTextual())) {
      ObjectMapper objectMapper = new ObjectMapper();
      JsonFactory jsonFactory = objectMapper.getFactory();
      try {
        JsonParser bodyParser = jsonFactory.createParser(bodyNode.asText());
        commandNode = objectMapper.readTree(bodyParser);
      } catch (Exception error) {
        processError(HttpURLConnection.HTTP_BAD_REQUEST, "POST, OPTIONS", "Invalid JSON in POST request data body",
            outputStream);
        return;
      }
    } else {
      processError(HttpURLConnection.HTTP_BAD_REQUEST, "POST, OPTIONS", "No POST request data body", outputStream);
      return;
    }

    // Derive the key expiry parameter from the request body. Note that the lifetime
    // of the created API key cannot exceed the lifetime of the authorising key.
    if (!(commandNode.isObject())) {
      processError(HttpURLConnection.HTTP_BAD_REQUEST, "POST, OPTIONS", "Invalid JSON in POST request data body",
          outputStream);
      return;
    }
    JsonNode lifetimeNode = commandNode.get("lifetime");
    long expiryTimestamp = 0;
    if (lifetimeNode == null) {
      expiryTimestamp = apiAuthKeyCapabilitySet.getExpiryTimestamp();
    } else if (lifetimeNode.canConvertToLong()) {
      expiryTimestamp = System.currentTimeMillis() + (lifetimeNode.asLong() * 1000L);
      if (expiryTimestamp > apiAuthKeyCapabilitySet.getExpiryTimestamp()) {
        expiryTimestamp = apiAuthKeyCapabilitySet.getExpiryTimestamp();
      }
    } else {
      processError(HttpURLConnection.HTTP_BAD_REQUEST, "POST, OPTIONS",
          "Invalid API key lifetime value in POST request data body", outputStream);
      return;
    }

    // Extract the key description string if present.
    String description = null;
    JsonNode descriptionNode = commandNode.get("description");
    if (descriptionNode != null) {
      description = descriptionNode.asText();
    }

    // Create the authority key list by appending the authority key to its own
    // authority key list.
    List<String> authorityKeys = new ArrayList<String>(apiAuthKeyCapabilitySet.getAuthorityKeys().size() + 1);
    authorityKeys.addAll(apiAuthKeyCapabilitySet.getAuthorityKeys());
    authorityKeys.add(apiAuthKeyCapabilitySet.getApiKey());
    ApiKeyCapabilitySet newCapabilitySet = new ApiKeyCapabilitySet(apiKeyFactory.createApiKey(), authorityKeys,
        expiryTimestamp, description);

    // Insert the requested capabilities into the new capability set. This can only
    // be done if the authorisation key also has the requested capabilities in its
    // capability set.
    JsonNode newCapabilitySetNode = commandNode.get("capabilitySet");
    if ((newCapabilitySetNode == null) || !(newCapabilitySetNode.isObject())) {
      processError(HttpURLConnection.HTTP_BAD_REQUEST, "POST, OPTIONS",
          "Invalid API key capability set in POST request data body", outputStream);
      return;
    }
    Iterator<Map.Entry<String, JsonNode>> newCapabilitySetIter = newCapabilitySetNode.fields();
    while (newCapabilitySetIter.hasNext()) {
      Map.Entry<String, JsonNode> newCapabilitySetEntry = newCapabilitySetIter.next();
      String newCapabilityName = newCapabilitySetEntry.getKey();
      JsonNode newCapabilityData = newCapabilitySetEntry.getValue();
      if ((newCapabilityData == null) || !(newCapabilityData.isObject())) {
        processError(HttpURLConnection.HTTP_BAD_REQUEST, "POST, OPTIONS",
            "Invalid API key capability set entry in POST request data body", outputStream);
        return;
      }

      // If the capability lock is in effect, any capability data held by the
      // authorisation capability will overwrite capability data in the request.
      ApiKeyCapability authCapability = apiAuthKeyCapabilitySet.getCapability(newCapabilityName);
      if (authCapability != null) {
        ApiKeyCapability newCapability = authCapability.getCapabilityParser().parseCapability(newCapabilityName,
            newCapabilityData);
        newCapability.addCapabilityData(authCapability.getCapabilityData(), capabilityLock);
        newCapabilitySet.addCapability(newCapability);
      }

      // If the capability lock is not in effect, new capabilities can be added to the
      // created key. New capabilities created in this manner will always be parsed
      // using the default capability parser.
      else if (!capabilityLock) {
        ApiKeyCapability newCapability = defaultCapabilityParser.parseCapability(newCapabilityName, newCapabilityData);
        newCapabilitySet.addCapability(newCapability);
      }
    }

    // Create a DynamoDB entry for the key capability and insert it into the
    // DynamoDB table.
    try {
      if (!apiKeyCapabilityWriter.writeApiKeyCapabilitySet(newCapabilitySet).get()) {
        processError(HttpURLConnection.HTTP_INTERNAL_ERROR, "POST, OPTIONS",
            "Failed to insert API key into API key database", outputStream);
        return;
      }
    } catch (Exception error) {
      processError(HttpURLConnection.HTTP_INTERNAL_ERROR, "POST, OPTIONS",
          "Failed to insert API key into API key database", outputStream);
      return;
    }

    // Return the generated API key to the caller. This is included in the payload
    // body but is not indicated in the location header.
    String message = "{\"message\":\"Created new API key\",\"apiKey\":\"" + newCapabilitySet.getApiKey() + "\"}";
    processSuccess(HttpURLConnection.HTTP_OK, "POST, OPTIONS", message, outputStream);
  }

  /**
   * Implements API key capability set read accesses. This method is called when
   * all prerequisites for reading the API key capability set have been met by the
   * outer API request handler.
   * 
   * @param apiAuthKeyCapabilitySet This is the authorisation key capability set
   *   that is associated with the API authorisation key passed in the HTTP
   *   request header.
   * @param futureApiReadKeyCapabilitySet This is the future API key capability
   *   set that was returned as a result of the early dispatch of the asynchronous
   *   read request.
   * @param authorisedMethods This is the set of HTTP methods that are authorised,
   *   given the capabilities held by the authorisation key.
   * @param outputStream This is the AWS Lambda function output stream to which
   *   the API response message should be written.
   */
  private void processReadRequest(ApiKeyCapabilitySet apiAuthKeyCapabilitySet,
      Future<ApiKeyCapabilitySet> futureApiReadKeyCapabilitySet, String authorisedMethods, OutputStream outputStream) {

    // Get the capability set object associated with the API read key.
    ApiKeyCapabilitySet apiReadKeyCapabilitySet;
    try {
      apiReadKeyCapabilitySet = futureApiReadKeyCapabilitySet.get();
    } catch (Exception error) {
      processError(HttpURLConnection.HTTP_NOT_FOUND, null, "API read key access failed", outputStream);
      return;
    }
    if (apiReadKeyCapabilitySet == null) {
      processError(HttpURLConnection.HTTP_NOT_FOUND, null, "API read key not found", outputStream);
      return;
    }

    // Format the expiry timestamp for the response message. Convert it to an ISO
    // date format for external use.
    DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
    String expiryDate = formatter.format(Instant.ofEpochMilli(apiReadKeyCapabilitySet.getExpiryTimestamp()));
    JsonNodeFactory nodeFactory = JsonNodeFactory.instance;
    ObjectNode responseNode = nodeFactory.objectNode();
    responseNode.put("expiryDate", expiryDate);

    // Include the text description if required.
    if (apiReadKeyCapabilitySet.getDescription() != null) {
      responseNode.put("description", apiReadKeyCapabilitySet.getDescription());
    }

    // Format the capability set for the response message. Note that only those
    // capabilities that are also present in the authorisation capability set will
    // be reported. Other capabilities will be concealed.
    ObjectNode capabilitySetNode = nodeFactory.objectNode();
    for (ApiKeyCapability capability : apiReadKeyCapabilitySet.getAllCapabilities()) {
      if (apiAuthKeyCapabilitySet.hasCapability(capability.getCapabilityName())) {
        capabilitySetNode.set(capability.getCapabilityName(), capability.getCapabilityJsonData());
      }
    }
    responseNode.set("capabilitySet", capabilitySetNode);
    String responseBody = responseNode.toString();

    // Return the response message.
    processSuccess(HttpURLConnection.HTTP_OK, authorisedMethods, responseBody, outputStream);
  }

  /**
   * Implements API key capability set deletion. This method is called when all
   * prerequisites for deleting the API key capability set have been met by the
   * outer API request handler.
   * 
   * @param apiAuthKeyCapabilitySet This is the authorisation key capability set
   *   that is associated with the API authorisation key passed in the HTTP
   *   request header.
   * @param apiAccessKey This is the API key for which the associated capability
   *   set is to be removed from the API key database.
   * @param authorisedMethods This is the set of HTTP methods that are authorised,
   *   given the capabilities held by the authorisation key.
   * @param outputStream This is the AWS Lambda function output stream to which
   *   the API response message should be written.
   */
  private void processDeleteRequest(ApiKeyCapabilitySet apiAuthKeyCapabilitySet, String apiAccessKey,
      String authorisedMethods, OutputStream outputStream) {

    // TODO: If a key has the create capability we should do a full scan of the key
    // database and also delete any other keys that have it in their authority list.

    // Issue a delete request for the DynamoDB table entry.
    try {
      if (!apiKeyCapabilityDeleter.deleteApiKeyCapabilitySet(apiAccessKey).get()) {
        processError(HttpURLConnection.HTTP_INTERNAL_ERROR, "POST, OPTIONS",
            "Failed to delete API key from API key database", outputStream);
        return;
      }
    } catch (Exception error) {
      processError(HttpURLConnection.HTTP_INTERNAL_ERROR, "POST, OPTIONS",
          "Failed to delete API key from API key database", outputStream);
      return;
    }

    // Return the response message.
    String message = "{\"message\":\"Deleted API key\"}";
    processSuccess(HttpURLConnection.HTTP_OK, authorisedMethods, message, outputStream);
  }

  /**
   * Implements the API success response handler. This returns a successful API
   * response using the specified HTTP response code and message payload.
   * 
   * @param httpStatusCode This is the HTTP response code that should be returned
   *   to the API gateway client.
   * @param allowedMethods This is a list of allowed HTTP methods that are
   *   supported by the API endpoint.
   * @param jsonPayload This is the message payload that is to be returned to the
   *   API gateway client. It should be a valid JSON format string.
   * @param outputStream This is the AWS Lambda function output stream to which
   *   the API response message should be written.
   */
  private void processSuccess(int httpStatusCode, String allowedMethods, String jsonPayload,
      OutputStream outputStream) {

    // Build JSON response.
    JsonNodeFactory nodeFactory = JsonNodeFactory.instance;
    ObjectNode jsonResponse = nodeFactory.objectNode();
    ObjectNode jsonHeaders = nodeFactory.objectNode();
    jsonHeaders.put("Access-Control-Allow-Origin", apiConfiguration.getCorsOriginDomain());
    jsonHeaders.put("Access-Control-Allow-Methods", allowedMethods);
    jsonHeaders.put("Access-Control-Allow-Headers", "Content-Type");
    jsonResponse.put("isBase64Encoded", false);
    jsonResponse.put("statusCode", httpStatusCode);

    // Include empty payload body if no payload is specified and content type header
    // if there is a payload body.
    if (jsonPayload == null) {
      jsonResponse.put("body", "");
    } else {
      jsonResponse.put("body", jsonPayload);
      jsonHeaders.put("Content-Type", "application/json;charset=UTF-8");
    }
    jsonResponse.set("headers", jsonHeaders);

    // Send the response on the output stream.
    try {
      OutputStreamWriter outputWriter = new OutputStreamWriter(new BufferedOutputStream(outputStream),
          StandardCharsets.UTF_8);
      outputWriter.write(jsonResponse.toString());
      outputWriter.close();
    } catch (IOException error) {
      error.printStackTrace();
    }
  }

  /**
   * Implements the API error response handler. This returns a failed API response
   * using the specified HTTP response code and error message.
   * 
   * @param httpErrorCode This is the HTTP response code that should be returned
   *   to the API gateway client.
   * @param allowedMethods This is a list of allowed HTTP methods that are
   *   supported by the API endpoint.
   * @param errorMessage This is the error message that is to be returned to the
   *   API gateway client. It will be wrapped as a JSON object.
   * @param outputStream This is the AWS Lambda function output stream to which
   *   the API response message should be written.
   */
  private void processError(int httpErrorCode, String allowedMethods, String errorMessage, OutputStream outputStream) {
    System.out.println("ERROR: <" + httpErrorCode + "> " + errorMessage);

    // Build JSON error response.
    JsonNodeFactory nodeFactory = JsonNodeFactory.instance;
    ObjectNode jsonResponse = nodeFactory.objectNode();
    ObjectNode jsonHeaders = nodeFactory.objectNode();
    jsonHeaders.put("Access-Control-Allow-Origin", apiConfiguration.getCorsOriginDomain());
    if (allowedMethods != null) {
      jsonHeaders.put("Access-Control-Allow-Methods", allowedMethods);
    }
    jsonHeaders.put("Access-Control-Allow-Headers", "Content-Type");
    jsonHeaders.put("Content-Type", "application/json;charset=UTF-8");
    jsonResponse.put("isBase64Encoded", false);
    jsonResponse.put("statusCode", httpErrorCode);
    jsonResponse.set("headers", jsonHeaders);

    // Include empty payload body if no error is specified.
    if (errorMessage != null) {
      jsonResponse.put("body", "{\"message\":\"" + errorMessage + "\"}");
    } else {
      jsonResponse.put("body", "{}");
    }

    // Send the response on the output stream.
    try {
      OutputStreamWriter outputWriter = new OutputStreamWriter(new BufferedOutputStream(outputStream),
          StandardCharsets.UTF_8);
      outputWriter.write(jsonResponse.toString());
      outputWriter.close();
    } catch (IOException error) {
      error.printStackTrace();
    }
  }
}
