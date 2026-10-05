package com.ktpm.auth.application;

public interface Passwords {
  String encode(String password);

  boolean matches(String raw, String encoded);
}
