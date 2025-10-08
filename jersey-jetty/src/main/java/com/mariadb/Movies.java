package com.mariadb;

/**
 * Movies
 */
public class Movies {

  private int id;
  private int genre;
  private int year;
  private String name;
  private String description;
  private String lowResUrl, highResUrl;
  private String thumbnailUrl;

  public Movies(int id, String name, String description, int genre, int year, String lowResUrl, String highResUrl,
      String thumbnailUrl) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.genre = genre;
    this.year = year;
    this.lowResUrl = lowResUrl;
    this.highResUrl = highResUrl;
    this.thumbnailUrl = thumbnailUrl;

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

  public String getLowResUrl() {
    return lowResUrl;
  }

  public void setLowResUrl(String lowResUrl) {
    this.lowResUrl = lowResUrl;
  }

  public String getHighResUrl() {
    return highResUrl;
  }

  public void setHighResUrl(String highResUrl) {
    this.highResUrl = highResUrl;
  }

  public String getThumbnailUrl() {
    return thumbnailUrl;
  }

  public void setThumbnailUrl(String thumbnailUrl) {
    this.thumbnailUrl = thumbnailUrl;
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
