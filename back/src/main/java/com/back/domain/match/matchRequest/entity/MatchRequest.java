package com.back.domain.match.matchRequest.entity;

import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.member.member.entity.Industry;
import com.back.domain.member.member.entity.Member;
import com.back.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "match_request", indexes = {
        @Index(name = "idx_match_request_status_room_id", columnList = "status, room_id")
})
public class MatchRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private ChatRoom room;
    // 한글 라벨로 DB에 저장해야 하는 값(사용자 노출용 코드성 데이터)은 AttributeConverter를 쓴다
    private Industry industry;
    private Situation situation;

    // 영문 name() 그대로 저장해도 무방한 내부 상태값은 @Enumerated(STRING)을 쓴다
    @Enumerated(EnumType.STRING)
    private MatchStatus status;

    private LocalDateTime requestedAt;

    // 매칭 엔진 내부의 동시 수정 경쟁은 Redis 분산 락(match:lock:{industry})이 전담하므로,
    // 이 필드가 그 경로에서 충돌을 방어할 일은 없다. 이 필드는 매칭 엔진 바깥 경로
    // (사용자 취소 API, 만료 배치 등)와의 충돌을 막는 최후의 안전장치로 유지한다.
    @Version
    private Long version = 0L;
    
    public MatchRequest(Member member, Situation situation) {
        this.member = member;
        this.industry = member.getIndustry();
        this.situation = situation;
        this.status = MatchStatus.PENDING;
        this.requestedAt = LocalDateTime.now();
    }

    public void matchWith(ChatRoom room) {
        this.room = room;
        this.status = MatchStatus.MATCHED;
    }

}
