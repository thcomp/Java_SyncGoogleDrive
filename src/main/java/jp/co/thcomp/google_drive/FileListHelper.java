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

import com.google.api.client.googleapis.batch.BatchCallback;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.ChildList;
import com.google.api.services.drive.model.ChildReference;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.IOException;
import java.util.List;

/**
 * @author h.tatsuguchi.1234@gmail.com
 *
 */
public class FileListHelper {
  private Drive mDriveApi;
  private String mApiKey;
  private Drive.Files.List mFilesListApi = null;

  public FileListHelper(Drive driveApi, String apiKey) {
    if (driveApi == null) {
      throw new NullPointerException("driveApi == null");
    }
    if (apiKey == null || apiKey.length() == 0) {
      throw new NullPointerException("apiKey == '" + apiKey + "'");
    }

    mDriveApi = driveApi;
    mApiKey = apiKey;
  }

  private Drive.Files.List getFilesListApi() throws IOException {
    if (mFilesListApi == null) {
      mFilesListApi = mDriveApi.files().list().setMaxResults(1000);
    }

    return mFilesListApi;
  }

  public DriveItem getDriveItem(String directory) {
    DriveItem ret = null;

    try {
      Drive.Files.List fileListApi = mDriveApi.files().list();
      fileListApi
          .setQ("mimeType='application/vnd.google-apps.folder' and title='" + directory + "'");

      FileList directoryFileList = fileListApi.execute();
      List<File> itemList = directoryFileList.getItems();
      if (itemList != null && itemList.size() > 0) {
        ret = new DriveItem(itemList.get(0));
        ret = getDriveItem(ret);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    return ret;
  }

  public DriveItem getDriveItem(DriveItem topItem) {
    final DriveItem fRet = topItem;

    try {
      Drive.Children.List childListApi = mDriveApi.children().list(topItem.getId());
      ChildList childList = childListApi.execute();
      List<ChildReference> childRefList = childList.getItems();
      if (childRefList.size() > 0) {
        BatchRequest batchRequest = mDriveApi.batch();
        BatchCallback<File, Void> callback = new BatchCallback<File, Void>() {
          @Override
          public void onSuccess(File t, HttpHeaders responseHeaders) throws IOException {
            fRet.addChild(t);
          }

          @Override
          public void onFailure(Void e, HttpHeaders responseHeaders) throws IOException {
          }
        };

        for (ChildReference childRef : childRefList) {
          Drive.Files.Get getApi = mDriveApi.files().get(childRef.getId());
          batchRequest.queue(getApi.buildHttpRequest(), File.class, Void.class, callback);
        }

        batchRequest.execute();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    return fRet;
  }
}

