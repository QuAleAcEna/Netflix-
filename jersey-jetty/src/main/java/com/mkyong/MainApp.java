package com.mkyong;

import org.eclipse.jetty.server.Server;
import org.glassfish.jersey.jetty.JettyHttpContainerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mariadb.*;
import com.mkyong.endpoints.Users;

public class MainApp {
  public static final String BASE_URI = "http://localhost:8080/";

  public static Server startServer() {

    // scan packages
    // final ResourceConfig config = new ResourceConfig().packages("com.mkyong");
    Class<?>[] set = { com.mkyong.endpoints.Users.class, com.mkyong.endpoints.Movies.class };
    final ResourceConfig config = new ResourceConfig(set);
    final Server server = JettyHttpContainerFactory.createServer(URI.create(BASE_URI), config);

    return server;

  }

  public static void main(String[] args) {
    Path currentRelativePath = Paths.get("");
    String s = currentRelativePath.toAbsolutePath().toString();
    System.out.println("Current absolute path is: " + s);
    if (Mariadb.init() == false) {
      System.out.println("Unable to init database");
      return;
    }

    try {

      final Server server = startServer();

      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        try {
          System.out.println("Shutting down the application...");
          server.stop();
          System.out.println("Done, exit.");
        } catch (Exception e) {
          Logger.getLogger(MainApp.class.getName()).log(Level.SEVERE, null, e);
        }
      }));

      System.out.println(String.format("Application started.%nStop the application using CTRL+C"));

      // block and wait shut down signal, like CTRL+C
      Thread.currentThread().join();

      // alternative
      // Thread.sleep(Long.MAX_VALUE); // sleep forever...
      // Thread.sleep(Integer.MAX_VALUE); // sleep around 60+ years

    } catch (InterruptedException ex) {
      Logger.getLogger(MainApp.class.getName()).log(Level.SEVERE, null, ex);
    }

  }

}
