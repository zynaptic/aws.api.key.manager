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

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * This interface defines the methods that must be implemented by all API key
 * capability parsers.
 * 
 * @author Chris Holgate
 */
public interface ApiKeyCapabilityParser {

  /**
   * Accesses the default name associated with the API key capability. This is
   * used to uniquely identify the capability within the API key capability set.
   * 
   * @return Returns the default name of the API key capability.
   */
  public String getCapabilityName();

  /**
   * Processes a JSON node that contains JSON encoded capability data, using it to
   * populate a new API key capability object.
   *
   * @param capabilityName This is the capability name to be assigned to the newly
   *   created API key capability. A null reference may be passed in order to use
   *   the default capability name.
   * @param capabilityData This is the JSON node that contains the JSON encoded
   *   capability data.
   * @return Returns an API key capability object that has been populated with the
   *   contents of the JSON encoded capability data. On failure to correctly
   *   process the capability data a null reference will be returned.
   */
  public ApiKeyCapability parseCapability(String capabilityName, JsonNode capabilityData);

  /**
   * Processes a DynamoDB attribute value that contains encoded capability data,
   * using it to populate a new API key capability object.
   * 
   * @param capabilityName This is the capability name to be assigned to the newly
   *   created API key capability. A null reference may be passed in order to use
   *   the default capability name.
   * @param capabilityData This is the DynamoDB attribute value that contains the
   *   encoded capability data.
   * @return Returns an API key capability object that has been populated with the
   *   contents of the DynamoDB capability data. On failure to correctly process
   *   the capability data a null reference will be returned.
   */
  public ApiKeyCapability parseCapability(String capabilityName, AttributeValue capabilityData);

}
