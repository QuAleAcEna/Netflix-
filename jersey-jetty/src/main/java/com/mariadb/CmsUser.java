package com.mariadb;

public class CmsUser {
  private int id;
  private String username;
  private String password;

  public CmsUser(int id, String username, String password) {
    this.id = id;
    this.username = username;
    this.password = password;
  }

  public int getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }
}
