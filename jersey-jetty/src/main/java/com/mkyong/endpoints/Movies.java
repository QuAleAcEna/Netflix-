package com.mkyong.endpoints;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.mariadb.Mariadb;
import com.mariadb.Movie;
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
  @Produces(MediaType.APPLICATION_JSON)
  public Response getMovies() {
    List<Movie> list = new ArrayList<>();

    ResultSet result = Mariadb.queryDB("SELECT * FROM MOVIE");
    try {
      while (result.next()) {
        String movieName = result.getString("name");
        list.add(new Movie(result.getInt("id"), movieName, result.getString("description"),
            result.getInt("genre"), result.getInt("year"), String.format("movie/%s", movieName),
            String.format("movie/thumbnails/%s", movieName)));
      }
    } catch (SQLException se) {
      System.out.println("Fetch error");
      return null;
    }
    System.out.println("Fetch success");
    return Response.ok(list).build();
  }

  @GET
  @Path("/test")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response video() {
    File file = new File("./res/videos/popeye/1080.mp4");
    return Response.ok(file, MediaType.APPLICATION_OCTET_STREAM)
        .build();
  }

  @GET
  @Path("/thumbnails/{movieName}")
  @Produces("image/png")
  public Response getThumbnail(@PathParam("movieName") String movieName) {
    String[] arg = { movieName };
    ResultSet result = Mariadb.queryDB("SELECT thumbnailPath FROM MOVIE WHERE name = ?", arg);

    try {
      if (result.next() == false)
        return Response.status(Response.Status.NOT_FOUND).entity("Thumbnail not found").type(MediaType.TEXT_PLAIN)
            .build();
      String thumbnailPath;
      thumbnailPath = result.getString("thumbnailPath");
      File file = new File(String.format("%s/%s.png", thumbnailPath, movieName));
      return Response.ok(file, MediaType.APPLICATION_OCTET_STREAM).build();
    } catch (SQLException e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Server error").build();
    }
  }

  @GET
  @Path("/{videoName}/{resolution}")
  @Produces("video/mp4")
  public Response streamVideo(@PathParam("videoName") String videoName, @PathParam("resolution") int resolution,
      @HeaderParam("Range") String range) {
    if (resolution != 1080 && resolution != 360)
      resolution = 1080;
    String[] arg = { videoName };
    ResultSet result = Mariadb.queryDB("SELECT videoPath FROM MOVIE WHERE name = ?", arg);
    try {
      if (result.next() == false)
        return Response.status(Response.Status.NOT_FOUND).entity("Video not found").type(MediaType.TEXT_PLAIN).build();
      String videoPath;
      videoPath = result.getString("videoPath");
      File videoFile = new File(
          String.format("%s/%s.mp4", videoPath,
              Integer.toString(resolution)));
      return buildStream(videoFile, range);
    } catch (SQLException e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Server error").build();
    }
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

        return Response.ok(stream, "video/mp4")
            .status(Response.Status.OK)
            .header("Accept-Ranges", "bytes")
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
      return Response.ok(mediaStreamer, "video/mp4")
          .status(Response.Status.PARTIAL_CONTENT)
          .header("Accept-Ranges", "bytes")
          .header("Content-Range", responseRange)
          .header(HttpHeaders.CONTENT_LENGTH, mediaStreamer.getLenth())
          .header(HttpHeaders.LAST_MODIFIED, new Date(videoFile.lastModified()))
          .build();
    } catch (IOException e) {
      // e.printStackTrace();
    }
    return null;
  }
}
