package com.mariadb;

public class User {

  private int id;
  private String name;
  private String password;

  public User(int x, String usrName, String usrPassword) {
    id = x;
    name = usrName;
    password = usrPassword;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setPassword(String newPassword) {
    this.password = newPassword;
  }

  public String getPassword() {
    return password;
  }
}
