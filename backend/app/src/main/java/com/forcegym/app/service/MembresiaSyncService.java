package com.forcegym.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forcegym.app.api.MembresiaSyncController.AttendanceSummary;
import com.forcegym.app.api.MembresiaSyncController.BootstrapMeta;
import com.forcegym.app.api.MembresiaSyncController.BootstrapResponse;
import com.forcegym.app.api.MembresiaSyncController.MemberSummary;
import com.forcegym.app.api.MembresiaSyncController.PaymentSummary;
import com.forcegym.app.api.MembresiaSyncController.PullSyncResponse;
import com.forcegym.app.api.MembresiaSyncController.PushSyncRequest;
import com.forcegym.app.api.MembresiaSyncController.PushSyncResponse;
import com.forcegym.app.api.MembresiaSyncController.SyncCursor;
import com.forcegym.app.api.MembresiaSyncController.SyncOperation;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MembresiaSyncService {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public MembresiaSyncService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public BootstrapResponse bootstrap() {
    final BootstrapMeta meta = jdbcTemplate.query(
        """
            select t.id::text as tenant_id, b.id::text as branch_id
            from tenants t
            join branches b on b.tenant_id = t.id
            order by t.created_at asc, b.created_at asc
            limit 1
            """,
        rs -> rs.next()
            ? new BootstrapMeta(rs.getString("tenant_id"), rs.getString("branch_id"), Instant.now().toString())
            : new BootstrapMeta("default-tenant", "default-branch", Instant.now().toString()));

    final List<MemberSummary> members = jdbcTemplate.query(
        """
            select
              m.id::text as id,
              m.member_code,
              m.status,
              m.ends_at,
              m.snapshot_jsonb::text as snapshot
            from memberships m
            order by m.ends_at asc
            limit 50
            """,
        (rs, rowNum) -> {
          final Map<String, Object> snapshot = readMap(rs.getString("snapshot"));
          return new MemberSummary(
              rs.getString("id"),
              rs.getString("member_code"),
              stringValue(snapshot.get("nombre"), "Miembro"),
              stringValue(snapshot.get("apellido"), "Demo"),
              rs.getString("status"),
              rs.getObject("ends_at", OffsetDateTime.class).toInstant().toString());
        });

    final List<PaymentSummary> payments = jdbcTemplate.query(
        """
            select
              p.id::text as id,
              p.membership_id::text as membership_id,
              p.amount,
              p.paid_at
            from payments p
            order by p.paid_at desc
            limit 100
            """,
        (rs, rowNum) -> new PaymentSummary(
            rs.getString("id"),
            rs.getString("membership_id"),
            rs.getBigDecimal("amount").doubleValue(),
            rs.getObject("paid_at", OffsetDateTime.class).toInstant().toString()));

    final List<AttendanceSummary> attendances = jdbcTemplate.query(
        """
            select
              a.id::text as id,
              a.membership_id::text as membership_id,
              a.occurred_at
            from attendance_events a
            order by a.occurred_at desc
            limit 100
            """,
        (rs, rowNum) -> new AttendanceSummary(
            rs.getString("id"),
            rs.getString("membership_id"),
            rs.getObject("occurred_at", OffsetDateTime.class).toInstant().toString()));

    return new BootstrapResponse(meta, members, payments, attendances);
  }

  public PullSyncResponse pull() {
    final List<Map<String, Object>> upserts = new ArrayList<>();

    jdbcTemplate.query(
        """
            select
              m.id::text as id,
              m.member_code,
              m.status,
              m.ends_at,
              m.snapshot_jsonb::text as snapshot
            from memberships m
            order by m.updated_at desc
            limit 50
            """,
        rs -> {
          final Map<String, Object> snapshot = readMap(rs.getString("snapshot"));
          final Map<String, Object> payload = new LinkedHashMap<>();
          payload.put("id", rs.getString("id"));
          payload.put("code", rs.getString("member_code"));
          payload.put("nombre", stringValue(snapshot.get("nombre"), "Miembro"));
          payload.put("apellido", stringValue(snapshot.get("apellido"), "Demo"));
          payload.put("telefono", stringValue(snapshot.get("telefono"), ""));
          payload.put("password", stringValue(snapshot.get("password"), "123456"));
          payload.put("fechaInicio", stringValue(snapshot.get("fechaInicio"), Instant.now().toString()));
          payload.put("fechaFin", rs.getObject("ends_at", OffsetDateTime.class).toInstant().toString());
          payload.put("meses", intValue(snapshot.get("meses"), 1));
          payload.put("diaPago", intValue(snapshot.get("diaPago"), 1));
          payload.put("asistencias", listValue(snapshot.get("asistencias")));
          payload.put("pagos", listValue(snapshot.get("pagos")));
          payload.put("progreso", listValue(snapshot.get("progreso")));

          upserts.add(Map.of(
              "entityType", "member",
              "entityId", rs.getString("id"),
              "operationType", "UPSERT",
              "payload", payload));
        });

    return new PullSyncResponse(
        new SyncCursor("membresia", Instant.now().toString()),
        upserts, List.of(), List.of(), List.of());
  }

  public PushSyncResponse push(PushSyncRequest request) {
    int accepted = 0;
    for (SyncOperation operation : request.operations()) {
      final UUID tenantId = defaultTenantId();
      final UUID branchId = defaultBranchId();
      final UUID userId = defaultAdminUserId();

      jdbcTemplate.update(
          """
              insert into sync_operations (
                id, tenant_id, branch_id, user_id,
                device_id, entity_type, entity_id, operation_type,
                payload_jsonb, operation_status, requested_at, created_at, updated_at
              ) values (
                gen_random_uuid(), ?, ?, ?,
                ?, ?, ?, ?, cast(? as jsonb), 'PENDING', cast(? as timestamptz), now(), now()
              )
              """,
          tenantId, branchId, userId,
          operation.deviceId(), operation.entityType(), operation.entityId(),
          operation.operationType(), writeJson(operation.payload()), operation.requestedAt());

      if ("member".equalsIgnoreCase(operation.entityType())) {
        applyMemberOperation(tenantId, branchId, operation);
      }

      jdbcTemplate.update(
          """
              update sync_operations
              set operation_status = 'PROCESSED',
                  processed_at = now(),
                  updated_at = now()
              where tenant_id = ?
                and entity_type = ?
                and entity_id = ?
                and requested_at = cast(? as timestamptz)
              """,
          tenantId, operation.entityType(), operation.entityId(), operation.requestedAt());

      accepted++;
    }
    return new PushSyncResponse(accepted, 0, Instant.now().toString(), List.of());
  }

  private void applyMemberOperation(UUID tenantId, UUID branchId, SyncOperation operation) {
    if ("DELETE".equalsIgnoreCase(operation.operationType())) {
      deleteMembership(tenantId, operation.payload());
      return;
    }
    upsertMembership(tenantId, branchId, operation.payload());
  }

  private void upsertMembership(UUID tenantId, UUID branchId, Map<String, Object> payload) {
    final String memberCode = stringValue(payload.get("code"), null);
    if (memberCode == null || memberCode.isBlank()) return;

    final UUID memberUserId = ensureMemberUser(tenantId, branchId, payload, memberCode);
    final UUID membershipId = existingMembershipId(tenantId, memberCode);
    final UUID planId = defaultPlanId(tenantId, branchId);
    final OffsetDateTime startsAt = parseTimestamp(payload.get("fechaInicio"), OffsetDateTime.now());
    final OffsetDateTime endsAt = parseTimestamp(payload.get("fechaFin"), startsAt.plusMonths(1));
    final int billingDay = intValue(payload.get("diaPago"), startsAt.getDayOfMonth());
    final String status = endsAt.isAfter(OffsetDateTime.now()) ? "active" : "expired";
    final String snapshotJson = writeJson(payload);

    if (membershipId == null) {
      jdbcTemplate.update(
          """
              insert into memberships (
                id, tenant_id, branch_id, member_user_id, plan_id,
                member_code, status, starts_at, ends_at, billing_day,
                snapshot_jsonb, created_at, updated_at
              ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), now(), now())
              """,
          UUID.randomUUID(), tenantId, branchId, memberUserId, planId,
          memberCode, status, startsAt, endsAt, billingDay, snapshotJson);
    } else {
      jdbcTemplate.update(
          """
              update memberships
              set member_user_id = ?, plan_id = ?, status = ?,
                  starts_at = ?, ends_at = ?, billing_day = ?,
                  snapshot_jsonb = cast(? as jsonb), updated_at = now()
              where id = ?
              """,
          memberUserId, planId, status, startsAt, endsAt, billingDay, snapshotJson, membershipId);
    }

    final UUID resolvedId = membershipId != null ? membershipId : existingMembershipId(tenantId, memberCode);
    if (resolvedId != null) {
      replacePayments(tenantId, branchId, resolvedId, payload.get("pagos"));
      replaceAttendances(tenantId, branchId, resolvedId, payload.get("asistencias"));
    }
  }

  private void deleteMembership(UUID tenantId, Map<String, Object> payload) {
    final String memberCode = stringValue(payload.get("code"), null);
    UUID membershipId = null;

    if (memberCode != null && !memberCode.isBlank()) {
      membershipId = existingMembershipId(tenantId, memberCode);
    }
    if (membershipId == null) {
      final String externalId = stringValue(payload.get("id"), null);
      if (externalId != null && !externalId.isBlank()) {
        membershipId = jdbcTemplate.query(
            """
                select id from memberships
                where tenant_id = ? and snapshot_jsonb ->> 'id' = ?
                limit 1
                """,
            rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
            tenantId, externalId);
      }
    }
    if (membershipId == null) return;

    jdbcTemplate.update("delete from attendance_events where membership_id = ?", membershipId);
    jdbcTemplate.update("delete from payments where membership_id = ?", membershipId);
    jdbcTemplate.update("delete from memberships where id = ?", membershipId);
  }

  private UUID ensureMemberUser(UUID tenantId, UUID branchId, Map<String, Object> payload, String memberCode) {
    final String phone = stringValue(payload.get("telefono"), "");
    final String password = stringValue(payload.get("password"), memberCode);
    final String profileJson = writeJson(Map.of(
        "nombre", stringValue(payload.get("nombre"), ""),
        "apellido", stringValue(payload.get("apellido"), "")));

    final UUID existingId = jdbcTemplate.query(
        "select id from users where tenant_id = ? and username = ? limit 1",
        rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
        tenantId, memberCode);

    if (existingId != null) {
      jdbcTemplate.update(
          """
              update users
              set branch_id = ?, phone = ?, password_hash = ?,
                  user_type = 'MEMBER', profile_jsonb = cast(? as jsonb), updated_at = now()
              where id = ?
              """,
          branchId, phone, password, profileJson, existingId);
      return existingId;
    }

    final UUID userId = UUID.randomUUID();
    jdbcTemplate.update(
        """
            insert into users (
              id, tenant_id, branch_id, phone, username,
              password_hash, user_type, profile_jsonb, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, 'MEMBER', cast(? as jsonb), now(), now())
            """,
        userId, tenantId, branchId, phone, memberCode, password, profileJson);
    return userId;
  }

  private void replacePayments(UUID tenantId, UUID branchId, UUID membershipId, Object paymentsValue) {
    jdbcTemplate.update("delete from payments where membership_id = ?", membershipId);
    if (!(paymentsValue instanceof List<?> payments)) return;

    for (Object item : payments) {
      if (!(item instanceof Map<?, ?> rawPayment)) continue;
      final Map<String, Object> payment = stringifyMap(rawPayment);
      jdbcTemplate.update(
          """
              insert into payments (
                id, tenant_id, branch_id, membership_id,
                amount, currency, paid_at, payment_method, metadata_jsonb, created_at, updated_at
              ) values (?, ?, ?, ?, ?, 'GTQ', ?, 'cash', cast(? as jsonb), now(), now())
              """,
          UUID.randomUUID(), tenantId, branchId, membershipId,
          decimalValue(payment.get("monto"), 0.0d),
          parseTimestamp(payment.get("fecha"), OffsetDateTime.now()),
          writeJson(payment));
    }
  }

  private void replaceAttendances(UUID tenantId, UUID branchId, UUID membershipId, Object attendancesValue) {
    jdbcTemplate.update("delete from attendance_events where membership_id = ?", membershipId);
    if (!(attendancesValue instanceof List<?> attendances)) return;

    for (Object item : attendances) {
      jdbcTemplate.update(
          """
              insert into attendance_events (
                id, tenant_id, branch_id, membership_id,
                occurred_at, source, context_jsonb, created_at
              ) values (?, ?, ?, ?, ?, 'sync', '{}'::jsonb, now())
              """,
          UUID.randomUUID(), tenantId, branchId, membershipId,
          parseTimestamp(item, OffsetDateTime.now()));
    }
  }

  private UUID defaultTenantId() {
    return jdbcTemplate.query(
        "select id from tenants order by created_at asc limit 1",
        rs -> rs.next() ? rs.getObject("id", UUID.class) : null);
  }

  private UUID defaultBranchId() {
    return jdbcTemplate.query(
        "select id from branches order by created_at asc limit 1",
        rs -> rs.next() ? rs.getObject("id", UUID.class) : null);
  }

  private UUID defaultAdminUserId() {
    return jdbcTemplate.query(
        "select id from users order by created_at asc limit 1",
        rs -> rs.next() ? rs.getObject("id", UUID.class) : null);
  }

  private UUID defaultPlanId(UUID tenantId, UUID branchId) {
    return jdbcTemplate.query(
        """
            select id from membership_plans
            where tenant_id = ? and (branch_id = ? or branch_id is null)
            order by created_at asc limit 1
            """,
        rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
        tenantId, branchId);
  }

  private UUID existingMembershipId(UUID tenantId, String memberCode) {
    return jdbcTemplate.query(
        "select id from memberships where tenant_id = ? and member_code = ? limit 1",
        rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
        tenantId, memberCode);
  }

  private OffsetDateTime parseTimestamp(Object value, OffsetDateTime fallback) {
    if (value instanceof String s && !s.isBlank()) {
      try { return OffsetDateTime.parse(s); } catch (Exception ignored) {}
      try { return Instant.parse(s).atOffset(java.time.ZoneOffset.UTC); } catch (Exception ignored) {}
    }
    return fallback;
  }

  private Map<String, Object> stringifyMap(Map<?, ?> rawMap) {
    final Map<String, Object> normalized = new LinkedHashMap<>();
    rawMap.forEach((k, v) -> normalized.put(String.valueOf(k), v));
    return normalized;
  }

  private double decimalValue(Object value, double fallback) {
    return value instanceof Number n ? n.doubleValue() : fallback;
  }

  private Map<String, Object> readMap(String json) {
    try {
      if (json == null || json.isBlank()) return Map.of();
      return objectMapper.readValue(json, MAP_TYPE);
    } catch (Exception e) { return Map.of(); }
  }

  private String writeJson(Map<String, Object> payload) {
    try { return objectMapper.writeValueAsString(payload == null ? Map.of() : payload); }
    catch (Exception e) { return "{}"; }
  }

  private String stringValue(Object value, String fallback) {
    return value instanceof String s && !s.isBlank() ? s : fallback;
  }

  private int intValue(Object value, int fallback) {
    return value instanceof Number n ? n.intValue() : fallback;
  }

  private List<Object> listValue(Object value) {
    return value instanceof List<?> l ? new ArrayList<>(l) : List.of();
  }
}
