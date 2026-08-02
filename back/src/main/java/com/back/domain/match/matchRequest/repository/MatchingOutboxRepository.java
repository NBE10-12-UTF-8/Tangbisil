package com.back.domain.match.matchRequest.repository;

import com.back.domain.match.matchRequest.entity.MatchingOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MatchingOutboxRepository extends JpaRepository<MatchingOutbox, UUID> {
}