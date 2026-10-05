package com.ktpm.auth.api;

import com.ktpm.auth.application.AuthService;
import com.ktpm.auth.infrastructure.CurrentUser;
import com.ktpm.user.application.UserDtos.Profile;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthService auth;
  private final com.ktpm.user.application.UserService users;

  @GetMapping("/me")
  public Profile me() {
    return users.get(CurrentUser.get().id());
  }

  @org.springframework.web.bind.annotation.ResponseStatus(
      org.springframework.http.HttpStatus.CREATED)
  @PostMapping("/register")
  public ResponseEntity<Profile> register(@Valid @RequestBody AuthDtos.Register r) {
    return ResponseEntity.status(201).body(auth.register(r.command()));
  }

  @PostMapping("/login")
  public com.ktpm.auth.application.AuthModels.Token login(@Valid @RequestBody AuthDtos.Login r) {
    return auth.login(r.command());
  }

  @org.springframework.web.bind.annotation.ResponseStatus(
      org.springframework.http.HttpStatus.NO_CONTENT)
  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    auth.logout(CurrentUser.get().id());
    return ResponseEntity.noContent().build();
  }
}
