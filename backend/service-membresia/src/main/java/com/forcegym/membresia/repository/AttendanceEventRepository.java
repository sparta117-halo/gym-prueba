package com.forcegym.membresia.repository;

import com.forcegym.membresia.domain.AttendanceEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceEventRepository extends JpaRepository<AttendanceEventEntity, UUID> {

  List<AttendanceEventEntity> findTop100ByOrderByOccurredAtDesc();
}