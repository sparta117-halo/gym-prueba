package com.forcegym.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "attendance_events")
public class AttendanceEventEntity {

  @Id
  private UUID id;

  @Column(name = "membership_id", nullable = false)
  private UUID membershipId;

  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt;

  @Column(nullable = false)
  private String source;

  protected AttendanceEventEntity() {}

  public UUID getId() { return id; }
  public UUID getMembershipId() { return membershipId; }
  public OffsetDateTime getOccurredAt() { return occurredAt; }
  public String getSource() { return source; }
}
