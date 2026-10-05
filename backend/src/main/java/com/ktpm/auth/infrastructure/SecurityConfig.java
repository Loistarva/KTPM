package com.ktpm.auth.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktpm.auth.application.*;
import com.ktpm.common.ApiExceptionHandler;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  FilterRegistrationBean<JwtAuthenticationFilter> noServletRegistration(JwtAuthenticationFilter f) {
    var b = new FilterRegistrationBean<>(f);
    b.setEnabled(false);
    return b;
  }

  @Bean
  SecurityFilterChain chain(HttpSecurity http, JwtAuthenticationFilter jwt, ObjectMapper json)
      throws Exception {
    return http.csrf(c -> c.disable())
        .logout(c -> c.disable())
        .cors(c -> {})
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/actuator/health")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/auctions/me")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.GET, "/api/auctions", "/api/auctions/*", "/api/auctions/*/bids")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (r, s, x) ->
                            write(json, r, s, HttpStatus.UNAUTHORIZED, "Authentication required"))
                    .accessDeniedHandler(
                        (r, s, x) -> write(json, r, s, HttpStatus.FORBIDDEN, "Access denied")))
        .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  private void write(
      ObjectMapper json,
      HttpServletRequest r,
      HttpServletResponse s,
      HttpStatus status,
      String message)
      throws java.io.IOException {
    s.setStatus(status.value());
    s.setContentType("application/json");
    json.writeValue(
        s.getOutputStream(), ApiExceptionHandler.body(status, message, r.getRequestURI()));
  }

  @Bean
  CorsConfigurationSource cors(@Value("${ktpm.cors-origins}") String origins) {
    var c = new CorsConfiguration();
    c.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
    c.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return source;
  }
}
