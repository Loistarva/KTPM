package com.ktpm.auth.api;

import com.ktpm.auth.application.AuthModels;
import jakarta.validation.constraints.*;

public final class AuthDtos {
  private AuthDtos() {}

  public record Register(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,50}") String username,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 8, max = 72) String password) {
    public AuthModels.Register command() {
      return new AuthModels.Register(username, email, password);
    }
  }

  public record Login(
      @NotBlank @Size(max = 254) String login, @NotBlank @Size(max = 72) String password) {
    public AuthModels.Login command() {
      return new AuthModels.Login(login, password);
    }
  }
}
