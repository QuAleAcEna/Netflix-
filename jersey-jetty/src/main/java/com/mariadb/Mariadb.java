package com.mariadb;

import java.sql.*;

public class Mariadb {
  // JDBC driver name and database URL

  static final String JDBC_DRIVER = "org.mariadb.jdbc.Driver";
  static final String DB_URL = "jdbc:mariadb://192.168.100.174/db";

  static private Connection conn = null;
  static private Statement stmt = null;

  // Database credentials
  static final String USER = "root";
  static final String PASS = "root";

  static public boolean init() {
    try {

      Class.forName("org.mariadb.jdbc.Driver");

      // STEP 3: Open a connection
      System.out.println("Connecting to a selected database...");
      conn = DriverManager.getConnection(
          "jdbc:mariadb://localhost:3306/db", "root", "root");
      System.out.println("Connected database successfully...");

      // STEP 4: Execute a query
      System.out.println("Creating table in given database...");
      stmt = conn.createStatement();

      String sql = "CREATE TABLE IF NOT EXISTS USER "
          + "(id INT AUTO_INCREMENT not NULL, "
          + " name VARCHAR(255) not NULL, "
          + " password VARCHAR(255) not NULL,"
          + " PRIMARY KEY ( id ))";

      stmt.executeUpdate(sql);
      sql = "CREATE TABLE IF NOT EXISTS MOVIE "
          + "(id INT AUTO_INCREMENT not NULL, "
          + " name VARCHAR(255) not NULL, "
          + " description VARCHAR(255),"
          + " genre INT,"
          + " year INT,"
          + " videoPath VARCHAR(255) not NULL,"
          + " thumbnailPath VARCHAR(255) not NULL,"
          + " PRIMARY KEY ( id ))";

      stmt.executeUpdate(sql);
      System.out.println("Created table in given database...");
    } catch (SQLException se) {
      Mariadb.exit();
      se.printStackTrace();
      return false;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    } finally {
    }

    return true;
  }

  public static ResultSet queryDB(String stm, String[] args) {

    try {
      PreparedStatement pstm = conn.prepareStatement(stm);
      for (int i = 0; i < args.length; i++) {
        pstm.setString(i + 1, args[i]);
      }
      ResultSet result = pstm.executeQuery();
      return result;
    } catch (SQLException se) {
      Mariadb.exit();
      se.printStackTrace();
      return null;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    } finally {
    }

  }

  public static ResultSet queryDB(String stm) {
    try {
      ResultSet result = stmt.executeQuery(stm);
      return result;
    } catch (SQLException se) {
      Mariadb.exit();
      se.printStackTrace();
      return null;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    } finally {
    }

  }

  private static void exit() {

    System.out.println("Exited");
    try {
      if (conn != null) {
        conn.close();
      }
    } catch (SQLException se) {
      se.printStackTrace();
    } // end finally try
  }

  public static boolean insert(String stm, String[] args) {
    try {
      PreparedStatement pstm = conn.prepareStatement(stm);
      for (int i = 0; i < args.length; i++) {
        pstm.setString(i + 1, args[i]);
      }
      pstm.executeUpdate();
    }

    catch (SQLException se) {
      se.printStackTrace();
      Mariadb.exit();
      return false;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    } finally {
    }

    return true;
  }

};
