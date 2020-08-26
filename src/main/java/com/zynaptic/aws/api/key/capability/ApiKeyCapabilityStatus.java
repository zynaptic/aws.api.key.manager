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

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * This class implements a single API key capability status record that is
 * associated with a given API key and capability name.
 * 
 * @author Chris Holgate
 */
public final class ApiKeyCapabilityStatus {
  private final String capabilityName;
  private final ObjectNode capabilityData;
  private final String statusMessage;

  /**
   * Creates a new API key capability status instance with the specified JSON
   * capability data and status message.
   * 
   * @param capabilityName This is the name of the capability that is associated
   *   with the capability status instance.
   * @param capabilityData This is a JSON object which contains the data elements
   *   which are associated with the capability, or a null reference if the
   *   capability does not exist.
   * @param statusMessage This is a status message which may be used to report the
   *   capability status to the user.
   */
  ApiKeyCapabilityStatus(String capabilityName, ObjectNode capabilityData, String statusMessage) {
    this.capabilityName = capabilityName;
    this.capabilityData = capabilityData;
    this.statusMessage = statusMessage;
  }

  /**
   * Indicates whether the capability is authorised for the associated API key.
   * 
   * @return Returns a boolean value which will be set to 'true' to indicate that
   *   the capability is authorised and 'false' otherwise.
   */
  public boolean isAuthorised() {
    return (capabilityData != null);
  }

  /**
   * Accesses the name of the capability that is associated with the capability
   * status instance.
   * 
   * @return Returns a string containing the capability name.
   */
  public String getCapabilityName() {
    return capabilityName;
  }

  /**
   * Accesses the capability data that is attached to the named capability for the
   * associated API key.
   * 
   * @return Returns a JSON object node that encapsulates the capability data, or
   *   a null reference if the capability is not authorised.
   */
  public ObjectNode getCapabilityData() {
    return capabilityData;
  }

  /**
   * Accesses the status message that is associated with the capability status
   * instance. This may be used to report the capability status to the user.
   * 
   * @return Returns a string containing the capability status message.
   */
  public String getStatusMessage() {
    return statusMessage;
  }
}
