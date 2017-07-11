/*
 * Copyright (c) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package jp.co.thcomp.google_drive;

import com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver;

import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author H_Tatsuguchi@google.com (Your Name Here)
 *
 */
public class PseudoVerificationReceiver implements VerificationCodeReceiver {

  /*
   * (non-Javadoc)
   * 
   * @see
   * com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver#getRedirectUri()
   */
  @Override
  public String getRedirectUri() throws IOException {
    return "urn:ietf:wg:oauth:2.0:oob";
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver#waitForCode()
   */
  @Override
  public String waitForCode() throws IOException {
    System.out.println("please input code from URL>");

    InputStreamReader reader = new InputStreamReader(System.in);
    StringBuilder readBuffer = new StringBuilder();
    while (true) {
      char readChar = (char) reader.read();
      if (readChar != '\n') {
        readBuffer.append(readChar);
      } else {
        break;
      }
    }

    return readBuffer.toString();
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver#stop()
   */
  @Override
  public void stop() throws IOException {
  }

}
