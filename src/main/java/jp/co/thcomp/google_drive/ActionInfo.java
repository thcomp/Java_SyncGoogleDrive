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

/**
 * @author h.tatsuguchi.1234@gmail.com
 *
 */
public class ActionInfo {
  public enum ActionType {
    UploadToDrive, DownloadToLocal, UpdateFromLocalToDrive, UpdateFromDriveToLocal,
  }

  private ActionType mActionType;
  private Object mUpdateFrom;
  private Object mUpdateTo;

  public ActionInfo(ActionType actionType, Object updateFrom, Object updateTo) {
    mActionType = actionType;
    mUpdateFrom = updateFrom;
    mUpdateTo = updateTo;
  }

  public ActionType getActionType() {
    return mActionType;
  }

  public Object getUpdateFrom() {
    return mUpdateFrom;
  }

  public Object getUpdateTo() {
    return mUpdateTo;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return "ActionInfo [mActionType=" + mActionType + ", mUpdateFrom=" + mUpdateFrom
        + ", mUpdateTo=" + mUpdateTo + "]";
  }
}
