package com.forcegym.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "memberships")
public class MembershipEntity {

  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "branch_id", nullable = false)
  private UUID branchId;

  @Column(name = "member_user_id", nullable = false)
  private UUID memberUserId;

  @Column(name = "member_code", nullable = false)
  private String memberCode;

  @Column(nullable = false)
  private String status;

  @Column(name = "starts_at", nullable = false)
  private OffsetDateTime startsAt;

  @Column(name = "ends_at", nullable = false)
  private OffsetDateTime endsAt;

  protected MembershipEntity() {}

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getBranchId() { return branchId; }
  public UUID getMemberUserId() { return memberUserId; }
  public String getMemberCode() { return memberCode; }
  public String getStatus() { return status; }
  public OffsetDateTime getStartsAt() { return startsAt; }
  public OffsetDateTime getEndsAt() { return endsAt; }
}
