package com.mkyong.endpoints;

import com.mariadb.*;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MediaType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Path("/user")
public class Users implements endpoint {

  public static class CreateRequest {
    public String name;
    public String password;
  }

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public String invalidParam() {
    return "Invalid url";
  }

  @Path("/{name}/{password}")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public User newUser(@PathParam("name") String name, @PathParam("password") String password) {

    String sanitizedName = name.trim();
    String[] arg = { sanitizedName };

    ResultSet result = Mariadb.queryDB("SELECT * FROM USER WHERE name = ?", arg);
    try {

      if (result != null && result.next() == false) {

        String[] args = { sanitizedName, password };
        Integer newUserId = Mariadb.insertAndReturnId("INSERT INTO USER(name,password) VALUES(?,?)", args);
        if (newUserId == null) {
          return null;
        }

        String[] profileArgs = { Integer.toString(newUserId), sanitizedName, "#E50914", "0" };
        Mariadb.insert("INSERT INTO PROFILE(userId,name,avatarColor,kids) VALUES(?,?,?,?)", profileArgs);

        String[] fetchArgs = { Integer.toString(newUserId) };
        ResultSet created = Mariadb.queryDB("SELECT * FROM USER WHERE id = ?", fetchArgs);
        if (created != null && created.next()) {
          return new User(created.getInt("id"), created.getString("name"), created.getString("password"));
        }
        return new User(newUserId, sanitizedName, password);
      }
    } catch (SQLException se) {
     // se.printStackTrace();
    } finally {
    }

    return null;

  }

  @Path("/connect/{name}/{password}")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public User connectUser(@PathParam("name") String name, @PathParam("password") String password) {

    String sanitizedName = name.trim();
    String[] args = { sanitizedName, password };
    ResultSet result = Mariadb.queryDB("SELECT * FROM USER WHERE name = ? AND password = ?", args);
    try {

      if (result != null && result.next()) {
        return new User(result.getInt("id"), result.getString("name"), result.getString("password"));
      }
    } catch (SQLException se) {
      //se.printStackTrace();
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

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response createUser(CreateRequest request) {
    if (request == null || request.name == null || request.password == null) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    String sanitizedName = request.name.trim();
    if (sanitizedName.isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    String[] existsArgs = { sanitizedName };
    ResultSet exists = Mariadb.queryDB("SELECT id FROM USER WHERE name = ?", existsArgs);
    try {
      if (exists != null && exists.next()) {
        System.out.println("Create user refused: user already exists -> " + sanitizedName);
        return Response.status(Response.Status.CONFLICT).entity("User already exists").build();
      }
    } catch (SQLException ignored) {
      System.out.println("Create user failed checking existing for " + sanitizedName);
    }

    String[] args = { sanitizedName, request.password };
    Integer newUserId = Mariadb.insertAndReturnId("INSERT INTO USER(name,password) VALUES(?,?)", args);
    if (newUserId == null) {
      System.out.println("Create user failed (DB insert) for name=" + sanitizedName);
      return Response.serverError().build();
    }

    // create a default profile
    String[] profileArgs = { Integer.toString(newUserId), sanitizedName, "#E50914", "0" };
    Mariadb.insert("INSERT INTO PROFILE(userId,name,avatarColor,kids) VALUES(?,?,?,?)", profileArgs);

    String[] fetchArgs = { Integer.toString(newUserId) };
    ResultSet created = Mariadb.queryDB("SELECT * FROM USER WHERE id = ?", fetchArgs);
    try {
      if (created != null && created.next()) {
        User user = new User(created.getInt("id"), created.getString("name"), created.getString("password"));
        System.out.println("Created streaming user id=" + user.getId() + " name=" + user.getName());
        return Response.ok(user).build();
      }
    } catch (SQLException ignored) {
    }
    System.out.println("Create user failed (post-fetch) for name=" + sanitizedName);
    return Response.serverError().build();
  }

  @DELETE
  @Path("/{id}")
  public Response deleteUser(@PathParam("id") int id) {
    String[] args = { Integer.toString(id) };
    ResultSet user = Mariadb.queryDB("SELECT id FROM USER WHERE id = ?", args);
    try {
      if (user != null && user.next()) {
        Mariadb.execute("DELETE FROM USER WHERE id = ?", args);
        System.out.println("Deleted streaming user id=" + id);
      } else {
        System.out.println("Delete user: id not found (id=" + id + ")");
      }
    } catch (SQLException ignored) {
      System.out.println("Delete user failed for id=" + id);
    }
    // idempotent delete
    return Response.noContent().build();
  }

}
