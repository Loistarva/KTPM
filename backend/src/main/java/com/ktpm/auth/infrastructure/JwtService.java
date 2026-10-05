package com.ktpm.auth.infrastructure;

import com.ktpm.auth.application.AuthTokens;
import com.ktpm.user.domain.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService implements AuthTokens {
  private final SecretKey key;
  private final long ttl;

  public JwtService(
      @Value("${ktpm.jwt.secret}") String secret, @Value("${ktpm.jwt.ttl-seconds}") long ttl) {
    if (secret.getBytes(StandardCharsets.UTF_8).length < 32 || ttl <= 0)
      throw new IllegalArgumentException(
          "JWT secret must have at least 32 bytes and TTL must be positive");
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.ttl = ttl;
  }

  public String issue(User u) {
    Instant now = Instant.now();
    return Jwts.builder()
        .setIssuer("KTPM")
        .setSubject(u.getId().toString())
        .claim("username", u.getUsername())
        .claim("role", u.getRole())
        .claim("ver", u.getTokenVersion())
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(now.plusSeconds(ttl)))
        .signWith(key)
        .compact();
  }

  public Claims parse(String token) {
    return Jwts.parserBuilder()
        .requireIssuer("KTPM")
        .setSigningKey(key)
        .build()
        .parseClaimsJws(token)
        .getBody();
  }

  public long ttlSeconds() {
    return ttl;
  }
}
