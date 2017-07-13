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

import com.google.api.services.drive.model.File;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author H_Tatsuguchi@google.com (Your Name Here)
 *
 */
public class DriveItem {
  private ArrayList<DriveItem> mChildDriveItemList = new ArrayList<DriveItem>();
  private HashMap<String, DriveItem> mChildDriveItemIdMap = new HashMap<String, DriveItem>();
  private HashMap<String, DriveItem> mChildDriveItemNameMap = new HashMap<String, DriveItem>();

  private File mFile;
  private boolean mIsFolder = false;

  public DriveItem(File file) {
    if (file == null) {
      throw new NullPointerException("file == null");
    }
    mFile = file;
    if (file.getMimeType().equals("application/vnd.google-apps.folder")) {
      mIsFolder = true;
    }
  }

  public boolean isFolder() {
    return mIsFolder;
  }

  public String getId() {
    return mFile.getId();
  }

  public String getTitle() {
    return mFile.getTitle();
  }

  public long getLastModified() {
    return mFile.getModifiedDate().getValue();
  }

  public void addChild(DriveItem item) {
    synchronized (this) {
      String id = item.mFile.getId();

      if (!mChildDriveItemIdMap.containsKey(item.mFile.getId())) {
        mChildDriveItemIdMap.put(id, item);
        mChildDriveItemNameMap.put(item.mFile.getTitle(), item);
        mChildDriveItemList.add(item);
      }
    }
  }

  public void addChild(File file) {
    synchronized (this) {
      String id = file.getId();

      if (!mChildDriveItemIdMap.containsKey(file.getId())) {
        DriveItem item = new DriveItem(file);
        mChildDriveItemIdMap.put(id, item);
        mChildDriveItemNameMap.put(item.mFile.getTitle(), item);
        mChildDriveItemList.add(item);
      }
    }
  }

  public int getChildCount() {
    return mChildDriveItemList.size();
  }

  public DriveItem getChildAt(int index) {
    return mChildDriveItemList.get(index);
  }

  public DriveItem getChildById(String id) {
    return mChildDriveItemIdMap.get(id);
  }

  public DriveItem getChildByName(String name) {
    return mChildDriveItemNameMap.get(name);
  }
}
