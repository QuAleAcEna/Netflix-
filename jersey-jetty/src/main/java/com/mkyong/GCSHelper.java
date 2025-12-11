
package com.mkyong;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.mkyong.endpoints.Movies;

import jakarta.ws.rs.core.Response;

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

  static public InputStream getGcsFileStream(String bucketName, String objectName) {
    Storage storage = StorageOptions.getDefaultInstance().getService();
    Blob blob = storage.get(BlobId.of(bucketName, objectName));
    if (blob == null)
      return null;
    return Channels.newInputStream(blob.reader());
  }

  static public InputStream getGcsStreamFromPath(String videoPath) {
    Storage storage = StorageOptions.getDefaultInstance().getService();
    Blob blob = storage.get(BlobId.of(bucketName, videoPath));
    if (blob == null)
      return null;
    return Channels.newInputStream(blob.reader());
  }

  public Response streamVideoFromGCS(String bucketName, String objectName, String range) throws IOException {
    Storage storage = StorageOptions.getDefaultInstance().getService();
    Blob blob = storage.get(bucketName, objectName);
    if (blob == null)
      return Response.status(Response.Status.NOT_FOUND).build();

    InputStream inputStream = Channels.newInputStream(blob.reader());
    File tempFile = File.createTempFile("video", ".mp4");
    Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

    return Movies.buildStream(tempFile, range); // reuse your existing streaming method
  }
}
