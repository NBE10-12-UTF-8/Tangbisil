package com.back.domain.member.member.repository

import com.back.domain.dashboard.dashboard.dto.IndustryStatisticsDto
import com.back.domain.member.member.entity.AuthProvider
import com.back.domain.member.member.entity.Member
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface MemberRepository : JpaRepository<Member, Long> {
    fun findByEmail(email: String): Member?
    fun findByUuid(uuid: UUID): Member?
    fun findByRefreshToken(refreshToken: UUID): Member?
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): Member?

    @Query("SELECT new com.back.domain.dashboard.dashboard.dto.IndustryStatisticsDto(m.industry, COUNT(m)) FROM Member m GROUP BY m.industry")
    fun countByIndustry(): List<IndustryStatisticsDto>

    fun findAllByIsSuspended(isSuspended: Boolean, pageable: Pageable): Page<Member>

    // 기간별 산업군 가입 통계 - 종료일 다음날 00:00을 배타적 상한으로 써서
    // 시/분/초 단위 오차 없이 종료일 하루 전체를 온전히 포함한다.
    @Query(
        """
       SELECT new com.back.domain.dashboard.dashboard.dto.IndustryStatisticsDto(m.industry, COUNT(m))
       FROM Member m
       WHERE m.createdAt >= :start AND m.createdAt < :end AND m.role != 'ADMIN'
       GROUP BY m.industry
       """
    )
    fun countByIndustryAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime
    ): List<IndustryStatisticsDto>
}
