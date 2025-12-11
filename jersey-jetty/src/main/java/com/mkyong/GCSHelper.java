
package com.mkyong;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.mkyong.endpoints.Movies;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

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
    Blob blob = storage.get(BlobId.of(bucketName, videoPath));
    if (blob == null)
      return null;
    return Channels.newInputStream(blob.reader());
  }

  /**
   * Stream a video from GCS supporting Range requests.
   *
   * @param bucketName  The GCS bucket.
   * @param objectName  The object path in GCS.
   * @param rangeHeader The HTTP Range header (can be null).
   * @return Response with streaming output.
   * @throws Exception on GCS errors.
   */

  public static Response streamFromGcs(String objectName, String rangeHeader) throws Exception {
    objectName = objectName.replaceFirst("https://storage.googleapis.com/" + bucketName + "/", "");
    Blob blob = storage.get(BlobId.of(bucketName, objectName));
    if (blob == null) {
      return Response.status(Response.Status.NOT_FOUND).entity("Video not found in GCS").build();
    }

    long blobSize = blob.getSize();
    long start = 0;
    long end = blobSize - 1;

    // Parse Range header if present
    if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
      String[] parts = rangeHeader.replace("bytes=", "").split("-");
      start = Long.parseLong(parts[0]);
      if (parts.length > 1 && !parts[1].isEmpty()) {
        end = Long.parseLong(parts[1]);
      }
    }

    long contentLength = end - start + 1;

    // Wrap ReadChannel as InputStream
    ReadChannel reader = blob.reader();
    reader.seek(start);
    InputStream inputStream = Channels.newInputStream(reader);

    StreamingOutput stream = output -> {
      byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
      long bytesLeft = contentLength;
      int read;
      while (bytesLeft > 0 && (read = inputStream.read(buffer, 0, (int) Math.min(buffer.length, bytesLeft))) != -1) {
        output.write(buffer, 0, read);
        bytesLeft -= read;
      }
      output.flush();
      inputStream.close();
    };

    return Response.ok(stream, "video/mp4")
        .status(rangeHeader != null ? Response.Status.PARTIAL_CONTENT : Response.Status.OK)
        .header("Accept-Ranges", "bytes")
        .header("Content-Length", contentLength)
        .header("Content-Range", String.format("bytes %d-%d/%d", start, end, blobSize))
        .build();
  }

}
