package com.back.domain.match.matchRequest.repository;

import com.back.domain.match.matchRequest.entity.MatchingOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface MatchingOutboxRepository extends JpaRepository<MatchingOutbox, Long> {

    // retryOutboxEvents 재시도 대상 조회 - 아웃박스 테이블은 시간이 지날수록 계속 쌓이므로,
    // findAll()로 전체를 메모리에 올리지 않고 DB에서 조건에 맞는 행만 조회한다.
    List<MatchingOutbox> findByStatusInAndRetryCountLessThan(
            Collection<MatchingOutbox.OutboxStatus> statuses, int retryCount);
}