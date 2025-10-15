package com.mkyong.endpoints;

import com.mariadb.Mariadb;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/file")
public class UploadService implements endpoint {

  @POST
  @Path("/upload")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response uploadFile(
      @FormDataParam("file") InputStream uploadedInputStream,
      @FormDataParam("file") FormDataContentDisposition fileDetail) {
    System.out.println("Uploading...");

    String movieName = fileDetail.getFileName().replace(".mp4", "");

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
    String[] args = { movieName, videoPath, thumbnailPath };
    Mariadb.insert("INSERT INTO MOVIE(name,videoPath,thumbnailPath) VALUES(?,?,?)", args);
  }

  private void processVideo(String uploadedFileLocation, String movieName) {
    try {
      new File(String.format("./res/videos/%s", movieName)).mkdirs();
      new File(String.format("./res/thumbnails/%s", movieName)).mkdirs();
      System.out.println(

          Runtime.getRuntime().exec(String.format("ffmpeg -y -ss 00:00:10 -i %s -frames 1 ./res/thumbnails/%s/%s.png ",
              uploadedFileLocation, movieName, movieName)).waitFor());
      System.out.println("Thumbnail generated");
      System.out.println(

          Runtime.getRuntime()
              .exec(String.format("ffmpeg -y -i %s -vf  scale=640:360 -c:a copy ./res/videos/%s/360.mp4 ",
                  uploadedFileLocation, movieName))
              .waitFor());
      System.out.println("Low res video generated");
      System.out.println(

          Runtime.getRuntime()
              .exec(String.format("ffmpeg -y -i %s -vf  scale=1920:1080 -c:a copy ./res/videos/%s/1080.mp4 ",
                  uploadedFileLocation, movieName))
              .waitFor());

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

  // save uploaded file to new location
  private void writeToFile(InputStream uploadedInputStream,
      String uploadedFileLocation) {

    try {

      OutputStream out = new FileOutputStream(new File(
          uploadedFileLocation));
      int read = 0;
      byte[] bytes = new byte[1024];

      while ((read = uploadedInputStream.read(bytes)) != -1) {
        out.write(bytes, 0, read);
      }
      out.flush();
      out.close();
    } catch (IOException e) {

      e.printStackTrace();
    }

  }

}
