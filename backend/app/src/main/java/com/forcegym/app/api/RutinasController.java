package com.forcegym.app.api;

import com.forcegym.app.service.RutinaCatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rutinas")
public class RutinasController {

  private final RutinaCatalogService rutinaCatalogService;

  public RutinasController(RutinaCatalogService rutinaCatalogService) {
    this.rutinaCatalogService = rutinaCatalogService;
  }

  @GetMapping("/catalog")
  public List<RoutineCatalogItem> catalog() {
    return rutinaCatalogService.listCatalog();
  }

  @PostMapping("/catalog")
  @ResponseStatus(HttpStatus.CREATED)
  public RoutineCatalogItem create(@Valid @RequestBody UpsertRoutineRequest request) {
    return rutinaCatalogService.create(request);
  }

  @PutMapping("/catalog/{routineId}")
  public RoutineCatalogItem update(
      @PathVariable UUID routineId,
      @Valid @RequestBody UpsertRoutineRequest request) {
    return rutinaCatalogService.update(routineId, request);
  }

  @DeleteMapping("/catalog/{routineId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID routineId) {
    rutinaCatalogService.delete(routineId);
  }

  public record RoutineCatalogItem(
      String id,
      String code,
      String name,
      String level,
      int daysPerWeek,
      int durationMinutes,
      String objective,
      String notes,
      List<String> exercises,
      String createdAt,
      String updatedAt) {}

  public record UpsertRoutineRequest(
      @NotBlank(message = "El codigo es obligatorio.") String code,
      @NotBlank(message = "El nombre es obligatorio.") String name,
      @NotBlank(message = "El nivel es obligatorio.") String level,
      @Min(value = 1, message = "Los dias por semana deben ser mayores a cero.")
      @Max(value = 7, message = "Los dias por semana no pueden ser mayores a siete.")
      int daysPerWeek,
      @Min(value = 10, message = "La duracion minima es 10 minutos.") int durationMinutes,
      String objective,
      String notes,
      List<String> exercises) {}
}
