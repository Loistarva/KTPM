package com.ktpm.user.domain;

import com.ktpm.common.BaseModel;
import lombok.*;

@Getter
@Setter
public class User extends BaseModel {
  private static final java.security.SecureRandom TOKEN_VERSIONS = new java.security.SecureRandom();

  private String username;

  private String email;

  private String passwordHash;

  private String role = "USER";

  private int tokenVersion = 1 + TOKEN_VERSIONS.nextInt(1_000_000_000);
}
