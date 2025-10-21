package com.mariadb;

/**
 * Movies
 */
public class Movie {

  private int id;
  private int genre;
  private int year;
  private String name;
  private String description;
  private String videoPath;
  private String thumbnailPath;

  public Movie(int id, String name, String description, int genre, int year, String videoPath,
      String thumbnailPath) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.genre = genre;
    this.year = year;
    this.videoPath = videoPath;
    this.thumbnailPath = thumbnailPath;

  }

  public void setId(int id) {
    this.id = id;
  }

  public int getGenre() {
    return genre;
  }

  public void setGenre(int genre) {
    this.genre = genre;
  }

  public int getYear() {
    return year;
  }

  public void setYear(int year) {
    this.year = year;
  }

  public String getVideoPath() {
    return videoPath;
  }

  public String getThumbnailPath() {
    return thumbnailPath;
  }

  public void setThumbnailUrl(String thumbnailUrl) {
    this.thumbnailPath = thumbnailUrl;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }
}
