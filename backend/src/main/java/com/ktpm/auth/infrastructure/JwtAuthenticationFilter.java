package com.ktpm.auth.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktpm.auth.application.*;
import com.ktpm.common.ApiExceptionHandler;
import com.ktpm.user.domain.User;
import com.ktpm.user.domain.UserStore;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtService jwt;
  private final UserStore users;
  private final ObjectMapper json;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String header = request.getHeader("Authorization");
    if (header != null) {
      try {
        if (!header.startsWith("Bearer ")) throw new IllegalArgumentException();
        Claims c = jwt.parse(header.substring(7));
        User u = users.findById(Long.valueOf(c.getSubject())).orElseThrow();
        if (u.getTokenVersion() != c.get("ver", Integer.class))
          throw new IllegalArgumentException();
        CurrentUser p =
            new CurrentUser(u.getId(), u.getUsername(), u.getRole(), u.getTokenVersion());
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    p, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole()))));
      } catch (Exception e) {
        SecurityContextHolder.clearContext();
        response.setStatus(401);
        response.setContentType("application/json");
        json.writeValue(
            response.getOutputStream(),
            ApiExceptionHandler.body(
                HttpStatus.UNAUTHORIZED, "Invalid or expired token", request.getRequestURI()));
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
