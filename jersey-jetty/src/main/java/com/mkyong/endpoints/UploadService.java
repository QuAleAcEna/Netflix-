package com.mkyong.endpoints;

import com.mariadb.Mariadb;
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

      processVideo(uploadedFileLocation, movieName);
      addVideoToDB(movieName);
    }).start();

    return Response.status(200).build();

  }

  private void addVideoToDB(String movieName) {

    String videoPath = String.format("./res/videos/%s", movieName);
    String thumbnailPath = String.format("./res/thumbnails/%s", movieName);
    String[] args = { movieName, videoPath, thumbnailPath, "aaa", "0", "0" };
    if (Mariadb.insert("INSERT INTO MOVIE(name,videoPath,thumbnailPath,description,year,genre) VALUES(?,?,?,?,?,?)",
        args) == false) {
      System.err.println("Unable to insert video to db");
      return;
    }
    System.out.println(String.format("Uploaded %s", movieName));

  }

  private void processVideo(String uploadedFileLocation, String movieName) {
    try {
      new File(String.format("./res/videos/%s", movieName)).mkdirs();
      new File(String.format("./res/thumbnails/%s", movieName)).mkdirs();

      executeFFmpegCommand(
          "ffmpeg", "-y", "-ss", "00:00:10", "-i", uploadedFileLocation,
          "-frames", "1", String.format("./res/thumbnails/%s/%s.png", movieName, movieName));
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
          String.format("./res/videos/%s/360.mp4", movieName));
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
          String.format("./res/videos/%s/1080.mp4", movieName));
      // executeFFmpegCommand(
      // "ffmpeg", "-y", "-i", uploadedFileLocation,
      // "-vf", "scale=1920:1080", "-c:a", "copy",
      // String.format("./res/videos/%s/1080.mp4", movieName));
      System.out.println("High res video generated");

    } catch (IOException e) {
      System.err.println("Unable to convert video");
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      new File(uploadedFileLocation).delete();
    }

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
