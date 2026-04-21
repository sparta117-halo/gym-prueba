package com.forcegym.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forcegym.app.api.RutinasController.RoutineCatalogItem;
import com.forcegym.app.api.RutinasController.UpsertRoutineRequest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RutinaCatalogService {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public RutinaCatalogService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public List<RoutineCatalogItem> listCatalog() {
    return jdbcTemplate.query(
        """
            select r.id, r.code, r.name,
                   r.definition_jsonb::text as definition,
                   r.created_at, r.updated_at
            from routines r
            where r.tenant_id = ?
            order by r.updated_at desc, r.created_at desc
            """,
        (rs, rowNum) -> toCatalogItem(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("definition"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)),
        defaultTenantId());
  }

  public RoutineCatalogItem create(UpsertRoutineRequest request) {
    final UUID routineId = UUID.randomUUID();
    jdbcTemplate.update(
        """
            insert into routines (id, tenant_id, branch_id, code, name, definition_jsonb, created_at, updated_at)
            values (?, ?, ?, ?, ?, cast(? as jsonb), now(), now())
            """,
        routineId, defaultTenantId(), defaultBranchId(),
        normalizeCode(request.code()), request.name().trim(),
        writeJson(toDefinition(request)));
    return getById(routineId);
  }

  public RoutineCatalogItem update(UUID routineId, UpsertRoutineRequest request) {
    final int updated = jdbcTemplate.update(
        """
            update routines
            set code = ?, name = ?, definition_jsonb = cast(? as jsonb), updated_at = now()
            where id = ? and tenant_id = ?
            """,
        normalizeCode(request.code()), request.name().trim(),
        writeJson(toDefinition(request)), routineId, defaultTenantId());

    if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La rutina no existe.");
    return getById(routineId);
  }

  public void delete(UUID routineId) {
    final int deleted = jdbcTemplate.update(
        "delete from routines where id = ? and tenant_id = ?",
        routineId, defaultTenantId());
    if (deleted == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La rutina no existe.");
  }

  private RoutineCatalogItem getById(UUID routineId) {
    return jdbcTemplate.query(
        """
            select r.id, r.code, r.name,
                   r.definition_jsonb::text as definition,
                   r.created_at, r.updated_at
            from routines r
            where r.id = ? and r.tenant_id = ?
            limit 1
            """,
        rs -> rs.next()
            ? toCatalogItem(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("definition"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class))
            : null,
        routineId, defaultTenantId());
  }

  private RoutineCatalogItem toCatalogItem(
      UUID id, String code, String name, String definitionJson,
      OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    final Map<String, Object> def = readMap(definitionJson);
    final List<String> exercises = listValue(def.get("exercises"));
    final List<String> fallback = blockNames(def.get("blocks"));
    final int duration = intValue(def.get("durationMinutes"), totalBlockMinutes(def.get("blocks"), 45));
    return new RoutineCatalogItem(
        id.toString(), code, name,
        stringValue(def.get("level"), "starter"),
        intValue(def.get("daysPerWeek"), 3),
        duration,
        stringValue(def.get("objective"), ""),
        stringValue(def.get("notes"), ""),
        exercises.isEmpty() ? fallback : exercises,
        createdAt != null ? createdAt.toInstant().toString() : OffsetDateTime.now().toInstant().toString(),
        updatedAt != null ? updatedAt.toInstant().toString() : OffsetDateTime.now().toInstant().toString());
  }

  private Map<String, Object> toDefinition(UpsertRoutineRequest request) {
    final List<String> exercises = sanitizeExercises(request.exercises());
    final Map<String, Object> def = new LinkedHashMap<>();
    def.put("level", stringValue(request.level(), "starter"));
    def.put("daysPerWeek", request.daysPerWeek());
    def.put("durationMinutes", request.durationMinutes());
    def.put("objective", stringValue(request.objective(), ""));
    def.put("notes", stringValue(request.notes(), ""));
    def.put("exercises", exercises);
    def.put("blocks", exercises.stream()
        .map(e -> Map.of("name", e, "minutes", Math.max(5, request.durationMinutes() / Math.max(1, exercises.size()))))
        .toList());
    return def;
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

  private Map<String, Object> readMap(String rawJson) {
    if (rawJson == null || rawJson.isBlank()) return Map.of();
    try { return objectMapper.readValue(rawJson, MAP_TYPE); }
    catch (Exception e) { return Map.of(); }
  }

  private String writeJson(Object payload) {
    try { return objectMapper.writeValueAsString(payload); }
    catch (Exception e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al serializar.", e); }
  }

  private String normalizeCode(String value) {
    return value == null ? "" : value.trim().toUpperCase();
  }

  private String stringValue(Object value, String fallback) {
    if (value == null) return fallback;
    final String s = value.toString().trim();
    return s.isBlank() ? fallback : s;
  }

  private int intValue(Object value, int fallback) {
    if (value instanceof Number n) return n.intValue();
    if (value instanceof String s) {
      try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
    }
    return fallback;
  }

  private List<String> listValue(Object value) {
    if (!(value instanceof List<?> items)) return List.of();
    return items.stream()
        .map(i -> i == null ? "" : i.toString().trim())
        .filter(i -> !i.isBlank())
        .toList();
  }

  private List<String> blockNames(Object value) {
    if (!(value instanceof List<?> blocks)) return List.of();
    final List<String> names = new ArrayList<>();
    for (Object block : blocks) {
      if (block instanceof Map<?, ?> m) {
        final Object n = m.get("name");
        if (n != null && !n.toString().isBlank()) names.add(n.toString().trim());
      }
    }
    return names;
  }

  private int totalBlockMinutes(Object value, int fallback) {
    if (!(value instanceof List<?> blocks)) return fallback;
    int total = 0;
    for (Object block : blocks) {
      if (block instanceof Map<?, ?> m) total += intValue(m.get("minutes"), 0);
    }
    return total > 0 ? total : fallback;
  }

  private List<String> sanitizeExercises(List<String> exercises) {
    if (exercises == null) return List.of();
    return exercises.stream()
        .map(e -> e == null ? "" : e.trim())
        .filter(e -> !e.isBlank())
        .toList();
  }
}
