package com.forcegym.app.api;

import com.forcegym.app.security.JwtTokenService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/gateway/auth")
public class AuthController {

  private final JdbcTemplate jdbcTemplate;
  private final JwtTokenService jwtTokenService;

  public AuthController(JdbcTemplate jdbcTemplate, JwtTokenService jwtTokenService) {
    this.jdbcTemplate = jdbcTemplate;
    this.jwtTokenService = jwtTokenService;
  }

  @PostMapping("/login")
  @ResponseStatus(HttpStatus.OK)
  public LoginResponse login(@RequestBody LoginRequest request) {
    final String username = resolveUsername(request);
    final List<UserRow> users = jdbcTemplate.query(
        """
            select id::text as id, username, password_hash, user_type
            from users
            where username = ?
            limit 1
            """,
        (rs, rowNum) -> new UserRow(
            rs.getString("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("user_type")),
        username);

    if (users.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas");
    }

    final UserRow user = users.getFirst();
    final String password = resolvePassword(request, user.userType(), user.username());
    if (!user.passwordHash().equals(password)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas");
    }

    final List<String> roles = new ArrayList<>(jdbcTemplate.query(
        """
            select r.code
            from user_roles ur
            join roles r on r.id = ur.role_id
            where ur.user_id = cast(? as uuid)
            order by r.code asc
            """,
        (rs, rowNum) -> rs.getString("code"),
            user.id()));

        if (roles.isEmpty() && hasText(user.userType())) {
          roles.add(user.userType().trim().toUpperCase());
        }

    final String accessToken = jwtTokenService.createAccessToken(
        user.id(), user.username(), user.userType(), roles);

    return new LoginResponse(
        accessToken,
        Instant.now().plus(15, ChronoUnit.MINUTES).toString(),
        new UserProfile(user.id(), user.username(), user.userType(), roles));
  }

  private String resolveUsername(LoginRequest request) {
    final String qrCode = trimToNull(request.qrCode());
    if (qrCode != null) {
      return parseQrCode(qrCode);
    }

    final String username = trimToNull(request.username());
    if (username == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes indicar un usuario o un QR.");
    }
    return username;
  }

  private String resolvePassword(LoginRequest request, String userType, String username) {
    final String password = trimToNull(request.password());
    if (password != null) {
      return password;
    }
    if ("MEMBER".equalsIgnoreCase(userType)) {
      return username;
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña es obligatoria.");
  }

  private String parseQrCode(String rawQrCode) {
    final String normalized = rawQrCode.trim();
    if (normalized.regionMatches(true, 0, "FGM:", 0, 4)) {
      final String memberCode = trimToNull(normalized.substring(4));
      if (memberCode != null) {
        return memberCode.toUpperCase();
      }
    }
    return normalized.toUpperCase();
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    final String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private boolean hasText(String value) {
    return trimToNull(value) != null;
  }

  public record LoginRequest(String username, String password, String qrCode) {}

  public record LoginResponse(String accessToken, String expiresAt, UserProfile user) {}

  public record UserProfile(String id, String username, String userType, List<String> roles) {}

  private record UserRow(String id, String username, String passwordHash, String userType) {}
}
