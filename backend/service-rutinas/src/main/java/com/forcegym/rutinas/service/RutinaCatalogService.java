package com.forcegym.rutinas.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forcegym.rutinas.api.RutinasController.RoutineCatalogItem;
import com.forcegym.rutinas.api.RutinasController.UpsertRoutineRequest;
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
            select
              r.id,
              r.code,
              r.name,
              r.definition_jsonb::text as definition,
              r.created_at,
              r.updated_at
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
            insert into routines (
              id,
              tenant_id,
              branch_id,
              code,
              name,
              definition_jsonb,
              created_at,
              updated_at
            ) values (?, ?, ?, ?, ?, cast(? as jsonb), now(), now())
            """,
        routineId,
        defaultTenantId(),
        defaultBranchId(),
        normalizeCode(request.code()),
        request.name().trim(),
        writeJson(toDefinition(request)));

    return getById(routineId);
  }

  public RoutineCatalogItem update(UUID routineId, UpsertRoutineRequest request) {
    final int updated = jdbcTemplate.update(
        """
            update routines
            set code = ?,
                name = ?,
                definition_jsonb = cast(? as jsonb),
                updated_at = now()
            where id = ?
              and tenant_id = ?
            """,
        normalizeCode(request.code()),
        request.name().trim(),
        writeJson(toDefinition(request)),
        routineId,
        defaultTenantId());

    if (updated == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La rutina no existe.");
    }

    return getById(routineId);
  }

  public void delete(UUID routineId) {
    final int deleted = jdbcTemplate.update(
        "delete from routines where id = ? and tenant_id = ?",
        routineId,
        defaultTenantId());

    if (deleted == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La rutina no existe.");
    }
  }

  private RoutineCatalogItem getById(UUID routineId) {
    return jdbcTemplate.query(
        """
            select
              r.id,
              r.code,
              r.name,
              r.definition_jsonb::text as definition,
              r.created_at,
              r.updated_at
            from routines r
            where r.id = ?
              and r.tenant_id = ?
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
        routineId,
        defaultTenantId());
  }

  private RoutineCatalogItem toCatalogItem(
      UUID id,
      String code,
      String name,
      String definitionJson,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    final Map<String, Object> definition = readMap(definitionJson);
    final List<String> exercises = listValue(definition.get("exercises"));
    final List<String> fallbackBlocks = blockNames(definition.get("blocks"));
    final int durationMinutes = intValue(definition.get("durationMinutes"), totalBlockMinutes(definition.get("blocks"), 45));

    return new RoutineCatalogItem(
        id.toString(),
        code,
        name,
        stringValue(definition.get("level"), "starter"),
        intValue(definition.get("daysPerWeek"), 3),
        durationMinutes,
        stringValue(definition.get("objective"), ""),
        stringValue(definition.get("notes"), ""),
        exercises.isEmpty() ? fallbackBlocks : exercises,
        createdAt != null ? createdAt.toInstant().toString() : OffsetDateTime.now().toInstant().toString(),
        updatedAt != null ? updatedAt.toInstant().toString() : OffsetDateTime.now().toInstant().toString());
  }

  private Map<String, Object> toDefinition(UpsertRoutineRequest request) {
    final List<String> exercises = sanitizeExercises(request.exercises());
    final Map<String, Object> definition = new LinkedHashMap<>();
    definition.put("level", stringValue(request.level(), "starter"));
    definition.put("daysPerWeek", request.daysPerWeek());
    definition.put("durationMinutes", request.durationMinutes());
    definition.put("objective", stringValue(request.objective(), ""));
    definition.put("notes", stringValue(request.notes(), ""));
    definition.put("exercises", exercises);
    definition.put(
        "blocks",
        exercises.stream()
            .map(exercise -> Map.of("name", exercise, "minutes", Math.max(5, request.durationMinutes() / Math.max(1, exercises.size()))))
            .toList());
    return definition;
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
    if (rawJson == null || rawJson.isBlank()) {
      return Map.of();
    }

    try {
      return objectMapper.readValue(rawJson, MAP_TYPE);
    } catch (Exception exception) {
      return Map.of();
    }
  }

  private String writeJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (Exception exception) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo serializar la rutina.", exception);
    }
  }

  private String normalizeCode(String value) {
    return value == null ? "" : value.trim().toUpperCase();
  }

  private String stringValue(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }

    final String normalized = value.toString().trim();
    return normalized.isBlank() ? fallback : normalized;
  }

  private int intValue(Object value, int fallback) {
    if (value instanceof Number number) {
      return number.intValue();
    }

    if (value instanceof String stringValue) {
      try {
        return Integer.parseInt(stringValue.trim());
      } catch (NumberFormatException ignored) {
        return fallback;
      }
    }

    return fallback;
  }

  private List<String> listValue(Object value) {
    if (!(value instanceof List<?> items)) {
      return List.of();
    }

    return items.stream()
        .map(item -> item == null ? "" : item.toString().trim())
        .filter(item -> !item.isBlank())
        .toList();
  }

  private List<String> blockNames(Object value) {
    if (!(value instanceof List<?> blocks)) {
      return List.of();
    }

    final List<String> names = new ArrayList<>();
    for (Object block : blocks) {
      if (block instanceof Map<?, ?> blockMap) {
        final Object name = blockMap.get("name");
        if (name != null && !name.toString().isBlank()) {
          names.add(name.toString().trim());
        }
      }
    }
    return names;
  }

  private int totalBlockMinutes(Object value, int fallback) {
    if (!(value instanceof List<?> blocks)) {
      return fallback;
    }

    int total = 0;
    for (Object block : blocks) {
      if (block instanceof Map<?, ?> blockMap) {
        total += intValue(blockMap.get("minutes"), 0);
      }
    }
    return total > 0 ? total : fallback;
  }

  private List<String> sanitizeExercises(List<String> exercises) {
    if (exercises == null) {
      return List.of();
    }

    return exercises.stream()
        .map(exercise -> exercise == null ? "" : exercise.trim())
        .filter(exercise -> !exercise.isBlank())
        .toList();
  }
}