package com.ktpm.auth.application;

import com.ktpm.auth.application.AuthModels.*;
import com.ktpm.common.*;
import com.ktpm.user.application.UserDtos.Profile;
import com.ktpm.user.domain.*;
import java.util.Locale;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuthService {
  private final UserStore users;
  private final Passwords passwords;
  private final AuthTokens tokens;
  private final UnitOfWork work;

  public Profile register(Register r) {
    return work.write(
        () -> {
          String name = r.username().toLowerCase(Locale.ROOT),
              email = r.email().toLowerCase(Locale.ROOT);
          if (users.existsByUsernameOrEmail(name, email))
            throw BusinessException.conflict("Username or email already registered");
          if (r.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw BusinessException.bad("Password must be <= 72 UTF-8 bytes");
          User u = new User();
          u.setUsername(name);
          u.setEmail(email);
          u.setPasswordHash(passwords.encode(r.password()));
          return Profile.from(users.saveAndFlush(u));
        });
  }

  public Token login(Login r) {
    return work.read(
        () -> {
          String login = r.login().toLowerCase(Locale.ROOT);
          var u =
              users
                  .findByUsernameOrEmail(login, login)
                  .orElseThrow(() -> BusinessException.unauthorized("Invalid credentials"));
          if (!passwords.matches(r.password(), u.getPasswordHash()))
            throw BusinessException.unauthorized("Invalid credentials");
          return new Token(tokens.issue(u), "Bearer", tokens.ttlSeconds());
        });
  }

  public void logout(Long id) {
    work.write(
        () -> {
          var u = users.lockById(id).orElseThrow(() -> BusinessException.missing("User not found"));
          u.setTokenVersion(u.getTokenVersion() + 1);
          users.saveAndFlush(u);
        });
  }
}
