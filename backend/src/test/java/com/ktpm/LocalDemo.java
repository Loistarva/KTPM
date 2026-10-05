package com.ktpm;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.SpringApplication;

/**
 * Optional development launcher. Never packaged in the production JAR. Persistent PostgreSQL data
 * is outside target so Maven clean cannot erase demo history.
 */
public final class LocalDemo {
  public static void main(String[] args) throws Exception {
    Path data =
        Paths.get(System.getProperty("ktpm.local.data", ".local/postgres")).toAbsolutePath();
    Files.createDirectories(data);
    EmbeddedPostgres pg =
        EmbeddedPostgres.builder()
            .setPort(55432)
            .setDataDirectory(data)
            .setCleanDataDirectory(false)
            .start();
    List<String> options = new ArrayList<>(Arrays.asList(args));
    options.add("--spring.datasource.url=" + pg.getJdbcUrl("postgres", "postgres"));
    options.add("--spring.datasource.username=postgres");
    options.add("--spring.datasource.password=");
    SpringApplication app = new SpringApplication(KtpmApplication.class);
    app.setAdditionalProfiles("dev");
    try {
      var context = app.run(options.toArray(String[]::new));
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    context.close();
                    try {
                      pg.close();
                    } catch (Exception ignored) {
                    }
                  }));
      new java.util.concurrent.CountDownLatch(1).await();
    } catch (Throwable e) {
      pg.close();
      throw e;
    }
  }
}
