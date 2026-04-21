package com.forcegym.config.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config/public")
public class PublicConfigController {

  @GetMapping("/client")
  public Map<String, Object> clientConfig() {
    return Map.of(
        "appName", "Force Gym",
        "syncIntervalMinutes", 60,
        "promptOnOnline", true,
        "updateMode", "confirm",
        "timestamp", Instant.now().toString());
  }

  @GetMapping("/version")
  public Map<String, Object> version() {
    return Map.of(
        "currentVersion", "1.0.0",
        "minimumSupportedVersion", "1.0.0",
        "updateAvailable", false,
        "timestamp", Instant.now().toString());
  }
}