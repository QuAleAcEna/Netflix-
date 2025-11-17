package com.mariadb;

import java.sql.Timestamp;

public class WatchProgress {
  public int profileId;
  public int movieId;
  public long positionMs;
  public Timestamp updatedAt;

  public WatchProgress(int profileId, int movieId, long positionMs, Timestamp updatedAt) {
    this.profileId = profileId;
    this.movieId = movieId;
    this.positionMs = positionMs;
    this.updatedAt = updatedAt;
  }

  public WatchProgress() {
  }
}
