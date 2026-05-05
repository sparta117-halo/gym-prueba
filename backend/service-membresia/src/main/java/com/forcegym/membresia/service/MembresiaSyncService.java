package com.forcegym.membresia.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forcegym.membresia.api.MembresiaSyncController.AttendanceSummary;
import com.forcegym.membresia.api.MembresiaSyncController.BootstrapMeta;
import com.forcegym.membresia.api.MembresiaSyncController.BootstrapResponse;
import com.forcegym.membresia.api.MembresiaSyncController.MemberSummary;
import com.forcegym.membresia.api.MembresiaSyncController.PaymentSummary;
import com.forcegym.membresia.api.MembresiaSyncController.PullSyncResponse;
import com.forcegym.membresia.api.MembresiaSyncController.PushSyncRequest;
import com.forcegym.membresia.api.MembresiaSyncController.PushSyncResponse;
import com.forcegym.membresia.api.MembresiaSyncController.SyncCursor;
import com.forcegym.membresia.api.MembresiaSyncController.SyncOperation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MembresiaSyncService {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public MembresiaSyncService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public BootstrapResponse bootstrap(String requestedUsername, boolean memberScope) {
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

    final String memberCodeFilter = memberScope ? requireMemberCode(requestedUsername) : null;

    final List<MemberSummary> members = memberScope
        ? jdbcTemplate.query(
            """
                select
                  m.id::text as id,
                  m.member_code,
                  m.status,
                  m.ends_at,
                  m.snapshot_jsonb::text as snapshot
                from memberships m
                where m.member_code = ?
                order by m.ends_at asc
                limit 50
                """,
            (rs, rowNum) -> toMemberSummary(
                rs.getString("id"),
                rs.getString("member_code"),
                rs.getString("status"),
                rs.getObject("ends_at", OffsetDateTime.class),
                rs.getString("snapshot")),
            memberCodeFilter)
        : jdbcTemplate.query(
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
            (rs, rowNum) -> toMemberSummary(
                rs.getString("id"),
                rs.getString("member_code"),
                rs.getString("status"),
                rs.getObject("ends_at", OffsetDateTime.class),
                rs.getString("snapshot")));

    final List<PaymentSummary> payments = memberScope
        ? jdbcTemplate.query(
            """
                select
                  p.id::text as id,
                  p.membership_id::text as membership_id,
                  p.amount,
                  p.paid_at
                from payments p
                join memberships m on m.id = p.membership_id
                where m.member_code = ?
                order by p.paid_at desc
                limit 100
                """,
            (rs, rowNum) -> toPaymentSummary(
                rs.getString("id"),
                rs.getString("membership_id"),
                rs.getBigDecimal("amount").doubleValue(),
                rs.getObject("paid_at", OffsetDateTime.class)),
            memberCodeFilter)
        : jdbcTemplate.query(
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
            (rs, rowNum) -> toPaymentSummary(
                rs.getString("id"),
                rs.getString("membership_id"),
                rs.getBigDecimal("amount").doubleValue(),
                rs.getObject("paid_at", OffsetDateTime.class)));

    final List<AttendanceSummary> attendances = memberScope
        ? jdbcTemplate.query(
            """
                select
                  a.id::text as id,
                  a.membership_id::text as membership_id,
                  a.occurred_at
                from attendance_events a
                join memberships m on m.id = a.membership_id
                where m.member_code = ?
                order by a.occurred_at desc
                limit 100
                """,
            (rs, rowNum) -> toAttendanceSummary(
                rs.getString("id"),
                rs.getString("membership_id"),
                rs.getObject("occurred_at", OffsetDateTime.class)),
            memberCodeFilter)
        : jdbcTemplate.query(
            """
                select
                  a.id::text as id,
                  a.membership_id::text as membership_id,
                  a.occurred_at
                from attendance_events a
                order by a.occurred_at desc
                limit 100
                """,
            (rs, rowNum) -> toAttendanceSummary(
                rs.getString("id"),
                rs.getString("membership_id"),
                rs.getObject("occurred_at", OffsetDateTime.class)));

    return new BootstrapResponse(meta, members, payments, attendances);
  }

  public PullSyncResponse pull(String requestedUsername, boolean memberScope) {
    final List<Map<String, Object>> upserts = new ArrayList<>();
    final String memberCodeFilter = memberScope ? requireMemberCode(requestedUsername) : null;

    if (memberScope) {
      jdbcTemplate.query(
          """
              select
                m.id::text as id,
                m.member_code,
                m.status,
                m.ends_at,
                m.snapshot_jsonb::text as snapshot
              from memberships m
              where m.member_code = ?
              order by m.updated_at desc
              limit 50
              """,
          (RowCallbackHandler) rs -> upserts.add(toMemberUpsert(rs)),
          memberCodeFilter);
    } else {
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
              (RowCallbackHandler) rs -> upserts.add(toMemberUpsert(rs)));
    }

    return new PullSyncResponse(
        new SyncCursor("membresia", Instant.now().toString()),
        upserts,
        List.of(),
        List.of(),
        List.of());
  }

  public PushSyncResponse push(PushSyncRequest request, String requestedUsername, boolean memberScope) {
    if (memberScope) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Los socios no pueden modificar membresias.");
    }

    int accepted = 0;
    for (SyncOperation operation : request.operations()) {
      final UUID tenantId = defaultTenantId();
      final UUID branchId = defaultBranchId();
      final UUID userId = defaultAdminUserId();

      jdbcTemplate.update(
          """
              insert into sync_operations (
                id,
                tenant_id,
                branch_id,
                user_id,
                device_id,
                entity_type,
                entity_id,
                operation_type,
                payload_jsonb,
                operation_status,
                requested_at,
                created_at,
                updated_at
              ) values (
                gen_random_uuid(),
                ?, ?, ?,
                ?, ?, ?, ?, cast(? as jsonb), 'PENDING', cast(? as timestamptz), now(), now()
              )
              """,
          tenantId,
          branchId,
          userId,
          operation.deviceId(),
          operation.entityType(),
          operation.entityId(),
          operation.operationType(),
          writeJson(operation.payload()),
          operation.requestedAt());

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
          tenantId,
          operation.entityType(),
          operation.entityId(),
          operation.requestedAt());
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
    final UUID existingMembershipId = resolveMembershipId(tenantId, payload);
    final String memberCode = resolveMemberCode(tenantId, existingMembershipId, payload);
    if (memberCode == null || memberCode.isBlank()) {
      return;
    }

    payload.put("code", memberCode);
    payload.put("password", memberCode);
    payload.put("qrToken", qrTokenFor(memberCode));

    final UUID memberUserId = ensureMemberUser(tenantId, branchId, payload, memberCode);
    final UUID membershipId = existingMembershipId != null ? existingMembershipId : existingMembershipId(tenantId, memberCode);
    final UUID planId = defaultPlanId(tenantId, branchId);
    final OffsetDateTime startsAt = parseTimestamp(payload.get("fechaInicio"), OffsetDateTime.now());
    final OffsetDateTime endsAt = parseTimestamp(payload.get("fechaFin"), startsAt.plusMonths(1));
    final int billingDay = intValue(payload.get("diaPago"), startsAt.getDayOfMonth());
    final String status = endsAt.isAfter(OffsetDateTime.now()) ? "active" : "expired";
    payload.put("fechaFin", endsAt.toInstant().toString());
    final String snapshotJson = writeJson(payload);

    if (membershipId == null) {
      jdbcTemplate.update(
          """
              insert into memberships (
                id,
                tenant_id,
                branch_id,
                member_user_id,
                plan_id,
                member_code,
                status,
                starts_at,
                ends_at,
                billing_day,
                snapshot_jsonb,
                created_at,
                updated_at
              ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), now(), now())
              """,
          UUID.randomUUID(),
          tenantId,
          branchId,
          memberUserId,
          planId,
          memberCode,
          status,
          startsAt,
          endsAt,
          billingDay,
          snapshotJson);
    } else {
      jdbcTemplate.update(
          """
              update memberships
              set member_user_id = ?,
                  plan_id = ?,
                  status = ?,
                  starts_at = ?,
                  ends_at = ?,
                  billing_day = ?,
                  snapshot_jsonb = cast(? as jsonb),
                  updated_at = now()
              where id = ?
              """,
          memberUserId,
          planId,
          status,
          startsAt,
          endsAt,
          billingDay,
          snapshotJson,
          membershipId);
    }

    final UUID resolvedMembershipId = membershipId != null ? membershipId : existingMembershipId(tenantId, memberCode);
    if (resolvedMembershipId != null) {
      replacePayments(tenantId, branchId, resolvedMembershipId, payload.get("pagos"));
      replaceAttendances(tenantId, branchId, resolvedMembershipId, payload.get("asistencias"));
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
                select id
                from memberships
                where tenant_id = ?
                  and snapshot_jsonb ->> 'id' = ?
                limit 1
                """,
            rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
            tenantId,
            externalId);
      }
    }

    if (membershipId == null) {
      return;
    }

    jdbcTemplate.update("delete from attendance_events where membership_id = ?", membershipId);
    jdbcTemplate.update("delete from payments where membership_id = ?", membershipId);
    jdbcTemplate.update("delete from memberships where id = ?", membershipId);
  }

  private UUID ensureMemberUser(UUID tenantId, UUID branchId, Map<String, Object> payload, String memberCode) {
    final String username = memberCode;
    final String phone = stringValue(payload.get("telefono"), "");
    final String password = memberCode;
    final String profileJson = writeJson(Map.of(
        "nombre", stringValue(payload.get("nombre"), ""),
        "apellido", stringValue(payload.get("apellido"), "")));

    final UUID existingId = jdbcTemplate.query(
        """
            select id
            from users
            where tenant_id = ?
              and username = ?
            limit 1
            """,
        rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
        tenantId,
        username);

    if (existingId != null) {
      jdbcTemplate.update(
          """
              update users
              set branch_id = ?,
                  phone = ?,
                  password_hash = ?,
                  user_type = 'MEMBER',
                  profile_jsonb = cast(? as jsonb),
                  updated_at = now()
              where id = ?
              """,
          branchId,
          phone,
          password,
          profileJson,
          existingId);
      ensureMemberRole(existingId);
      return existingId;
    }

    final UUID userId = UUID.randomUUID();
    jdbcTemplate.update(
        """
            insert into users (
              id,
              tenant_id,
              branch_id,
              phone,
              username,
              password_hash,
              user_type,
              profile_jsonb,
              created_at,
              updated_at
            ) values (?, ?, ?, ?, ?, ?, 'MEMBER', cast(? as jsonb), now(), now())
            """,
        userId,
        tenantId,
        branchId,
        phone,
        username,
        password,
        profileJson);
    ensureMemberRole(userId);
    return userId;
  }

  private void ensureMemberRole(UUID userId) {
    final UUID memberRoleId = jdbcTemplate.query(
        "select id from roles where code = 'MEMBER' limit 1",
        rs -> rs.next() ? rs.getObject("id", UUID.class) : null);
    if (memberRoleId == null) {
      return;
    }

    jdbcTemplate.update(
        "insert into user_roles (user_id, role_id) values (?, ?) on conflict do nothing",
        userId,
        memberRoleId);
  }

  private void replacePayments(UUID tenantId, UUID branchId, UUID membershipId, Object paymentsValue) {
    jdbcTemplate.update("delete from payments where membership_id = ?", membershipId);

    if (!(paymentsValue instanceof List<?> payments)) {
      return;
    }

    for (Object item : payments) {
      if (!(item instanceof Map<?, ?> rawPayment)) {
        continue;
      }

      final Map<String, Object> payment = stringifyMap(rawPayment);
      jdbcTemplate.update(
          """
              insert into payments (
                id,
                tenant_id,
                branch_id,
                membership_id,
                amount,
                currency,
                paid_at,
                payment_method,
                metadata_jsonb,
                created_at,
                updated_at
              ) values (?, ?, ?, ?, ?, 'GTQ', ?, 'cash', cast(? as jsonb), now(), now())
              """,
          UUID.randomUUID(),
          tenantId,
          branchId,
          membershipId,
          decimalValue(payment.get("monto"), 0.0d),
          parseTimestamp(payment.get("fecha"), OffsetDateTime.now()),
          writeJson(payment));
    }
  }

  private void replaceAttendances(UUID tenantId, UUID branchId, UUID membershipId, Object attendancesValue) {
    jdbcTemplate.update("delete from attendance_events where membership_id = ?", membershipId);

    if (!(attendancesValue instanceof List<?> attendances)) {
      return;
    }

    for (Object item : attendances) {
      jdbcTemplate.update(
          """
              insert into attendance_events (
                id,
                tenant_id,
                branch_id,
                membership_id,
                occurred_at,
                source,
                context_jsonb,
                created_at
              ) values (?, ?, ?, ?, ?, 'sync', '{}'::jsonb, now())
              """,
          UUID.randomUUID(),
          tenantId,
          branchId,
          membershipId,
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
            select id
            from membership_plans
            where tenant_id = ?
              and (branch_id = ? or branch_id is null)
            order by created_at asc
            limit 1
            """,
        rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
        tenantId,
        branchId);
  }

  private UUID existingMembershipId(UUID tenantId, String memberCode) {
    return jdbcTemplate.query(
        """
            select id
            from memberships
            where tenant_id = ?
              and member_code = ?
            limit 1
            """,
        rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
        tenantId,
        memberCode);
  }

  private UUID resolveMembershipId(UUID tenantId, Map<String, Object> payload) {
    final String memberCode = stringValue(payload.get("code"), null);
    if (memberCode != null && !memberCode.isBlank()) {
      final UUID membershipId = existingMembershipId(tenantId, memberCode);
      if (membershipId != null) {
        return membershipId;
      }
    }

    final String externalId = stringValue(payload.get("id"), null);
    if (externalId == null || externalId.isBlank()) {
      return null;
    }

    return jdbcTemplate.query(
        """
            select id from memberships
            where tenant_id = ? and snapshot_jsonb ->> 'id' = ?
            limit 1
            """,
        rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
        tenantId,
        externalId);
  }

  private String resolveMemberCode(UUID tenantId, UUID membershipId, Map<String, Object> payload) {
    if (membershipId != null) {
      return memberCodeByMembershipId(membershipId);
    }

    final String provided = stringValue(payload.get("code"), null);
    if (provided != null && !provided.isBlank()) {
      return provided.trim().toUpperCase();
    }

    return nextMemberCode(tenantId);
  }

  private String memberCodeByMembershipId(UUID membershipId) {
    return jdbcTemplate.query(
        "select member_code from memberships where id = ? limit 1",
        rs -> rs.next() ? rs.getString("member_code") : null,
        membershipId);
  }

  private String nextMemberCode(UUID tenantId) {
    for (int attempt = 0; attempt < 100; attempt++) {
      final String candidate = String.format("%06d", ThreadLocalRandom.current().nextInt(100_000, 1_000_000));
      if (!memberCodeExists(tenantId, candidate)) {
        return candidate;
      }
    }

    throw new ResponseStatusException(HttpStatus.CONFLICT, "No se pudo generar un codigo unico para el socio.");
  }

  private boolean memberCodeExists(UUID tenantId, String memberCode) {
    final Integer total = jdbcTemplate.query(
        """
            select count(*)
            from (
              select 1
              from memberships
              where tenant_id = ?
                and member_code = ?

              union all

              select 1
              from users
              where tenant_id = ?
                and user_type = 'MEMBER'
                and username = ?
            ) codes
            """,
        rs -> rs.next() ? rs.getInt(1) : 0,
        tenantId,
        memberCode,
        tenantId,
        memberCode);
    return total != null && total > 0;
  }

  private String qrTokenFor(String memberCode) {
    return "FGM:" + memberCode;
  }

  private String requireMemberCode(String requestedUsername) {
    if (requestedUsername == null || requestedUsername.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion invalida para socio.");
    }
    return requestedUsername;
  }

  private OffsetDateTime parseTimestamp(Object value, OffsetDateTime fallback) {
    if (value instanceof String stringValue && !stringValue.isBlank()) {
      try {
        return OffsetDateTime.parse(stringValue);
      } catch (Exception ignored) {
        try {
          return Instant.parse(stringValue).atOffset(java.time.ZoneOffset.UTC);
        } catch (Exception ignoredAgain) {
          return fallback;
        }
      }
    }
    return fallback;
  }

  private Map<String, Object> stringifyMap(Map<?, ?> rawMap) {
    final Map<String, Object> normalized = new LinkedHashMap<>();
    rawMap.forEach((key, value) -> normalized.put(String.valueOf(key), value));
    return normalized;
  }

  private double decimalValue(Object value, double fallback) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    return fallback;
  }

  private Map<String, Object> readMap(String json) {
    try {
      if (json == null || json.isBlank()) {
        return Map.of();
      }
      return objectMapper.readValue(json, MAP_TYPE);
    } catch (Exception exception) {
      return Map.of();
    }
  }

  private String writeJson(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
    } catch (Exception exception) {
      return "{}";
    }
  }

  private String stringValue(Object value, String fallback) {
    return value instanceof String string && !string.isBlank() ? string : fallback;
  }

  private int intValue(Object value, int fallback) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    return fallback;
  }

  private List<Object> listValue(Object value) {
    if (value instanceof List<?> list) {
      return new ArrayList<>(list);
    }
    return Collections.emptyList();
  }

  private MemberSummary toMemberSummary(
      String id,
      String memberCode,
      String status,
      OffsetDateTime endsAt,
      String snapshotJson) {
    final Map<String, Object> snapshot = readMap(snapshotJson);
    return new MemberSummary(
        id,
        memberCode,
        stringValue(snapshot.get("nombre"), "Miembro"),
        stringValue(snapshot.get("apellido"), "Demo"),
        status,
        endsAt.toInstant().toString());
  }

  private PaymentSummary toPaymentSummary(String id, String membershipId, double amount, OffsetDateTime paidAt) {
    return new PaymentSummary(id, membershipId, amount, paidAt.toInstant().toString());
  }

  private AttendanceSummary toAttendanceSummary(String id, String membershipId, OffsetDateTime occurredAt) {
    return new AttendanceSummary(id, membershipId, occurredAt.toInstant().toString());
  }

  private Map<String, Object> toMemberUpsert(ResultSet rs) throws SQLException {
    final Map<String, Object> snapshot = readMap(rs.getString("snapshot"));
    final Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", rs.getString("id"));
    payload.put("code", rs.getString("member_code"));
    payload.put("nombre", stringValue(snapshot.get("nombre"), "Miembro"));
    payload.put("apellido", stringValue(snapshot.get("apellido"), "Demo"));
    payload.put("telefono", stringValue(snapshot.get("telefono"), ""));
    payload.put("password", stringValue(snapshot.get("password"), rs.getString("member_code")));
    payload.put("fechaInicio", stringValue(snapshot.get("fechaInicio"), Instant.now().toString()));
    payload.put("fechaFin", rs.getObject("ends_at", OffsetDateTime.class).toInstant().toString());
    payload.put("meses", intValue(snapshot.get("meses"), 1));
    payload.put("diaPago", intValue(snapshot.get("diaPago"), 1));
    payload.put("routineCode", stringValue(snapshot.get("routineCode"), ""));
    payload.put("qrToken", stringValue(snapshot.get("qrToken"), qrTokenFor(rs.getString("member_code"))));
    payload.put("asistencias", listValue(snapshot.get("asistencias")));
    payload.put("pagos", listValue(snapshot.get("pagos")));
    payload.put("progreso", listValue(snapshot.get("progreso")));

    return Map.of(
        "entityType", "member",
        "entityId", rs.getString("id"),
        "operationType", "UPSERT",
        "payload", payload);
  }
}
