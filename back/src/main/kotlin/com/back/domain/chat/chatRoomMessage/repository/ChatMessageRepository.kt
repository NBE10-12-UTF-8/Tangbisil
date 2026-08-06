package com.back.domain.chat.chatRoomMessage.repository

import com.back.domain.chat.chatRoomMessage.entity.ChatMessage
import com.back.domain.member.member.entity.Member
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {

    fun findByUuid(uuid: UUID): ChatMessage?

    @Modifying
    @Query(
        """
            DELETE FROM ChatMessage m
            WHERE m.chatRoom.id IN (
                SELECT r.id FROM ChatRoom r
                WHERE r.status = com.back.domain.chat.chatRoom.entity.ChatRoomStatus.CLOSED
                AND r.closedAt <= :threshold
            )
            """
    )
    fun deleteMessagesInRoomsClosedBefore(@Param("threshold") threshold: LocalDateTime): Int

    // 봇 응답 생성용 - 전체 조회 후 메모리에서 자르지 않고 DB에서 최근 N개만 가져온다.
    // JPQL은 LIMIT 문법이 없어서 Pageable로 개수를 제한한다 (예: PageRequest.of(0, 12)).
    @Query(
        """
       SELECT m FROM ChatMessage m
       JOIN FETCH m.participant p
       JOIN FETCH p.member
       WHERE m.chatRoom.id = :roomId
       ORDER BY m.createdAt DESC
       """
    )
    fun findRecentByChatRoomId(@Param("roomId") roomId: Long, pageable: Pageable): List<ChatMessage>

    // 신고 메시지 작성일시(createdAt) 기준 그 이전의 대화들을 최근 순서(DESC)로 최대 30개만 제한 조회
    // N+1 문제 해결: 30개 대화를 복사할 때 지연로딩 추가 쿼리(N+1) 방지를 위해 조인 페치 선언
    @EntityGraph(attributePaths = ["participant", "participant.member"])
    fun findTop30ByChatRoomIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
        chatRoomId: Long,
        createdAt: LocalDateTime
    ): List<ChatMessage>

    @Query(
        """
           SELECT m FROM ChatMessage m
           JOIN FETCH m.participant p
           JOIN FETCH p.member
           WHERE m.chatRoom.id = :roomId
           ORDER BY m.createdAt ASC
           """
    )
    fun findByChatRoomIdOrderByCreatedAtAsc(@Param("roomId") roomId: Long): List<ChatMessage>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
       DELETE FROM ChatMessage m
       WHERE m.participant IN (
           SELECT p FROM ChatRoomParticipant p WHERE p.member = :member
       )
       """
    )
    fun deleteByParticipantMember(@Param("member") member: Member)
}
