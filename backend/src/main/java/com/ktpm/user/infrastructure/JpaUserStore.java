package com.ktpm.user.infrastructure;

import com.ktpm.common.Pages;
import com.ktpm.common.infrastructure.*;
import com.ktpm.user.domain.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaUserStore implements UserStore {
  private final UserRepository repo;
  private final EntityManager entities;

  private User model(UserEntity e) {
    return JpaMapping.model(e, User::new);
  }

  public Optional<User> findById(Long id) {
    return repo.findById(id).map(this::model);
  }

  public Optional<User> lockById(Long id) {
    return repo.lockById(id).map(this::model);
  }

  public Optional<User> findByUsername(String name) {
    return repo.findByUsername(name).map(this::model);
  }

  public Optional<User> findByUsernameOrEmail(String name, String email) {
    return repo.findByUsernameOrEmail(name, email).map(this::model);
  }

  public boolean existsByUsernameOrEmail(String name, String email) {
    return repo.existsByUsernameOrEmail(name, email);
  }

  public boolean existsById(Long id) {
    return repo.existsById(id);
  }

  public User saveAndFlush(User u) {
    return JpaMapping.save(u, repo, UserEntity::new);
  }

  public void delete(Long id) {
    repo.deleteById(id);
    repo.flush();
    entities.clear();
  }

  public Pages.Result<User> list(int page, int size) {
    return JpaPages.result(repo.findAll(JpaPages.request(page, size)), this::model);
  }
}
