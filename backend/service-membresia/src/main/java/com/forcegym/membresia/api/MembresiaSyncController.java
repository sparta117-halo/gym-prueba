package com.forcegym.membresia.api;

import com.forcegym.membresia.service.MembresiaSyncService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/membresia")
public class MembresiaSyncController {

    private final MembresiaSyncService syncService;

    public MembresiaSyncController(MembresiaSyncService syncService) {
        this.syncService = syncService;
    }

  @GetMapping("/bootstrap")
  public BootstrapResponse bootstrap() {
        return syncService.bootstrap();
  }

  @GetMapping("/sync/pull")
  public PullSyncResponse pull() {
        return syncService.pull();
  }

  @PostMapping("/sync/push")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public PushSyncResponse push(@RequestBody PushSyncRequest request) {
        return syncService.push(request);
  }

  public record BootstrapResponse(
      BootstrapMeta meta,
      List<MemberSummary> members,
      List<PaymentSummary> payments,
      List<AttendanceSummary> attendances) {}

  public record BootstrapMeta(String tenantId, String branchId, String generatedAt) {}

  public record MemberSummary(
      String id,
      String code,
      String firstName,
      String lastName,
      String status,
      String membershipEndsAt) {}

  public record PaymentSummary(
      String id,
      String memberId,
      double amount,
      String paidAt) {}

  public record AttendanceSummary(
      String id,
      String memberId,
      String occurredAt) {}

  public record PullSyncResponse(
      SyncCursor cursor,
      List<Map<String, Object>> upserts,
      List<Map<String, Object>> deletions,
      List<Map<String, Object>> conflicts,
      List<Map<String, Object>> warnings) {}

  public record SyncCursor(String entity, String nextCursor) {}

  public record PushSyncRequest(List<SyncOperation> operations) {}

  public record SyncOperation(
      String entityType,
      String entityId,
      String operationType,
      Map<String, Object> payload,
      String deviceId,
      String requestedAt) {}

  public record PushSyncResponse(
      int accepted,
      int rejected,
      String processedAt,
      List<Map<String, Object>> conflicts) {}
}