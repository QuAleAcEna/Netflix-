package com.mkyong.endpoints;

import com.mariadb.Mariadb;
import com.mkyong.GCSHelper;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

@Path("/file")
public class UploadService implements endpoint {

  @POST
  @Path("/upload")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response uploadFile(
      @FormDataParam("file") InputStream uploadedInputStream,
      @FormDataParam("file") FormDataContentDisposition fileDetail) {
    System.out.println(String.format("Uploading %s", fileDetail.getFileName()));

    String movieName = fileDetail.getFileName().replace(".mp4", "");
    new File(String.format("./temp/", movieName)).mkdirs();

    String uploadedFileLocation = "./temp/" + fileDetail.getFileName();

    writeToFile(uploadedInputStream, uploadedFileLocation);

    new Thread(() -> {

      if (processVideo(uploadedFileLocation, movieName))
        addVideoToDB(movieName);
      else
        System.out.println("Unable to upload");
    }).start();

    return Response.status(200).build();

  }

  @POST
  @Path("/upload-thumbnail")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response uploadThumbnail(
      @FormDataParam("file") InputStream uploadedInputStream,
      @FormDataParam("file") FormDataContentDisposition fileDetail,
      @FormDataParam("movieName") String movieName) {
    if (uploadedInputStream == null || fileDetail == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Missing file").build();
    }
    String originalName = fileDetail.getFileName();
    if (originalName == null || originalName.trim().isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Invalid file name").build();
    }
    try {
      File tempFile = File.createTempFile("thumb_", "_" + originalName);
      Files.copy(uploadedInputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      String safeMovieName = (movieName != null && !movieName.trim().isEmpty())
          ? movieName.trim()
          : tempFile.getName().replace(".png", "").replace(".jpg", "");
      String objectName = "thumbnails/" + safeMovieName + ".png";
      String contentType = Files.probeContentType(tempFile.toPath());
      if (contentType == null || contentType.isEmpty()) {
        contentType = "image/png";
      }
      GCSHelper.upload(objectName, tempFile, contentType);
      String publicUrl = GCSHelper.getPublicUrl(objectName);
      tempFile.delete();
      return Response.ok(publicUrl).type(MediaType.TEXT_PLAIN).build();
    } catch (IOException e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("Failed to upload thumbnail: " + e.getMessage()).build();
    }
  }

  private void addVideoToDB(String movieName) {
      Integer nextId = Mariadb.queryDB("SELECT AUTO_INCREMENT FROM information_schema.TABLES where TABLE_SCHEMA = \"db\" AND TABLE_NAME = \"MOVIE\";").getInt("AUTO_INCREMENT");
    String videoPath = GCSHelper.getPublicUrl(String.format("videos/%d", nextId));
    String thumbnailPath = GCSHelper.getPublicUrl(String.format("thumbnails/%d.png", nextId));
    String[] args = { movieName, videoPath, thumbnailPath, "aaa", "0", "0" };
    if (Mariadb.insert("INSERT INTO MOVIE(name,videoPath,thumbnailPath,description,year,genre) VALUES(?,?,?,?,?,?)",
        args) == false) {
      System.err.println("Unable to insert video to db");
      return;
    }
    System.out.println(String.format("Uploaded %s", movieName));

  }

  private boolean processVideo(String uploadedFileLocation, String movieName) {
    try {
      File videoDir = new File(System.getProperty("java.io.tmpdir"), movieName + "_videos");
      videoDir.mkdirs();

      File thumbFile = new File(videoDir, "img.png");
      File lowResFile = new File(videoDir, "360.mp4");
      File highResFile = new File(videoDir, "1080.mp4");

      executeFFmpegCommand(
          "ffmpeg", "-y", "-ss", "00:00:01", "-i", uploadedFileLocation,
          "-frames", "1", thumbFile.getAbsolutePath());
      System.out.println("Thumbnail generated");

      // Generate 360p video
      executeFFmpegCommand(
          "ffmpeg", "-y",
          "-i", uploadedFileLocation,
          "-vf", "scale=640:360:flags=fast_bilinear",
          "-c:v", "libx264",
          "-preset", "ultrafast",
          "-crf", "28",
          "-c:a", "copy",
          lowResFile.getAbsolutePath());
      // executeFFmpegCommand(
      // "ffmpeg", "-y", "-i", uploadedFileLocation,
      // "-vf", "scale=640:360", "-c:a", "copy",
      // String.format("./res/videos/%s/360.mp4", movieName));
      System.out.println("Low res video generated");

      // Generate 1080p video
      executeFFmpegCommand(
          "ffmpeg", "-y",
          "-i", uploadedFileLocation,
          "-vf", "scale=1920:1080:flags=fast_bilinear",
          "-c:v", "libx264",
          "-preset", "ultrafast",
          "-crf", "28",
          "-c:a", "copy",
          highResFile.getAbsolutePath());
      // executeFFmpegCommand(
      // "ffmpeg", "-y", "-i", uploadedFileLocation,
      // "-vf", "scale=1920:1080", "-c:a", "copy",
      // String.format("./res/videos/%s/1080.mp4", movieName));
      System.out.println("High res video generated");
      
      Integer nextId = Mariadb.queryDB("SELECT AUTO_INCREMENT FROM information_schema.TABLES where TABLE_SCHEMA = \"db\" AND TABLE_NAME = \"MOVIE\";").getInt("AUTO_INCREMENT");
      GCSHelper.upload("thumbnails/" + nextId.toString() + ".png", thumbFile, "image/png");
      GCSHelper.upload("videos/" + nextId.toString() + "/360.mp4", lowResFile, "video/mp4");
      GCSHelper.upload("videos/" + nextId.toString() + "/1080.mp4", highResFile, "video/mp4");

      thumbFile.delete();
      lowResFile.delete();
      highResFile.delete();
      File file = new File(uploadedFileLocation);
      file.delete();
      return true;
    } catch (IOException e) {
      System.err.println("Unable to convert video");
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      new File(uploadedFileLocation).delete();
    }
    return false;

  }

  private void executeFFmpegCommand(String... command) throws IOException, InterruptedException {
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.redirectErrorStream(true);
    pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);

    Process process = pb.start();
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IOException("FFmpeg process failed with exit code: " + exitCode);
    }
  }

  // save uploaded file to new location
  private void writeToFile(InputStream uploadedInputStream,
      String uploadedFileLocation) {

    try {

      try (OutputStream out = new FileOutputStream(new File(
          uploadedFileLocation))) {
        int read = 0;
        byte[] bytes = new byte[1024];

        while ((read = uploadedInputStream.read(bytes)) != -1) {
          out.write(bytes, 0, read);
        }
        out.flush();
      }
    } catch (IOException e) {

      e.printStackTrace();
    }

  }

}
