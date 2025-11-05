package com.mkyong.endpoints;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.mariadb.Mariadb;
import com.mariadb.Profile;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/profile")
public class Profiles implements endpoint {

  public static class ProfilePayload {
    public int userId;
    public String name;
    public String avatarColor;
    public boolean kids;

    public ProfilePayload() {
    }
  }

  @GET
  @Path("/user/{userId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getProfiles(@PathParam("userId") int userId) {
    List<Profile> profiles = new ArrayList<>();
    String[] args = { Integer.toString(userId) };
    ResultSet result = Mariadb.queryDB("SELECT * FROM PROFILE WHERE userId = ?", args);
    try {
      if (result != null) {
        while (result.next()) {
          profiles.add(new Profile(result.getInt("id"), result.getInt("userId"),
              result.getString("name"), result.getString("avatarColor"), result.getBoolean("kids")));
        }
      }
      return Response.ok(profiles).build();
    } catch (SQLException se) {
      se.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Unable to fetch profiles").build();
    }
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response createProfile(ProfilePayload payload) {
    if (payload == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Invalid payload").type(MediaType.TEXT_PLAIN).build();
    }

    String trimmedName = payload.name == null ? "" : payload.name.trim();
    if (trimmedName.isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Profile name is required")
          .type(MediaType.TEXT_PLAIN).build();
    }

    String color = (payload.avatarColor == null || payload.avatarColor.trim().isEmpty())
        ? "#E50914"
        : payload.avatarColor.trim();

    String[] duplicateArgs = { Integer.toString(payload.userId), trimmedName };
    ResultSet existing = Mariadb.queryDB("SELECT id FROM PROFILE WHERE userId = ? AND name = ?", duplicateArgs);
    try {
      if (existing != null && existing.next()) {
        return Response.status(Response.Status.CONFLICT).entity("Profile already exists")
            .type(MediaType.TEXT_PLAIN).build();
      }
    } catch (SQLException se) {
      se.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Unable to validate profile uniqueness")
          .type(MediaType.TEXT_PLAIN).build();
    }

    String[] insertArgs = { Integer.toString(payload.userId), trimmedName, color, payload.kids ? "1" : "0" };
    Integer newProfileId = Mariadb.insertAndReturnId(
        "INSERT INTO PROFILE(userId,name,avatarColor,kids) VALUES(?,?,?,?)",
        insertArgs);

    if (newProfileId == null) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Unable to create profile")
          .type(MediaType.TEXT_PLAIN).build();
    }

    String[] fetchArgs = { Integer.toString(newProfileId) };
    ResultSet created = Mariadb.queryDB("SELECT * FROM PROFILE WHERE id = ?", fetchArgs);
    try {
      if (created != null && created.next()) {
        Profile profile = new Profile(created.getInt("id"), created.getInt("userId"),
            created.getString("name"), created.getString("avatarColor"), created.getBoolean("kids"));
        return Response.status(Response.Status.CREATED).entity(profile).build();
      }
    } catch (SQLException se) {
      se.printStackTrace();
    }

    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Profile created but not retrievable")
        .type(MediaType.TEXT_PLAIN).build();
  }
}
