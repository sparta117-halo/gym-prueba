package com.forcegym.app.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtTokenService jwtTokenService;

  public JwtAuthFilter(JwtTokenService jwtTokenService) {
    this.jwtTokenService = jwtTokenService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    final String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      final String token = authHeader.substring(7);
      try {
        final Claims claims = Jwts.parser()
            .verifyWith(jwtTokenService.secretKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();

        final String username = claims.get("username", String.class);
        @SuppressWarnings("unchecked")
        final List<String> roles = claims.get("roles", List.class);

        final List<SimpleGrantedAuthority> authorities = roles == null
            ? List.of()
            : roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(username, null, authorities));
      } catch (Exception ignored) {
        // Token inválido — el contexto queda vacío y Spring Security bloqueará rutas protegidas
      }
    }

    filterChain.doFilter(request, response);
  }
}
