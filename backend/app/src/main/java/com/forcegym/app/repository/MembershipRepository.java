package com.forcegym.app.repository;

import com.forcegym.app.domain.MembershipEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<MembershipEntity, UUID> {}
