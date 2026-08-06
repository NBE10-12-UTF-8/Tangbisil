package com.back.domain.match.matchRequest.entity

import com.back.domain.chat.chatRoom.entity.ChatRoom
import com.back.domain.member.member.entity.Industry
import com.back.domain.member.member.entity.Member
import com.back.global.jpa.entity.BaseEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "match_request",
    indexes = [Index(name = "idx_match_request_status_room_id", columnList = "status, room_id")]
)
class MatchRequest(
    member: Member,
    situation: Situation
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    var member: Member = member
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    var room: ChatRoom? = null
        protected set

    // 한글 라벨로 DB에 저장해야 하는 값(사용자 노출용 코드성 데이터)은 AttributeConverter를 쓴다.
    // 호출부(MatchRequestService.create)가 이미 member.industry != null을 검증한 뒤에만 생성하므로
    // 여기서 non-null로 스냅샷을 떠두면, 이후 매칭 로직 전체에서 member를 다시 안 타도 되고
    // member.industry가 nullable이라 생기는 스마트 캐스트 문제도 피할 수 있다.
    var industry: Industry = member.industry
        ?: error("산업군이 설정되지 않은 회원으로는 매칭 요청을 생성할 수 없습니다.")
        protected set

    var situation: Situation = situation
        protected set

    // 영문 name() 그대로 저장해도 무방한 내부 상태값은 @Enumerated(STRING)을 쓴다
    @Enumerated(EnumType.STRING)
    var status: MatchStatus = MatchStatus.PENDING
        protected set

    var requestedAt: LocalDateTime = LocalDateTime.now()
        protected set

    // 매칭 엔진 내부의 동시 수정 경쟁은 Redis 분산 락(match:lock:{industry})이 전담하므로,
    // 이 필드가 그 경로에서 충돌을 방어할 일은 없다. 이 필드는 매칭 엔진 바깥 경로
    // (사용자 취소 API, 만료 배치 등)와의 충돌을 막는 최후의 안전장치로 유지한다.
    @Version
    var version: Long = 0L
        protected set

    fun matchWith(room: ChatRoom) {
        this.room = room
        this.status = MatchStatus.MATCHED
    }
}
