package com.ktpm.common;

import com.ktpm.user.domain.User;
import com.ktpm.user.domain.UserStore;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "ktpm.seed-enabled", havingValue = "true")
@RequiredArgsConstructor
public class DevelopmentSeed implements ApplicationRunner {
  private final UserStore users;
  private final PasswordEncoder passwords;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    user("admin", "ADMIN", "Admin123!");
    user("seller", "USER", "Seller123!");
    user("bidder1", "USER", "Bidder123!");
    user("bidder2", "USER", "Bidder123!");
  }

  private void user(String name, String role, String password) {
    if (users.findByUsername(name).isPresent()) return;
    User u = new User();
    u.setUsername(name);
    u.setEmail(name + "@ktpm.local");
    u.setRole(role);
    u.setPasswordHash(passwords.encode(password));
    users.saveAndFlush(u);
  }
}
