package com.ktpm.auth.application;

import com.ktpm.user.domain.User;

public interface AuthTokens {
  String issue(User user);

  long ttlSeconds();
}
