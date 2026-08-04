package com.back.domain.match.matchRequest.entity;

import com.back.domain.member.member.entity.Industry;
import com.back.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "matching_outbox")
public class MatchingOutbox extends BaseEntity {

    @Column(nullable = false)
    private UUID matchRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Industry industry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Situation situation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    // Redis 재적재 시 최초 신청 시각을 유지하여 매칭 대기열의 우선순위 역전을 방지함
    private long requestedAtEpochMilli;

    public enum OutboxStatus {
        INIT, SUCCESS, FAIL
    }

    protected MatchingOutbox() {}

    private MatchingOutbox(UUID matchRequestId, Industry industry, Situation situation,
                           OutboxStatus status, int retryCount, long requestedAtEpochMilli) {
        this.matchRequestId = matchRequestId;
        this.industry = industry;
        this.situation = situation;
        this.status = status;
        this.retryCount = retryCount;
        this.requestedAtEpochMilli = requestedAtEpochMilli;
    }

    public static MatchingOutbox create(UUID matchRequestId, Industry industry, Situation situation, long requestedAtEpochMilli) {
        return new MatchingOutbox(matchRequestId, industry, situation, OutboxStatus.INIT, 0, requestedAtEpochMilli);
    }

    public UUID getMatchRequestId() {
        return matchRequestId;
    }

    public Industry getIndustry() {
        return industry;
    }

    public Situation getSituation() {
        return situation;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public long getRequestedAtEpochMilli() {
        return requestedAtEpochMilli;
    }

    // Redis 적재가 최종 완료되었을 때 SUCCESS 상태로 마킹함
    public void markSuccess() {
        this.status = OutboxStatus.SUCCESS;
    }

    // Redis 적재 실패 시 FAIL 상태로 마킹하고 재시도 횟수를 1 증가시킴
    public void markFailed() {
        this.status = OutboxStatus.FAIL;
        this.retryCount += 1;
    }
}