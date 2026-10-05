package com.ktpm.auth.infrastructure;

import com.ktpm.common.BusinessException;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;

public record CurrentUser(Long id, String username, String role, int tokenVersion) {
  public static CurrentUser get() {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || !(a.getPrincipal() instanceof CurrentUser u))
      throw BusinessException.unauthorized("Authentication required");
    return u;
  }
}
