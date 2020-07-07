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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * This class implements a single API key capability set that is associated with
 * a given API key.
 * 
 * @author Chris Holgate
 */
public class ApiKeyCapabilitySet {

  // Specify the capability set internal parameters.
  private final String apiKey;
  private final String authorityKey;
  private final long expiryTimestamp;
  private final Map<String, ApiKeyCapability> capabilityMap;

  /**
   * Creates a new API key capability set, given the associated API key and
   * corresponding key expiry timestamp.
   * 
   * @param apiKey This is the API key that is associated with the API key
   *   capability set.
   * @param authorityKey This is the API authority key that was originally used to
   *   create the API key and capability set.
   * @param expiryTimestamp This is the API key expiry time, expressed as an
   *   integer number of milliseconds since the UNIX epoch.
   */
  public ApiKeyCapabilitySet(String apiKey, String authorityKey, long expiryTimestamp) {
    this.apiKey = apiKey;
    this.authorityKey = authorityKey;
    this.expiryTimestamp = expiryTimestamp;
    this.capabilityMap = new HashMap<String, ApiKeyCapability>();
  }

  /**
   * Adds a new API key capability to the capability set. Should only be called
   * during initial capability set construction and before any other methods are
   * called.
   * 
   * @param capability This is the new API key capability that is to be added to
   *   the capability set.
   */
  public void addCapability(ApiKeyCapability capability) {
    capabilityMap.put(capability.getCapabilityName(), capability);
  }

  /**
   * Accesses the API key that is associated with the API key capability set.
   * 
   * @return Returns the API key for the API key capability set.
   */
  public String getApiKey() {
    return apiKey;
  }

  /**
   * Accesses the API authority key that was originally used to create the API key
   * and capability set.
   * 
   * @return Returns the authority key for the API key capability set.
   */
  public String getAuthorityKey() {
    return authorityKey;
  }

  /**
   * Accesses the expiry timestamp associated with the API key capability set.
   * 
   * @return Returns the expiry timestanp for the capability set.
   */
  public long getExpiryTimestamp() {
    return expiryTimestamp;
  }

  /**
   * Indicates whether the API key grant has expired. After expiry the API key
   * should no longer be used.
   * 
   * @return Returns a boolean value which will be set to 'true' if the API key
   *   grant has expired and 'false' otherwise.
   */
  public boolean grantExpired() {
    return (System.currentTimeMillis() > expiryTimestamp);
  }

  /**
   * Indicates whether a named capability is present in the API key capability
   * set.
   * 
   * @param capabilityName This is the name of the capability which is to be
   *   checked for membership of the capability set.
   * @return Returns a boolean value that will be set to 'true' if the named
   *   capability is present in the API key capability set and 'false' otherwise.
   */
  public boolean hasCapability(String capabilityName) {
    return capabilityMap.containsKey(capabilityName);
  }

  /**
   * Accesses an individual API key capability from the capability set if present.
   * 
   * @param capabilityName This is the name of the capability which is to be
   *   accessed.
   * @return Returns the capability object which is associated with the specified
   *   capability name, or a null reference if no corresponding capability exists.
   */
  public ApiKeyCapability getCapability(String capabilityName) {
    return capabilityMap.get(capabilityName);
  }

  /**
   * Accesses the full set of capabilities as an unordered collection.
   * 
   * @return Returns the full set of capabilities.
   */
  public Collection<ApiKeyCapability> getAllCapabilities() {
    return capabilityMap.values();
  }
}
