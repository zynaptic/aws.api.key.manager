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

import java.util.Map;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * This interface defines the methods that must be implemented by all API key
 * capability classes.
 * 
 * @author Chris Holgate
 */
public interface ApiKeyCapability {

  /**
   * Accesses the name associated with the API key capability. This is used to
   * uniquely identify the capability within the API key capability set.
   * 
   * @return Returns the name of the API key capability.
   */
  public String getCapabilityName();

  /**
   * Gets a reference to the API capability parser that was used to generate this
   * capability instance.
   * 
   * @return Returns a reference to the API capability parser that generated this
   *   capability instance.
   */
  public ApiKeyCapabilityParser getCapabilityParser();

  /**
   * Modifies the key capability by merging in additional capability data. The
   * exact process will depend on the capability implementation, but generally the
   * data already held by the key will be overwritten by the merged capability
   * data.
   * 
   * @param capabilityData This is the new capability data that is to be merged
   *   with the existing capability data. This should be a map of data element
   *   names to data values of String, Boolean or Integer type.
   */
  public void addCapabilityData(Map<String, Object> capabilityData);

  /**
   * Accesses the data elements associated with the capability as a set of native
   * Java objects.
   * 
   * @return Returns a map containing the named data elements that make up the
   *   capability data.
   */
  public Map<String, Object> getCapabilityData();

  /**
   * Accesses the data elements associated with the capability as a set of
   * DynamoDB attribute values.
   * 
   * @return Returns a DynamoDB map attribute value that encapsulates the
   *   capability data as a set of DynamoDB attributes.
   */
  public AttributeValue getCapabilityAttrData();

  /**
   * Accesses the data elements associated with the capability as a set of JSON
   * object fields.
   * 
   * @return Returns a JSON object node that encapsulates the capability data as a
   *   set of JSON object fields.
   */
  public ObjectNode getCapabilityJsonData();

}
