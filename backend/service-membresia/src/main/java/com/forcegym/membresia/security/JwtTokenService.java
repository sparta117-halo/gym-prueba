package com.forcegym.membresia.security;

import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

  private final SecretKey secretKey;

  public JwtTokenService(
      @Value("${force-gym.security.jwt-secret:0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF}")
      String jwtSecret) {
    this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
  }

  public SecretKey secretKey() {
    return secretKey;
  }
}