package com.forcegym.app.repository;

import com.forcegym.app.domain.AttendanceEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceEventRepository extends JpaRepository<AttendanceEventEntity, UUID> {}
