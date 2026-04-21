package com.forcegym.app.repository;

import com.forcegym.app.domain.PaymentEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {}
