package com.forcegym.membresia.repository;

import com.forcegym.membresia.domain.PaymentEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

  List<PaymentEntity> findTop100ByOrderByPaidAtDesc();
}