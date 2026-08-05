package com.back.global.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(PROTECTED)
    private Long id;

    // 외부 노출용 공개 식별자. DB PK(id)는 내부 조인/인덱싱 성능을 위해 Long을 쓰고,
    // API 응답·URL 경로변수·JWT 등 외부에 노출되는 자리엔 이 uuid를 쓴다(순번 노출 방지).
    @Column(nullable = false, unique = true, updatable = false)
    @Setter(PROTECTED)
    private UUID uuid = UUID.randomUUID();

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime modifiedAt;

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseEntity that = (BaseEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}