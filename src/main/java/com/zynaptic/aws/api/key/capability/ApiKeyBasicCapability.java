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

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * This class provides a basic implementation of the API key capability
 * interface. This supports arbitrary capability data structures with text
 * string, boolean value or integer value fields.
 * 
 * @author Chris Holgate
 */
public class ApiKeyBasicCapability implements ApiKeyCapability {
  private final String capabilityName;
  private final Map<String, Object> capabilityDataMap;
  private final ApiKeyBasicCapabilityParser capabilityParser;

  /**
   * Creates a new API key basic capability given the capability name and a JSON
   * object that contains the elements of the arbitrary capability data structure.
   * 
   * @param capabilityName This is the name of the newly created API key basic
   *   capability.
   * @param capabilityData This is the the Java native map which contains the
   *   elements of the arbitrary capability data structure.
   * @param capabilityParser This is a reference to the capability parser that is
   *   creating this capability instance.
   */
  ApiKeyBasicCapability(String capabilityName, Map<String, Object> capabilityData,
      ApiKeyBasicCapabilityParser capabilityParser) {
    this.capabilityName = capabilityName;
    this.capabilityDataMap = capabilityData;
    this.capabilityParser = capabilityParser;
  }

  /*
   * Accesses the API key basic capability name.
   */
  @Override
  public String getCapabilityName() {
    return capabilityName;
  }

  /*
   * Gets the capability parser that created this capability instance.
   */
  @Override
  public ApiKeyCapabilityParser getCapabilityParser() {
    return capabilityParser;
  }

  /*
   * Modifies the capability using the supplied capability data.
   */
  @Override
  public void addCapabilityData(Map<String, Object> capabilityData) {
    for (Map.Entry<String, Object> capabilityDataItem : capabilityData.entrySet()) {
      String dataItemKey = capabilityDataItem.getKey();
      Object dataItemValue = capabilityDataItem.getValue();
      if ((dataItemValue instanceof String) || (dataItemValue instanceof Boolean)
          || (dataItemValue instanceof Integer)) {
        capabilityDataMap.put(dataItemKey, dataItemValue);
      }
    }
  }

  /*
   * Accesses basic capability data elements.
   */
  @Override
  public Map<String, Object> getCapabilityData() {
    return capabilityDataMap;
  }

  /*
   * Accesses the capability data as a set of DynamoDB attribute values.
   */
  @Override
  public AttributeValue getCapabilityAttrData() {
    Map<String, AttributeValue> attrDataItems = new HashMap<String, AttributeValue>();
    for (Map.Entry<String, Object> dataItem : capabilityDataMap.entrySet()) {
      if (dataItem.getValue() instanceof String) {
        attrDataItems.put(dataItem.getKey(), new AttributeValue((String) dataItem.getValue()));
      } else if (dataItem.getValue() instanceof Boolean) {
        attrDataItems.put(dataItem.getKey(), new AttributeValue().withBOOL((Boolean) dataItem.getValue()));
      } else if (dataItem.getValue() instanceof Integer) {
        attrDataItems.put(dataItem.getKey(), new AttributeValue().withN(((Integer) dataItem.getValue()).toString()));
      }
    }
    return new AttributeValue().withM(attrDataItems);
  }

  /*
   * Accesses the capability data as a set of JSON object fields.
   */
  @Override
  public ObjectNode getCapabilityJsonData() {
    JsonNodeFactory nodeFactory = JsonNodeFactory.instance;
    ObjectNode capabilityDataNode = nodeFactory.objectNode();
    for (Map.Entry<String, Object> dataItem : capabilityDataMap.entrySet()) {
      String dataItemName = dataItem.getKey();
      Object dataItemValue = dataItem.getValue();
      if (dataItemValue instanceof String) {
        capabilityDataNode.set(dataItemName, nodeFactory.textNode((String) dataItemValue));
      } else if (dataItemValue instanceof Boolean) {
        capabilityDataNode.set(dataItemName, nodeFactory.booleanNode((Boolean) dataItemValue));
      } else if (dataItemValue instanceof Integer) {
        capabilityDataNode.set(dataItemName, nodeFactory.numberNode((Integer) dataItemValue));
      }
    }
    return capabilityDataNode;
  }

  /**
   * Accesses the capability data item associated with the specified data key
   * after first checking that it is a text data item.
   * 
   * @param dataKey This is the data item key which is used to identify the
   *   capability data item being accessed.
   * @return Returns a string containing the data item that is associated with the
   *   data key, or a null reference if the data key does not correspond to a
   *   valid text data item.
   */
  public String getTextData(String dataKey) {
    Object textNode = capabilityDataMap.get(dataKey);
    if ((textNode == null) || !(textNode instanceof String)) {
      return null;
    } else {
      return (String) textNode;
    }
  }

  /**
   * Accesses the capability data item associated with the specified data key
   * after first checking that it is a boolean data item.
   * 
   * @param dataKey This is the data item key which is used to identify the
   *   capability data item being accessed.
   * @return Returns a boolean value containing the data item that is associated
   *   with the data key, or a null reference if the data key does not correspond
   *   to a valid boolean data item.
   */
  public Boolean getBooleanData(String dataKey) {
    Object booleanNode = capabilityDataMap.get(dataKey);
    if ((booleanNode == null) || !(booleanNode instanceof Boolean)) {
      return null;
    } else {
      return (Boolean) booleanNode;
    }
  }

  /**
   * Accesses the capability data item associated with the specified data key
   * after first checking that it is an integer data item.
   * 
   * @param dataKey This is the data item key which is used to identify the
   *   capability data item being accessed.
   * @return Returns an integer value containing the data item that is associated
   *   with the data key, or a null reference if the data key does not correspond
   *   to a valid integer data item.
   */
  public Integer getIntegerData(String dataKey) {
    Object integerNode = capabilityDataMap.get(dataKey);
    if ((integerNode == null) || !(integerNode instanceof Integer)) {
      return null;
    } else {
      return (Integer) integerNode;
    }
  }
}
