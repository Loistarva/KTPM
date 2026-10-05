package com.ktpm;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Always a real isolated PostgreSQL. Default Docker; portable binaries selectable when Docker is
 * unavailable.
 */
public abstract class DatabaseTest {
  private static final String url, username, password;
  private static EmbeddedPostgres embedded;
  private static PostgreSQLContainer<?> container;

  static {
    try {
      if (Boolean.getBoolean("ktpm.test.embedded")) {
        embedded = EmbeddedPostgres.builder().setPort(0).start();
        url = embedded.getJdbcUrl("postgres", "postgres");
        username = "postgres";
        password = "";
        Runtime.getRuntime()
            .addShutdownHook(
                new Thread(
                    () -> {
                      try {
                        embedded.close();
                      } catch (Exception ignored) {
                      }
                    }));
      } else {
        container = new PostgreSQLContainer<>("postgres:16.2");
        container.start();
        url = container.getJdbcUrl();
        username = container.getUsername();
        password = container.getPassword();
      }
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> url);
    r.add("spring.datasource.username", () -> username);
    r.add("spring.datasource.password", () -> password);
    r.add("ktpm.jwt.secret", () -> "ktpm-tests-secret-with-more-than-32-bytes-0123456789");
    r.add("ktpm.seed-enabled", () -> false);
    r.add("ktpm.scheduler.enabled", () -> false);
  }
}
