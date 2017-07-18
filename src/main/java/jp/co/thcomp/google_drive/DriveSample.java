/*
 * Copyright (c) 2012 Google Inc.
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

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.common.io.Files;

import jp.co.thcomp.google_drive.ActionInfo.ActionType;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A sample application that runs multiple requests against the Drive API. The requests this sample
 * makes are:
 * <ul>
 * <li>Does a resumable media upload</li>
 * <li>Updates the uploaded file by renaming it</li>
 * <li>Does a resumable media download</li>
 * <li>Does a direct media upload</li>
 * <li>Does a direct media download</li>
 * </ul>
 *
 * @author rmistry@google.com (Ravi Mistry)
 */
public class DriveSample {

  /**
   * Be sure to specify the name of your application. If the application name is {@code null} or
   * blank, the application will log a warning. Suggested format is "MyCompany-ProductName/1.0".
   */
  private static final String APPLICATION_NAME = "aaa";

  private static final String UPLOAD_FILE_PATH =
      "C:\\Users\\H_Tatsuguchi\\Downloads\\S__4980756.jpg";
  private static final String DIR_FOR_DOWNLOADS = "C:\\Users\\H_Tatsuguchi\\Downloads";
  private static final java.io.File UPLOAD_FILE = new java.io.File(UPLOAD_FILE_PATH);

  /** Directory to store user credentials. */
  private static final java.io.File DATA_STORE_DIR =
      new java.io.File(System.getProperty("user.home"), ".store/drive_sample");

  /**
   * Global instance of the {@link DataStoreFactory}. The best practice is to make it a single
   * globally shared instance across your application.
   */
  private static FileDataStoreFactory dataStoreFactory;

  /** Global instance of the HTTP transport. */
  private static HttpTransport httpTransport;

  /** Global instance of the JSON factory. */
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

  /** Global Drive API client. */
  private static Drive drive;

  /** Authorizes the installed application to access user's protected data. */
  private static Credential authorize() throws Exception {
    // load client secrets
    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
        new InputStreamReader(DriveSample.class.getResourceAsStream("/client_secrets.json")));
    if (clientSecrets.getDetails().getClientId().startsWith("Enter")
        || clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
      System.out.println(
          "Enter Client ID and Secret from https://code.google.com/apis/console/?api=drive "
              + "into drive-cmdline-sample/src/main/resources/client_secrets.json");
      System.exit(1);
    }
    // set up authorization code flow
    GoogleAuthorizationCodeFlow flow =
        new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets,
            DriveScopes.all()).setDataStoreFactory(dataStoreFactory).build();
    // authorize
    return new AuthorizationCodeInstalledApp(flow,
        /* new LocalServerReceiver() */new PseudoVerificationReceiver()).authorize("user");
  }

  public static void main(String[] args) {
    try {
      httpTransport = GoogleNetHttpTransport.newTrustedTransport();
      dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
      // authorization
      Credential credential = authorize();
      // set up the global Drive instance
      drive = new Drive.Builder(httpTransport, JSON_FACTORY, credential)
          .setApplicationName(APPLICATION_NAME).build();

      FileListHelper helper = new FileListHelper(drive, "AIzaSyBhkIrt7Sxf8WulmQysmawOTQnD1TATgZU");
      syncFolder(helper, "" /* TODO */, "Photo");
      return;
    } catch (IOException e) {
      System.err.println(e.getMessage());
    } catch (Throwable t) {
      t.printStackTrace();
    }
    System.exit(1);
  }

  private static void syncFolder(FileListHelper helper, String localFolderPath, DriveItem item) {
    java.io.File localRootFolder = new java.io.File(localFolderPath);

    ArrayList<ActionInfo> actionInfoList = new ArrayList<ActionInfo>();
    boolean ret = isMatch(localRootFolder, item, actionInfoList);

    if (!ret) {
      if (actionInfoList.size() > 0) {
        for (ActionInfo actionInfo : actionInfoList) {
          switch (actionInfo.getActionType()) {
            case UploadToDrive:
              // upload local files to Google Drive
              uploadLocalFilesToGoogleDrive(helper, actionInfo);
              break;
            case DownloadToLocal:
              // download file to local file system at Google Drive
              downloadGoogleDriveFilesToLocalFileSystem(helper, actionInfo);
              break;
            case UpdateFromLocalToDrive:
              // update drive item by local file
              updateFileFromLocalToDrive(helper, actionInfo);
              break;
            case UpdateFromDriveToLocal:
              // update drive item by local file
              updateFileFromDriveToLocal(helper, actionInfo);
              break;
          }
        }
      }
    }
  }

  private static void syncFolder(FileListHelper helper, String localFolderPath,
      String driveItemPath) {
    DriveItem item = helper.getDriveItem(driveItemPath);
    if (item != null) {
      syncFolder(helper, localFolderPath, item);
    }
  }

  private static void uploadLocalFilesToGoogleDrive(FileListHelper helper, ActionInfo actionInfo) {
    java.io.File notExistAtDriveItem = (java.io.File) actionInfo.getUpdateFrom();
    DriveItem parentDriveItem = (DriveItem) actionInfo.getUpdateTo();

    if (notExistAtDriveItem.isDirectory()) {
      File folderMetadata = new File();
      DateTime localFileDateTime = new DateTime(notExistAtDriveItem.lastModified());

      folderMetadata.setTitle(notExistAtDriveItem.getName());
      folderMetadata.setMimeType("application/vnd.google-apps.folder");
      folderMetadata.setCreatedDate(localFileDateTime);
      folderMetadata.setLastViewedByMeDate(localFileDateTime);

      try {
        File folder = drive.files().insert(folderMetadata).setFields("id").execute();
        DriveItem folderDriveItem = new DriveItem(folder);
        parentDriveItem.addChild(folderDriveItem);
        syncFolder(helper, notExistAtDriveItem.getAbsolutePath(), folderDriveItem);
      } catch (IOException exception) {
        exception.printStackTrace();
      }
    } else {
      File fileMetadata = new File();
      DateTime localFileDateTime = new DateTime(notExistAtDriveItem.lastModified());
      fileMetadata.setTitle(notExistAtDriveItem.getName());
      fileMetadata.setCreatedDate(localFileDateTime);
      fileMetadata.setLastViewedByMeDate(localFileDateTime);

      FileContent mediaContent = null;
      String fileExtension = Files.getFileExtension(notExistAtDriveItem.getName());

      switch (fileExtension) {
        case "jpg":
        case "jpeg":
          mediaContent = new FileContent("image/jpeg", notExistAtDriveItem);
          break;

        case "png":
          mediaContent = new FileContent("image/png", notExistAtDriveItem);
          break;

        case "bmp":
          mediaContent = new FileContent("image/bmp", notExistAtDriveItem);
          break;

        case "gif":
          mediaContent = new FileContent("image/gif", notExistAtDriveItem);
          break;
      }

      if (mediaContent != null) {
        Drive.Files.Insert insert;
        try {
          insert = drive.files().insert(fileMetadata, mediaContent);
          MediaHttpUploader uploader = insert.getMediaHttpUploader();
          uploader.setDirectUploadEnabled(true);
          uploader.setProgressListener(new FileUploadProgressListener());
          File insertFile = insert.execute();
          parentDriveItem.addChild(insertFile);
        } catch (IOException exception) {
          // TODO Auto-generated catch block
          exception.printStackTrace();
        }
      }
    }
  }

  private static void downloadGoogleDriveFilesToLocalFileSystem(FileListHelper helper,
      ActionInfo actionInfo) {
    DriveItem notExistAtLocalFile = (DriveItem) actionInfo.getUpdateFrom();
    java.io.File parentLocalFolder = (java.io.File) actionInfo.getUpdateTo();
  }

  private static void updateFileFromLocalToDrive(FileListHelper helper, ActionInfo actionInfo) {
    java.io.File notExistAtRemoteFile = (java.io.File) actionInfo.getUpdateFrom();
    DriveItem parentLocalFolder = (DriveItem) actionInfo.getUpdateTo();
  }

  private static void updateFileFromDriveToLocal(FileListHelper helper, ActionInfo actionInfo) {
    DriveItem notExistAtLocalFile = (DriveItem) actionInfo.getUpdateFrom();
    java.io.File parentLocalFolder = (java.io.File) actionInfo.getUpdateTo();
  }

  private static boolean isMatch(java.io.File localFolder, DriveItem driveFolder,
      List<ActionInfo> actionInfoList) {

    boolean ret = false;

    if (localFolder.lastModified() == driveFolder.getLastModified()) {
      ret = true;
    } else {
      java.io.File[] childFileArray = localFolder.listFiles();
      HashMap<String, java.io.File> localChildFileMapByName = new HashMap<String, java.io.File>();

      for (java.io.File childFile : childFileArray) {
        String childName = childFile.getName();
        localChildFileMapByName.put(childName, childFile);

        if (!childName.startsWith(".")) {
          DriveItem childDriveItem = driveFolder.getChildByName(childName);

          if (childDriveItem == null) {
            actionInfoList.add(new ActionInfo(ActionType.UploadToDrive, childFile, driveFolder));
          } else {
            // 最終更新日が新しい方が真として、それをリストに追加
            if (childDriveItem.getLastModified() > childFile.lastModified()) {
              actionInfoList.add(
                  new ActionInfo(ActionType.UpdateFromDriveToLocal, childDriveItem, childFile));
            } else if (childDriveItem.getLastModified() < childFile.lastModified()) {
              actionInfoList.add(
                  new ActionInfo(ActionType.UpdateFromLocalToDrive, childFile, childDriveItem));
            } else {
              // 最終更新日が等しい場合は一致と判断
            }
          }
        }
      }

      for (int i = 0, size = driveFolder.getChildCount(); i < size; i++) {
        DriveItem childDriveItem = driveFolder.getChildAt(i);
        java.io.File childLocalFile = localChildFileMapByName.get(childDriveItem.getTitle());

        if (childLocalFile == null) {
          actionInfoList
              .add(new ActionInfo(ActionType.DownloadToLocal, childDriveItem, localFolder));
        } else {
          // 最終更新日が新しい方が真として、それをリストに追加
          if (childDriveItem.getLastModified() > childLocalFile.lastModified()) {
            actionInfoList.add(
                new ActionInfo(ActionType.UpdateFromDriveToLocal, childDriveItem, childLocalFile));
          } else if (childDriveItem.getLastModified() < childLocalFile.lastModified()) {
            actionInfoList.add(
                new ActionInfo(ActionType.UpdateFromLocalToDrive, childLocalFile, childDriveItem));
          } else {
            // 最終更新日が等しい場合は一致と判断
          }
        }
      }
    }

    if (!ret) {
      ret = actionInfoList.size() == 0;
    }

    return ret;
  }

  /** Uploads a file using either resumable or direct media upload. */
  private static File uploadFile(boolean useDirectUpload) throws IOException {
    File fileMetadata = new File();
    fileMetadata.setTitle(UPLOAD_FILE.getName());

    FileContent mediaContent = new FileContent("image/jpeg", UPLOAD_FILE);

    Drive.Files.Insert insert = drive.files().insert(fileMetadata, mediaContent);
    MediaHttpUploader uploader = insert.getMediaHttpUploader();
    uploader.setDirectUploadEnabled(useDirectUpload);
    uploader.setProgressListener(new FileUploadProgressListener());
    return insert.execute();
  }

  /** Updates the name of the uploaded file to have a "drivetest-" prefix. */
  private static File updateFileWithTestSuffix(String id) throws IOException {
    File fileMetadata = new File();
    fileMetadata.setTitle("drivetest-" + UPLOAD_FILE.getName());

    Drive.Files.Update update = drive.files().update(id, fileMetadata);
    return update.execute();
  }

  /** Downloads a file using either resumable or direct media download. */
  private static void downloadFile(boolean useDirectDownload, File uploadedFile)
      throws IOException {
    // create parent directory (if necessary)
    java.io.File parentDir = new java.io.File(DIR_FOR_DOWNLOADS);
    if (!parentDir.exists() && !parentDir.mkdirs()) {
      throw new IOException("Unable to create parent directory");
    }
    OutputStream out = new FileOutputStream(new java.io.File(parentDir, uploadedFile.getTitle()));

    MediaHttpDownloader downloader =
        new MediaHttpDownloader(httpTransport, drive.getRequestFactory().getInitializer());
    downloader.setDirectDownloadEnabled(useDirectDownload);
    downloader.setProgressListener(new FileDownloadProgressListener());
    downloader.download(new GenericUrl(uploadedFile.getDownloadUrl()), out);
  }
}
