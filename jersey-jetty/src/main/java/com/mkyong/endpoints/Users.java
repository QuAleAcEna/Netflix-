package com.mkyong.endpoints;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.mariadb.*;

@Path("/user")
public class Users implements endpoint {

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public String invalidParam() {
    return "Invalid url";
  }

  @Path("/{name}/{password}")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public User newUser(@PathParam("name") String name, @PathParam("password") String password) {

    User obj = new User(0, name, password);
    String[] arg = { name };

    ResultSet result = Mariadb.queryDB("SELECT * FROM USER WHERE name = ?", arg);
    try {

      if (result != null && result.next() == false) {

        String[] args = { name, password };
        Mariadb.insert("INSERT INTO USER(name,password) VALUES(?,?)", args);
        return obj;
      }
    } catch (SQLException se) {
      se.printStackTrace();
    } finally {
    }

    return null;

  }

  @Path("/connect/{name}/{password}")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public User connectUser(@PathParam("name") String name, @PathParam("password") String password) {

    String[] args = { name, password };
    ResultSet result = Mariadb.queryDB("SELECT * FROM USER WHERE name = ? AND password = ?", args);
    try {

      if (result != null) {
        result.next();
        return new User(result.getInt("id"), result.getString("name"), result.getString("password"));
      }
    } catch (SQLException se) {
      se.printStackTrace();
    } finally {
    }

    return null;
  }

  @Path("/all")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<User> helloList() {

    List<User> list = new ArrayList<>();

    ResultSet result = Mariadb.queryDB("SELECT * FROM USER");
    try {

      while (result.next()) {
        list.add(new User(result.getInt("id"), result.getString("name"), result.getString("password")));
      }
    } catch (SQLException se) {
      return null;
    }
    return list;

  }

}
