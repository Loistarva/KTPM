package com.ktpm.user.domain;

import com.ktpm.common.Pages;
import java.util.Optional;

public interface UserStore {
  Optional<User> findById(Long id);

  Optional<User> lockById(Long id);

  Optional<User> findByUsername(String name);

  Optional<User> findByUsernameOrEmail(String username, String email);

  boolean existsByUsernameOrEmail(String username, String email);

  boolean existsById(Long id);

  User saveAndFlush(User user);

  void delete(Long id);

  Pages.Result<User> list(int page, int size);
}
