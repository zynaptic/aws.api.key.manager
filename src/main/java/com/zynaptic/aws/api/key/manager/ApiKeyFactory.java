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

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;

/**
 * This class provides support for generating new API key values. Where possible
 * a strong random number generator is used to generate the API keys.
 * 
 * @author Chris Holgate
 */
final class ApiKeyFactory {
  private final Random randomSource;
  private final int apiKeySize;
  private final Base64.Encoder encoder;

  /**
   * Creates a new API key factory instance with the specified API key size.
   * 
   * @param apiKeySize This is the size of the generated API keys, expressed as an
   *   integer number of bytes.
   */
  ApiKeyFactory(int apiKeySize) {
    Random newRandomSource = null;
    try {
      newRandomSource = SecureRandom.getInstanceStrong();
    } catch (NoSuchAlgorithmException err1) {
      try {
        newRandomSource = SecureRandom.getInstance("SHA1PRNG");
      } catch (NoSuchAlgorithmException err2) {
        newRandomSource = new Random();
        System.out.println("WARNING: No secure random number source available. Using Java Random.");
      }
    }
    this.randomSource = newRandomSource;
    this.apiKeySize = apiKeySize;
    this.encoder = Base64.getUrlEncoder();
  }

  /**
   * Creates a new API key as a URL safe Base64 encoding of a randomly generated
   * byte array.
   * 
   * @return Returns a string containing a URL safe randomly generated API key.
   */
  String createApiKey() {
    byte[] randomBytes = new byte[apiKeySize];
    randomSource.nextBytes(randomBytes);
    return encoder.encodeToString(randomBytes);
  }
}
