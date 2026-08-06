package com.back.domain.member.passwordReset.repository

import com.back.domain.member.passwordReset.entity.PasswordResetToken
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findTopByEmailOrderByCreatedAtDesc(email: String): PasswordResetToken?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PasswordResetToken t WHERE t.email = :email")
    fun deleteByEmail(@Param("email") email: String)

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :now")
    fun deleteAllExpiredBefore(@Param("now") now: LocalDateTime)
}
