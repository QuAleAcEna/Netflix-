
package com.mkyong;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;

public class GCSHelper {

  public static Storage storage = StorageOptions.getDefaultInstance().getService();
  private static final String bucketName = "armazenamento-netflix"; // replace with your bucket

  private GCSHelper() {
    // private constructor to prevent instantiation
  }

  public static void upload(String objectName, File file, String contentType) throws IOException {
    BlobId blobId = BlobId.of(bucketName, objectName);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(contentType).build();

    // deeply simpler: read the file bytes directly from the path
    storage.create(blobInfo, java.nio.file.Files.readAllBytes(file.toPath()));
  }

  public static String getPublicUrl(String objectName) {
    return String.format("https://storage.googleapis.com/%s/%s", bucketName, objectName);
  }
}
