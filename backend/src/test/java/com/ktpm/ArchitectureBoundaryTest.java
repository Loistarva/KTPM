package com.ktpm;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ArchitectureBoundaryTest {
  @Test
  void businessSourcesHaveNoWebDatabaseOrInfrastructureDependencies() throws Exception {
    Path root = Path.of("src/main/java/com/ktpm");
    Pattern forbidden =
        Pattern.compile(
            "jakarta\\.(persistence|servlet)|org\\.springframework\\.|io\\.jsonwebtoken|com\\.ktpm\\.\\w+\\.(api|infrastructure)");
    int checked = 0;
    try (var paths = Files.walk(root)) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        String name = root.relativize(path).toString().replace('\\', '/');
        if (name.contains("/application/")
            || name.contains("/domain/")
            || Set.of(
                    "common/BaseModel.java",
                    "common/BusinessException.java",
                    "common/Pages.java",
                    "common/UnitOfWork.java")
                .contains(name)) {
          assertFalse(
              forbidden.matcher(Files.readString(path)).find(),
              "Forbidden business dependency: " + name);
          checked++;
        }
      }
    }
    assertTrue(checked >= 20, "Business boundary scan must cover all modules");
  }

  @Test
  void businessModelsAreNotOrmEntities() {
    for (Class<?> model :
        List.of(
            com.ktpm.user.domain.User.class,
            com.ktpm.auction.domain.Auction.class,
            com.ktpm.bidding.domain.Bid.class)) {
      for (var annotation : model.getAnnotations())
        assertFalse(annotation.annotationType().getName().startsWith("jakarta.persistence"));
    }
  }
}
