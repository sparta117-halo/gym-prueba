package com.forcegym.membresia.repository;

import com.forcegym.membresia.domain.MembershipEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<MembershipEntity, UUID> {

  List<MembershipEntity> findTop50ByOrderByEndsAtAsc();
}