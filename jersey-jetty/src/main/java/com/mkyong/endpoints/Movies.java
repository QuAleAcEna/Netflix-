package com.mkyong.endpoints;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Date;

import com.mkyong.MediaStreamer;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

@Path("/movie")
public class Movies implements endpoint {
  private static final int BUFFER_SIZE = 1024 * 1024; // 1MB

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public String invalidParam() {
    return "Invalid url";
  }

  @GET
  @Path("/{videoName}/{resolution}")
  @Produces("video/mp4")
  public Response streamVideo(@PathParam("videoName") String videoName, @PathParam("resolution") int resolution,
      @HeaderParam("Range") String range) {
    if (resolution != 1080 || resolution != 360)
      resolution = 1080;
    File videoFile = new File(
        String.format("./res/videos/%s/%s.mp4", videoName, String.valueOf(resolution)));
    return buildStream(videoFile, range);
  }

  private Response buildStream(final File videoFile, final String range) {

    long length = videoFile.length();
    long start = 0;
    long end = length - 1;
    long chunkSize = end - start + 1;
    try {
      if (range == null) {

        InputStream inputStream = new FileInputStream(videoFile);
        StreamingOutput stream = output -> {
          byte[] buffer = new byte[BUFFER_SIZE];
          long bytesRead = 0;
          while (bytesRead < chunkSize) {
            int read = inputStream.read(buffer);
            if (read == -1)
              break;
            output.write(buffer, 0, read);
            bytesRead += read;
          }
          output.flush();
          inputStream.close();
        };

        return Response.ok(stream)
            .status(Response.Status.OK)
            .header(HttpHeaders.CONTENT_LENGTH, videoFile.length())
            .build();
      }
      String[] ranges = range.split("=")[1].split("-");
      int from = Integer.parseInt(ranges[0]);
      long to = chunkSize + from;
      if (to >= videoFile.length()) {
        to = videoFile.length() - 1;
      }
      final String responseRange = String.format("bytes %d-%d/%d", from, to, videoFile.length());

      final RandomAccessFile raf = new RandomAccessFile(videoFile, "r");
      raf.seek(from);
      final long len = to - from + 1;
      final MediaStreamer mediaStreamer = new MediaStreamer((int) len, raf);
      return Response.ok(mediaStreamer)
          .status(Response.Status.PARTIAL_CONTENT)
          .header("Accept-Ranges", "bytes")
          .header("Content-Range", responseRange)
          .header(HttpHeaders.CONTENT_LENGTH, mediaStreamer.getLenth())
          .header(HttpHeaders.LAST_MODIFIED, new Date(videoFile.lastModified()))
          .build();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
}
