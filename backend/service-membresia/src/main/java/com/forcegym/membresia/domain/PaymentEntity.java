package com.forcegym.membresia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentEntity {

  @Id
  private UUID id;

  @Column(name = "membership_id", nullable = false)
  private UUID membershipId;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(name = "paid_at", nullable = false)
  private OffsetDateTime paidAt;

  protected PaymentEntity() {}

  public UUID getId() {
    return id;
  }

  public UUID getMembershipId() {
    return membershipId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public OffsetDateTime getPaidAt() {
    return paidAt;
  }
}