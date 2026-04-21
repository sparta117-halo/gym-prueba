package com.forcegym.app.api;

import com.forcegym.app.security.JwtTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
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
        request.username());

    if (users.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas");
    }

    final UserRow user = users.getFirst();
    if (!user.passwordHash().equals(request.password())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas");
    }

    final List<String> roles = jdbcTemplate.query(
        """
            select r.code
            from user_roles ur
            join roles r on r.id = ur.role_id
            where ur.user_id = cast(? as uuid)
            order by r.code asc
            """,
        (rs, rowNum) -> rs.getString("code"),
        user.id());

    final String accessToken = jwtTokenService.createAccessToken(
        user.id(), user.username(), user.userType(), roles);

    return new LoginResponse(
        accessToken,
        Instant.now().plus(15, ChronoUnit.MINUTES).toString(),
        new UserProfile(user.id(), user.username(), user.userType(), roles));
  }

  public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

  public record LoginResponse(String accessToken, String expiresAt, UserProfile user) {}

  public record UserProfile(String id, String username, String userType, List<String> roles) {}

  private record UserRow(String id, String username, String passwordHash, String userType) {}
}
