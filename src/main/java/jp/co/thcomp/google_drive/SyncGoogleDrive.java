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

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SyncGoogleDrive {
  private static final String[] PARAMS =
      {"--apikey", "--local_folder_path", "--google_drive_folder_name"};

  private static final String PARAM_APIKEY = "--apikey";

  private static final String PARAM_TARGET_LOCAL_FOLDER_PATH = "--local_folder_path";

  private static final String PARAM_TARGET_GOOGLE_DRIVE_TOP_FOLDER_NAME =
      "--google_drive_folder_name";

  /**
   * Be sure to specify the name of your application. If the application name is {@code null} or
   * blank, the application will log a warning. Suggested format is "MyCompany-ProductName/1.0".
   */
  private static final String APPLICATION_NAME = SyncGoogleDrive.class.getSimpleName();

  /** Directory to store user credentials. */
  private static final java.io.File DATA_STORE_DIR =
      new java.io.File(System.getProperty("user.home"), ".store/sync_google_drive");

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
        new InputStreamReader(SyncGoogleDrive.class.getResourceAsStream("/client_secrets.json")));
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
    HashMap<String, String> keyValueMap = new HashMap<String, String>();

    if (args != null && args.length > 0) {
      for (String arg : args) {
        String[] argKeyValue = arg.split(arg, 2);

        if (argKeyValue.length > 1) {
          switch (argKeyValue[0]) {
            case PARAM_APIKEY:
            case PARAM_TARGET_LOCAL_FOLDER_PATH:
            case PARAM_TARGET_GOOGLE_DRIVE_TOP_FOLDER_NAME:
              keyValueMap.put(argKeyValue[0], argKeyValue[1]);
              break;
          }
        }
      }
    }

    String apiKey = keyValueMap.get(PARAM_APIKEY);
    String localFolderPath = keyValueMap.get(PARAM_TARGET_LOCAL_FOLDER_PATH);
    String googleDriveTopFolderName = keyValueMap.get(PARAM_TARGET_GOOGLE_DRIVE_TOP_FOLDER_NAME);

    if (!isEmpty(apiKey) && !isEmpty(localFolderPath) && !isEmpty(googleDriveTopFolderName)) {
      try {
        httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
        // authorization
        Credential credential = authorize();
        // set up the global Drive instance
        drive = new Drive.Builder(httpTransport, JSON_FACTORY, credential)
            .setApplicationName(APPLICATION_NAME).build();

        FileListHelper helper = new FileListHelper(drive, apiKey);
        syncFolder(helper, localFolderPath, googleDriveTopFolderName);
        return;
      } catch (IOException e) {
        System.err.println(e.getMessage());
      } catch (Throwable t) {
        t.printStackTrace();
      }
    } else {
      System.out.println("set following parameters:");
      System.out.println(PARAM_APIKEY);
      System.out.println(PARAM_TARGET_LOCAL_FOLDER_PATH);
      System.out.println(PARAM_TARGET_GOOGLE_DRIVE_TOP_FOLDER_NAME);
    }

    System.exit(1);
  }

  private static boolean isEmpty(String text) {
    return text == null || text.length() == 0;
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
              try {
                downloadGoogleDriveFilesToLocalFileSystem(helper, actionInfo);
              } catch (IOException e) {
                e.printStackTrace();
              }
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
          exception.printStackTrace();
        }
      }
    }
  }

  private static void downloadGoogleDriveFilesToLocalFileSystem(FileListHelper helper,
      ActionInfo actionInfo) throws IOException {
    DriveItem notExistAtLocalFile = (DriveItem) actionInfo.getUpdateFrom();
    java.io.File parentLocalFolder = (java.io.File) actionInfo.getUpdateTo();

    if (notExistAtLocalFile.isFolder()) {
      java.io.File localFolder = new java.io.File(parentLocalFolder.getAbsolutePath()
          + java.io.File.pathSeparator + notExistAtLocalFile.getTitle());
      if (!localFolder.exists()) {
        localFolder.mkdirs();
      }

      if (localFolder.exists()) {
        if (notExistAtLocalFile.getChildCount() == 0) {
          // 念のため子要素の取得を試みる
          notExistAtLocalFile = helper.getDriveItem(notExistAtLocalFile);
          int childCount = 0;
          if ((childCount = notExistAtLocalFile.getChildCount()) > 0) {
            boolean errorOccurred = false;
            for (int i = 0; i < childCount; i++) {
              ActionInfo tempActionInfo = new ActionInfo(ActionInfo.ActionType.DownloadToLocal,
                  notExistAtLocalFile.getChildAt(i), localFolder);
              try {
                downloadGoogleDriveFilesToLocalFileSystem(helper, tempActionInfo);
              } catch (IOException e) {
                errorOccurred = true;
              }
            }

            if (!errorOccurred) {
              // 最終更新日をGoogle Driveに合わせる
              localFolder.setLastModified(notExistAtLocalFile.getLastModified());
            } else {
              // エラーがあった場合は次のsync時に更新したいので、更新日を0にして、Google Driveより前の日付にしておく
              localFolder.setLastModified(0);
            }
          }
        }
      }
    } else {
      java.io.File localFile = new java.io.File(parentLocalFolder.getAbsolutePath()
          + java.io.File.pathSeparator + notExistAtLocalFile.getTitle());

      // ローカルへのダウンロードは実施しない
      if (!localFile.exists()) {
        java.io.FileOutputStream localFileStream = null;
        try {
          localFileStream = new java.io.FileOutputStream(localFile);

          MediaHttpDownloader downloader =
              new MediaHttpDownloader(httpTransport, drive.getRequestFactory().getInitializer());
          downloader.setDirectDownloadEnabled(true);
          downloader.setProgressListener(new FileDownloadProgressListener());
          downloader.download(new GenericUrl(notExistAtLocalFile.getDownloadUrl()),
              localFileStream);

          // 最終更新日をGoogle Driveに合わせる
          localFile.setLastModified(notExistAtLocalFile.getLastModified());
        } finally {
          if (localFileStream != null) {
            try {
              localFileStream.close();
            } catch (IOException exception) {
              exception.printStackTrace();
            } finally {
              localFileStream = null;
            }
          }
        }
      } else {
        System.out.println(
            "dont downaload \"" + localFile.getAbsolutePath() + "\", because it already exists");
      }
    }
  }

  private static void updateFileFromLocalToDrive(FileListHelper helper, ActionInfo actionInfo) {
    // TODO
  }

  private static void updateFileFromDriveToLocal(FileListHelper helper, ActionInfo actionInfo) {
    try {
      downloadGoogleDriveFilesToLocalFileSystem(helper, actionInfo);
    } catch (IOException e) {
      e.printStackTrace();
    }
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
}
