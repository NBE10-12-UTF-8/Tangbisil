package com.back.domain.member.member.entity

import com.back.global.jpa.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "Member", uniqueConstraints = [UniqueConstraint(columnNames = ["provider", "provider_id"])])
class Member() : BaseEntity() {
    @Column(unique = true)
    var email: String? = null
        protected set

    var password: String? = null
        protected set

    var industry: Industry? = null
        protected set

    var role: String? = null // "USER", "ADMIN"
        protected set

    var isSuspended: Boolean = false
        protected set

    @Column(unique = true)
    var refreshToken: UUID? = null
        protected set

    var refreshTokenExpiresAt: LocalDateTime? = null
        protected set

    @Enumerated(EnumType.STRING)
    var provider: AuthProvider? = null
        protected set

    var providerId: String? = null
        protected set

    // "is" 접두사가 없으면 Kotlin이 getEmailVerified()를 생성해 Lombok이 만들던
    // isEmailVerified() 관례와 안 맞는다. 단, DB 컬럼명은 원래 Java 필드명(emailVerified) 그대로
    // email_verified라 Hibernate의 기본 네이밍 전략(is_email_verified)과 어긋나 명시 지정이 필요하다.
    @Column(name = "email_verified")
    var isEmailVerified: Boolean = false
        protected set

    var consentedAt: LocalDateTime? = null
        protected set

    // 인증 필터/Rq에서 매 요청마다 DB 재조회 없이 가볍게 재구성하는 용도.
    // uuid를 세팅하지 않으면 BaseEntity 필드 초기화(UUID.randomUUID())가 그대로 남아
    // 진짜 공개 식별자와 다른 값이 되므로, 실제 값을 반드시 함께 넘겨야 한다.
    constructor(id: Long, uuid: UUID, email: String, role: String) : this() {
        setId(id)
        setUuid(uuid)
        this.email = email
        this.role = role
    }

    constructor(email: String, password: String, industry: Industry?, role: String) : this() {
        this.email = email
        this.password = password
        this.industry = industry
        this.role = role
        this.isSuspended = false
        this.provider = AuthProvider.LOCAL
        this.isEmailVerified = true
    }

    fun updateRefreshToken(refreshToken: UUID?) {
        this.refreshToken = refreshToken
        this.refreshTokenExpiresAt = if (refreshToken != null) LocalDateTime.now().plusMonths(1) else null
    }

    val isAdmin: Boolean
        get() = role == "ADMIN"

    fun getAuthorities(): Collection<GrantedAuthority> =
        listOf(SimpleGrantedAuthority("ROLE_$role"))

    fun updateIndustry(industry: Industry) {
        this.industry = industry
    }

    fun toggleSuspended() {
        isSuspended = !isSuspended
    }

    fun updatePassword(encodedPassword: String) {
        this.password = encodedPassword
    }

    fun markConsented() {
        consentedAt = LocalDateTime.now()
    }

    companion object {
        @JvmStatic
        fun ofOAuth(email: String, provider: AuthProvider, providerId: String): Member {
            val member = Member()
            member.email = email
            member.provider = provider
            member.providerId = providerId
            member.role = "USER"
            member.isEmailVerified = true
            return member
        }
    }
}
