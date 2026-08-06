package com.back.domain.match.matchRequest.entity

import com.back.domain.member.member.entity.Industry
import com.back.global.jpa.entity.BaseEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "matching_outbox")
class MatchingOutbox private constructor(
    matchRequestId: UUID,
    industry: Industry,
    situation: Situation,
    status: OutboxStatus,
    retryCount: Int,
    requestedAtEpochMilli: Long
) : BaseEntity() {

    @Column(nullable = false)
    var matchRequestId: UUID = matchRequestId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var industry: Industry = industry
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var situation: Situation = situation
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OutboxStatus = status
        protected set

    @Column(nullable = false)
    var retryCount: Int = retryCount
        protected set

    @Column(nullable = false)
    // Redis 재적재 시 최초 신청 시각을 유지하여 매칭 대기열의 우선순위 역전을 방지함
    var requestedAtEpochMilli: Long = requestedAtEpochMilli
        protected set

    enum class OutboxStatus { INIT, SUCCESS, FAIL }

    // Redis 적재가 최종 완료되었을 때 SUCCESS 상태로 마킹함
    fun markSuccess() {
        status = OutboxStatus.SUCCESS
    }

    // Redis 적재 실패 시 FAIL 상태로 마킹하고 재시도 횟수를 1 증가시킴
    fun markFailed() {
        status = OutboxStatus.FAIL
        retryCount += 1
    }

    companion object {
        @JvmStatic
        fun create(matchRequestId: UUID, industry: Industry, situation: Situation, requestedAtEpochMilli: Long): MatchingOutbox =
            MatchingOutbox(matchRequestId, industry, situation, OutboxStatus.INIT, 0, requestedAtEpochMilli)
    }
}
