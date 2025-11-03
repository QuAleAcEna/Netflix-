package com.mariadb;

public class Profile {
  private int id;
  private int userId;
  private String name;
  private String avatarColor;
  private boolean kids;

  public Profile(int id, int userId, String name, String avatarColor, boolean kids) {
    this.id = id;
    this.userId = userId;
    this.name = name;
    this.avatarColor = avatarColor;
    this.kids = kids;
  }

  public Profile() {
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public int getUserId() {
    return userId;
  }

  public void setUserId(int userId) {
    this.userId = userId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getAvatarColor() {
    return avatarColor;
  }

  public void setAvatarColor(String avatarColor) {
    this.avatarColor = avatarColor;
  }

  public boolean isKids() {
    return kids;
  }

  public void setKids(boolean kids) {
    this.kids = kids;
  }
}
