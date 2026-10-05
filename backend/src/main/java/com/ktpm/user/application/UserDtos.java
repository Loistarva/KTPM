package com.ktpm.user.application;

import com.ktpm.user.domain.User;
import java.time.Instant;

public final class UserDtos {
  private UserDtos() {}

  public record Profile(Long id, String username, String email, String role, Instant createdAt) {
    public static Profile from(User u) {
      return new Profile(u.getId(), u.getUsername(), u.getEmail(), u.getRole(), u.getCreatedAt());
    }
  }
}
