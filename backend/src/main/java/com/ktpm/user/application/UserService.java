package com.ktpm.user.application;

import com.ktpm.common.*;
import com.ktpm.user.application.UserDtos.Profile;
import com.ktpm.user.domain.UserStore;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserService {
  private final UserStore users;
  private final UnitOfWork work;

  public Profile get(Long id) {
    return work.read(
        () ->
            Profile.from(
                users.findById(id).orElseThrow(() -> BusinessException.missing("User not found"))));
  }

  public void requireExists(Long id) {
    work.read(
        () -> {
          if (!users.existsById(id)) throw BusinessException.missing("User not found");
          return null;
        });
  }
}
