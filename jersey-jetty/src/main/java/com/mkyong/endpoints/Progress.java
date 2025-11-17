package com.mkyong.endpoints;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.mariadb.Mariadb;
import com.mariadb.WatchProgress;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/progress")
public class Progress implements endpoint {

  public static class ProgressPayload {
    public int profileId;
    public int movieId;
    public long positionMs;
  }

  private WatchProgress mapRow(ResultSet result) throws SQLException {
    return new WatchProgress(result.getInt("profileId"), result.getInt("movieId"), result.getLong("positionMs"),
        result.getTimestamp("updatedAt"));
  }

  @GET
  @Path("/profile/{profileId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getProgressByProfile(@PathParam("profileId") int profileId) {
    String[] args = { Integer.toString(profileId) };
    ResultSet result = Mariadb.queryDB(
        "SELECT profileId, movieId, positionMs, updatedAt FROM WATCH_PROGRESS WHERE profileId = ?", args);
    if (result == null) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to fetch progress entries").build();
    }

    List<WatchProgress> entries = new ArrayList<>();
    try {
      while (result.next()) {
        entries.add(mapRow(result));
      }
      return Response.ok(entries).build();
    } catch (SQLException e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to parse entries").build();
    }
  }

  @GET
  @Path("/{profileId}/{movieId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getProgress(@PathParam("profileId") int profileId, @PathParam("movieId") int movieId) {
    String[] args = { Integer.toString(profileId), Integer.toString(movieId) };
    ResultSet result = Mariadb.queryDB(
        "SELECT profileId, movieId, positionMs, updatedAt FROM WATCH_PROGRESS WHERE profileId = ? AND movieId = ?",
        args);
    if (result == null) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to fetch progress entry").build();
    }
    try {
      if (result.next() == false) {
        return Response.status(Response.Status.NOT_FOUND).entity("Progress not found").build();
      }
      return Response.ok(mapRow(result)).build();
    } catch (SQLException e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to parse entry").build();
    }
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response upsertProgress(ProgressPayload payload) {
    if (payload == null || payload.profileId <= 0 || payload.movieId <= 0 || payload.positionMs < 0) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Invalid progress payload").build();
    }

    String sql = "INSERT INTO WATCH_PROGRESS (profileId, movieId, positionMs) VALUES (?, ?, ?) "
        + "ON DUPLICATE KEY UPDATE positionMs = VALUES(positionMs), updatedAt = CURRENT_TIMESTAMP";
    String[] args = { Integer.toString(payload.profileId), Integer.toString(payload.movieId),
        Long.toString(payload.positionMs) };
    boolean success = Mariadb.insert(sql, args);
    if (!success) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to save progress").build();
    }
    return Response.ok().build();
  }

  @DELETE
  @Path("/{profileId}/{movieId}")
  public Response deleteProgress(@PathParam("profileId") int profileId, @PathParam("movieId") int movieId) {
    if (profileId <= 0 || movieId <= 0) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Invalid identifiers").build();
    }
    String[] args = { Integer.toString(profileId), Integer.toString(movieId) };
    boolean success = Mariadb.execute("DELETE FROM WATCH_PROGRESS WHERE profileId = ? AND movieId = ?", args);
    if (!success) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to clear progress").build();
    }
    return Response.ok().build();
  }
}
