package com.forcegym.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

  private final SecretKey secretKey;
  private final long accessTokenMinutes;

  public JwtTokenService(
      @Value("${force-gym.security.jwt-secret}") String jwtSecret,
      @Value("${force-gym.security.access-token-minutes:15}") long accessTokenMinutes) {
    this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    this.accessTokenMinutes = accessTokenMinutes;
  }

  public String createAccessToken(String userId, String username, String userType, List<String> roles) {
    final Instant now = Instant.now();
    return Jwts.builder()
        .subject(userId)
        .claim("username", username)
        .claim("userType", userType)
        .claim("roles", roles)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(accessTokenMinutes, ChronoUnit.MINUTES)))
        .signWith(secretKey)
        .compact();
  }
}