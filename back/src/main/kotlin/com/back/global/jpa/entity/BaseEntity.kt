package com.back.global.jpa.entity

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime
import java.util.Objects
import java.util.UUID

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    // 외부 노출용 공개 식별자. DB PK(id)는 내부 조인/인덱싱 성능을 위해 Long을 쓰고,
    // API 응답·URL 경로변수·JWT 등 외부에 노출되는 자리엔 이 uuid를 쓴다(순번 노출 방지).
    @Column(nullable = false, unique = true, updatable = false)
    var uuid: UUID = UUID.randomUUID()
        protected set

    @CreatedDate
    var createdAt: LocalDateTime? = null
        protected set

    @LastModifiedDate
    var modifiedAt: LocalDateTime? = null
        protected set

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || javaClass != other.javaClass) return false
        other as BaseEntity
        return id == other.id
    }

    override fun hashCode(): Int = Objects.hashCode(id)
}
