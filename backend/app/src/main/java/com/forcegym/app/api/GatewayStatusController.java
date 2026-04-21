package com.forcegym.app.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gateway")
public class GatewayStatusController {

  @GetMapping("/status")
  public Map<String, Object> status() {
    return Map.of(
        "service", "force-gym-app",
        "status", "UP",
        "timestamp", Instant.now().toString(),
        "routes", new String[]{
          "/api/config/**",
          "/api/membresia/**",
          "/api/rutinas/**",
          "/api/media/**",
          "/api/scheluder/**"
        });
  }
}
