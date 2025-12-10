package com.mkyong.endpoints;

import com.mariadb.CmsUser;
import com.mariadb.Mariadb;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.sql.ResultSet;
import java.sql.SQLException;

@Path("/cms")
public class CmsAuth implements endpoint {

  public static class LoginRequest {
    public String username;
    public String password;
  }

  @POST
  @Path("/login")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CmsUser login(LoginRequest request) {
    if (request == null || request.username == null || request.password == null) {
      return null;
    }
    String[] args = { request.username.trim(), request.password };
    ResultSet result = Mariadb.queryDB("SELECT * FROM CMS_USER WHERE username = ? AND password = ?", args);
    try {
      if (result != null && result.next()) {
        return new CmsUser(
            result.getInt("id"),
            result.getString("username"),
            result.getString("password"));
      }
    } catch (SQLException se) {
      return null;
    }
    return null;
  }
}
