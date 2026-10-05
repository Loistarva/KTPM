package com.ktpm.auth.application;

public final class AuthModels {
  private AuthModels() {}

  public record Register(String username, String email, String password) {}

  public record Login(String login, String password) {}

  public record Token(String accessToken, String tokenType, long expiresIn) {}
}
