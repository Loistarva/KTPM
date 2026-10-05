package com.ktpm.auth.infrastructure;

import com.ktpm.user.domain.User;
import com.ktpm.user.domain.UserStore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
  private final UserStore users;

  public User loadAccount(String login) {
    return users
        .findByUsernameOrEmail(login.toLowerCase(), login.toLowerCase())
        .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
  }

  public UserDetails loadUserByUsername(String login) {
    User u = loadAccount(login);
    return org.springframework.security.core.userdetails.User.withUsername(u.getUsername())
        .password(u.getPasswordHash())
        .roles(u.getRole())
        .build();
  }
}
