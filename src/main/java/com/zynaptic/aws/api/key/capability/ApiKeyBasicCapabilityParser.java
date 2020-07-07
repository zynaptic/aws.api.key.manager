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
import java.util.Iterator;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * This class provides a basic implementation of the API key capability parser
 * interface. This supports arbitrary capability data structures with text
 * string, boolean value or integer value fields.
 * 
 * @author Chris Holgate
 */
public class ApiKeyBasicCapabilityParser implements ApiKeyCapabilityParser {
  private final String capabilityName;

  /**
   * Creates a new API key basic capability parser given the associated capability
   * name.
   * 
   * @param capabilityName This is the name of the newly created API key basic
   *   capability parser.
   */
  public ApiKeyBasicCapabilityParser(String capabilityName) {
    this.capabilityName = capabilityName;
  }

  /*
   * Accesses the API key basic capability name.
   */
  @Override
  public String getCapabilityName() {
    return capabilityName;
  }

  /*
   * Checks that the supplied capability data corresponds to a JSON object node
   * and then uses it to populate a new API key basic capability object.
   */
  @Override
  public ApiKeyCapability parseCapability(String capabilityName, JsonNode capabilityData) {
    if (capabilityName == null) {
      capabilityName = this.capabilityName;
    }
    if ((capabilityData == null) || !(capabilityData instanceof ObjectNode)) {
      return null;
    } else {
      Map<String, Object> capabilityDataMap = new HashMap<String, Object>();
      Iterator<Map.Entry<String, JsonNode>> jsonNodeIter = capabilityData.fields();
      while (jsonNodeIter.hasNext()) {
        Map.Entry<String, JsonNode> jsonNodeEntry = jsonNodeIter.next();
        String attributeKey = jsonNodeEntry.getKey();
        JsonNode attributeValue = jsonNodeEntry.getValue();
        if (attributeValue != null) {
          if (attributeValue.isTextual()) {
            capabilityDataMap.put(attributeKey, attributeValue.asText());
          } else if (attributeValue.isBoolean()) {
            capabilityDataMap.put(attributeKey, Boolean.valueOf(attributeValue.asBoolean()));
          } else if (attributeValue.isInt()) {
            capabilityDataMap.put(attributeKey, Integer.valueOf(attributeValue.asInt()));
          }
        }
      }
      return new ApiKeyBasicCapability(capabilityName, capabilityDataMap, this);
    }
  }

  /*
   * Checks that the supplied capability data corresponds to a DynamoDB map and
   * then uses it to populate a new API key basic capability object. Note that
   * invalid integer number formats are forced to zero for consistency with the
   * JSON based constructor.
   */
  @Override
  public ApiKeyCapability parseCapability(String capabilityName, AttributeValue capabilityData) {
    if (capabilityName == null) {
      capabilityName = this.capabilityName;
    }
    if ((capabilityData == null) || (capabilityData.getM() == null)) {
      return null;
    } else {
      Map<String, Object> capabilityDataMap = new HashMap<String, Object>();
      Map<String, AttributeValue> attributeMap = capabilityData.getM();
      for (Map.Entry<String, AttributeValue> attributeMapEntry : attributeMap.entrySet()) {
        String attributeKey = attributeMapEntry.getKey();
        AttributeValue attributeValue = attributeMapEntry.getValue();
        if (attributeValue != null) {
          if (attributeValue.getS() != null) {
            capabilityDataMap.put(attributeKey, attributeValue.getS());
          } else if (attributeValue.getBOOL() != null) {
            capabilityDataMap.put(attributeKey, attributeValue.getBOOL());
          } else if (attributeValue.getN() != null) {
            try {
              capabilityDataMap.put(attributeKey, Integer.valueOf(attributeValue.getN()));
            } catch (NumberFormatException error) {
              capabilityDataMap.put(attributeKey, Integer.valueOf(0));
            }
          }
        }
      }
      return new ApiKeyBasicCapability(capabilityName, capabilityDataMap, this);
    }
  }
}
