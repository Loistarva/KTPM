package com.ktpm.user.infrastructure;

import com.ktpm.common.infrastructure.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
public class UserEntity extends BaseEntity {
  private static final java.security.SecureRandom TOKEN_VERSIONS = new java.security.SecureRandom();

  @Column(nullable = false, length = 50, unique = true)
  private String username;

  @Column(nullable = false, length = 254, unique = true)
  private String email;

  @Column(nullable = false, length = 255)
  private String passwordHash;

  @Column(nullable = false, length = 16)
  private String role = "USER";

  @Column(nullable = false)
  private int tokenVersion = 1 + TOKEN_VERSIONS.nextInt(1_000_000_000);
}
