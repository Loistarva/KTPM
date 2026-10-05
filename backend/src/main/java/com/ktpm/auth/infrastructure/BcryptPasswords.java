package com.ktpm.auth.infrastructure;

import com.ktpm.auth.application.Passwords;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BcryptPasswords implements Passwords {
  private final PasswordEncoder encoder;

  public String encode(String raw) {
    return encoder.encode(raw);
  }

  public boolean matches(String raw, String encoded) {
    return encoder.matches(raw, encoded);
  }
}
