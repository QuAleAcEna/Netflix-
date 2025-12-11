
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

  static public Response streamVideoWithRange(String objectName, String rangeHeader) throws Exception {
    Blob blob = storage.get(BlobId.of(bucketName, objectName));
    if (blob == null)
      return Response.status(Response.Status.NOT_FOUND).build();

    long blobSize = blob.getSize();
    long start = 0;
    long end = blobSize - 1;

    // parse the Range header
    if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
      String[] parts = rangeHeader.replace("bytes=", "").split("-");
      start = Long.parseLong(parts[0]);
      if (parts.length > 1 && !parts[1].isEmpty()) {
        end = Long.parseLong(parts[1]);
      }
    }

    long contentLength = end - start + 1;
    final long finalStart = start;
    final long finalEnd = end;

    ReadChannel reader = blob.reader();
    reader.seek(finalStart);

    StreamingOutput stream = output -> {
      ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
      long bytesToRead = contentLength;
      while (bytesToRead > 0) {
        buffer.clear();
        int read = reader.read(buffer);
        if (read < 0)
          break;
        buffer.flip();
        if (read > bytesToRead) {
          read = (int) bytesToRead;
        }
        output.write(buffer.array(), 0, read);
        bytesToRead -= read;
      }
      output.flush();
      reader.close();
    };

    return Response.ok(stream, "video/mp4")
        .status(rangeHeader != null ? Response.Status.PARTIAL_CONTENT : Response.Status.OK)
        .header("Accept-Ranges", "bytes")
        .header("Content-Length", contentLength)
        .header("Content-Range", String.format("bytes %d-%d/%d", start, end, blobSize))
        .build();
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
