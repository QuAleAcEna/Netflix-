package com.mkyong.endpoints;

import com.mariadb.Mariadb;
import com.mariadb.Movie;
import com.mkyong.GCSHelper;
import com.mkyong.MediaStreamer;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Path("/movie")
public class Movies implements endpoint {
  private static final int BUFFER_SIZE = 1024 * 1024; // 1MB

  public static class CreateMovieRequest {
    public String name;
    public String description;
    public Integer genre;
    public Integer year;
    public String videoPath;
    public String thumbnailPath;
  }
  public static class UpdateMovieRequest {
    public String name;
    public String description;
    public Integer genre;
    public String thumbnailPath;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response getMovies() {
    List<Movie> list = new ArrayList<>();

    ResultSet result = Mariadb.queryDB("SELECT * FROM MOVIE");
    try {
      while (result.next()) {
        String movieName = result.getString("name");
        list.add(new Movie(result.getInt("id"), movieName, result.getString("description"),
            result.getInt("genre"), result.getInt("year"), result.getString("videoPath"),
            result.getString("thumbnailPath")));
      }
    } catch (SQLException se) {
      System.out.println("Fetch error");
      return null;
    }
    System.out.println("Fetch success");
    return Response.ok(list).build();
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response createMovie(CreateMovieRequest request) {
    if (request == null || request.name == null || request.videoPath == null || request.thumbnailPath == null) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    String name = request.name.trim();
    if (name.isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    String[] existsArgs = { name };
    ResultSet exists = Mariadb.queryDB("SELECT id FROM MOVIE WHERE name = ?", existsArgs);
    try {
      if (exists != null && exists.next()) {
        System.out.println("Create movie refused: movie already exists -> " + name);
        return Response.status(Response.Status.CONFLICT).entity("Movie already exists").build();
      }
    } catch (SQLException ignored) {
      System.out.println("Create movie failed checking existing for " + name);
    }

    // Normalize local/device paths into service-friendly defaults
    String normalizedVideoPath = request.videoPath;
    if (normalizedVideoPath.startsWith("./") || normalizedVideoPath.startsWith("/data")
        || normalizedVideoPath.startsWith("/storage") || normalizedVideoPath.startsWith("/sdcard")) {
      normalizedVideoPath = String.format("movie/%s", name);
    }
    String normalizedThumbnailPath = request.thumbnailPath;
    if (normalizedThumbnailPath.startsWith("./") || normalizedThumbnailPath.startsWith("/data")
        || normalizedThumbnailPath.startsWith("/storage") || normalizedThumbnailPath.startsWith("/sdcard")) {
      normalizedThumbnailPath = String.format("movie/thumbnails/%s", name);
    }

    String[] args = {
        name,
        request.description != null ? request.description : "",
        request.genre != null ? Integer.toString(request.genre) : null,
        request.year != null ? Integer.toString(request.year) : null,
        normalizedVideoPath,
        normalizedThumbnailPath
    };
    Integer newId = Mariadb.insertAndReturnId(
        "INSERT INTO MOVIE(name,description,genre,year,videoPath,thumbnailPath) VALUES(?,?,?,?,?,?)", args);
    if (newId == null) {
      System.out.println("Create movie failed (DB insert) for " + name);
      return Response.serverError().build();
    }
    System.out.println("Created movie id=" + newId + " name=" + name);
    Movie movie = new Movie(newId, name, request.description != null ? request.description : "",
        request.genre != null ? request.genre : 0,
        request.year != null ? request.year : 0,
        normalizedVideoPath, normalizedThumbnailPath);
    return Response.ok(movie).build();
  }

  @DELETE
  @Path("/{id}")
  public Response deleteMovie(@PathParam("id") int id) {
    String[] args = { Integer.toString(id) };
    ResultSet movie = Mariadb.queryDB("SELECT id, name, videoPath, thumbnailPath FROM MOVIE WHERE id = ?", args);
    try {
      if (movie != null && movie.next()) {
        String videoPath = movie.getString("videoPath");
        String thumbnailPath = movie.getString("thumbnailPath");
        // Attempt to delete associated objects in the bucket
        GCSHelper.deleteObject(thumbnailPath);
        if (videoPath != null) {
          if (videoPath.contains("videos/") && !videoPath.endsWith(".mp4")) {
            // Likely a folder path like videos/<name>; try common resolutions
            String base = videoPath.replaceFirst("https://storage.googleapis.com/[^/]+/", "");
            GCSHelper.deleteObject(base + "/360.mp4");
            GCSHelper.deleteObject(base + "/1080.mp4");
          } else {
            GCSHelper.deleteObject(videoPath);
          }
        }
        Mariadb.execute("DELETE FROM MOVIE WHERE id = ?", args);
        System.out.println("Deleted movie id=" + id);
      } else {
        System.out.println("Delete movie: id not found (id=" + id + ")");
      }
    } catch (SQLException ignored) {
      System.out.println("Delete movie failed for id=" + id);
    }
    return Response.noContent().build();
  }

  @PUT
  @Path("/{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response updateMovie(@PathParam("id") int id, UpdateMovieRequest request) {
    String[] args = { Integer.toString(id) };
    ResultSet existing = Mariadb.queryDB("SELECT * FROM MOVIE WHERE id = ?", args);
    try {
      if (existing == null || !existing.next()) {
        return Response.status(Response.Status.NOT_FOUND).entity("Movie not found").build();
      }
      String currentName = existing.getString("name");
      String currentDescription = existing.getString("description");
      Integer currentGenre = existing.getInt("genre");
      if (existing.wasNull()) currentGenre = null;
      Integer currentYear = existing.getInt("year");
      if (existing.wasNull()) currentYear = 0;
      String currentVideoPath = existing.getString("videoPath");
      String currentThumbnailPath = existing.getString("thumbnailPath");

      if (request == null) {
        return Response.status(Response.Status.BAD_REQUEST).entity("Missing body").build();
      }

      String newName = request.name != null ? request.name.trim() : currentName;
      String newDescription = request.description != null ? request.description.trim() : currentDescription;
      Integer newGenre = request.genre != null ? request.genre : currentGenre;
      String newThumbnailPath = request.thumbnailPath != null ? request.thumbnailPath.trim() : currentThumbnailPath;

      if (newName == null || newName.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST).entity("Name cannot be empty").build();
      }
      if (newThumbnailPath == null || newThumbnailPath.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST).entity("Thumbnail path cannot be empty").build();
      }

      // Normalize paths similar to createMovie
      if (newThumbnailPath.startsWith("./") || newThumbnailPath.startsWith("/data")
          || newThumbnailPath.startsWith("/storage") || newThumbnailPath.startsWith("/sdcard")) {
        newThumbnailPath = String.format("movie/thumbnails/%s", newName);
      }

      String[] updateArgs = {
          newName,
          newDescription != null ? newDescription : "",
          newGenre != null ? Integer.toString(newGenre) : null,
          newThumbnailPath,
          Integer.toString(id)
      };
      Mariadb.execute("UPDATE MOVIE SET name = ?, description = ?, genre = ?, thumbnailPath = ? WHERE id = ?",
          updateArgs);
      Movie movie = new Movie(id, newName, newDescription != null ? newDescription : "",
          newGenre != null ? newGenre : 0,
          currentYear != null ? currentYear : 0, currentVideoPath, newThumbnailPath);
      System.out.println("Updated movie id=" + id + " name=" + newName);
      return Response.ok(movie).build();
    } catch (SQLException e) {
      System.out.println("Update movie failed for id=" + id);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Server error").build();
    }
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
      thumbnailPath = String.format("%s.png", thumbnailPath);
      return Response.seeOther(new java.net.URI(thumbnailPath)).build();
      // File file = new File(String.format("%s/%s.png", thumbnailPath, movieName));
      // return Response.ok(file, MediaType.APPLICATION_OCTET_STREAM).build();
    } catch (SQLException | URISyntaxException e) {
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
    try {
      // Step 1: Get video path from DB
      String[] arg = { videoName };
      ResultSet result = Mariadb.queryDB("SELECT videoPath FROM MOVIE WHERE name = ?", arg);
      if (!result.next()) {
        return Response.status(Response.Status.NOT_FOUND).entity("Video not found").build();
      }
      String videoPath = result.getString("videoPath");
      videoPath = String.format("%s/%d.mp4", videoPath, resolution);
      return GCSHelper.streamFromGcs(videoPath, range);

    } catch (Exception e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Server error").build();
    }
  }

  static public Response buildStream(final File videoFile, final String range) {

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
          .header(HttpHeaders.CONTENT_LENGTH, mediaStreamer.getLength())
          .header(HttpHeaders.LAST_MODIFIED, new Date(videoFile.lastModified()))
          .build();
    } catch (IOException e) {
      // e.printStackTrace();
    }
    return null;
  }
}
