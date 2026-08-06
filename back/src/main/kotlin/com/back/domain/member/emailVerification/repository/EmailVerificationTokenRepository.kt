package com.back.domain.member.emailVerification.repository

import com.back.domain.member.emailVerification.entity.EmailVerificationToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationToken, Long> {
    fun findTopByEmailOrderByCreatedAtDesc(email: String): EmailVerificationToken?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM EmailVerificationToken t WHERE t.email = :email")
    fun deleteByEmail(@Param("email") email: String)
}
