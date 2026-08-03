package com.back.domain.member.member.repository;

import com.back.domain.dashboard.dashboard.dto.IndustryStatisticsDto;
import com.back.domain.member.member.entity.AuthProvider;
import com.back.domain.member.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, UUID> {
    Optional<Member> findByEmail(String email);
    Optional<Member> findByRefreshToken(UUID refreshToken);
    Optional<Member> findByProviderAndProviderId(AuthProvider provider, String providerId);
    @Query("SELECT new com.back.domain.dashboard.dashboard.dto.IndustryStatisticsDto(m.industry, COUNT(m)) FROM Member m GROUP BY m.industry")
    List<IndustryStatisticsDto> countByIndustry();

    Page<Member> findAllByIsSuspended(boolean isSuspended, Pageable pageable);
}