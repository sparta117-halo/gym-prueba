package com.forcegym.app.api;

import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scheluder")
public class ScheluderController {

  @GetMapping("/jobs")
  public List<ScheduledJobResponse> jobs() {
    return List.of(
        new ScheduledJobResponse(
            "sync-hourly",
            "SCHEDULED",
            "0 0 * * * *",
            Instant.now().plusSeconds(3600).toString()),
        new ScheduledJobResponse(
            "membership-expiry-alerts",
            "SCHEDULED",
            "0 0 8 * * *",
            Instant.now().plusSeconds(8 * 3600).toString()));
  }

  public record ScheduledJobResponse(
      String jobName,
      String status,
      String cron,
      String nextExecutionAt) {}
}
