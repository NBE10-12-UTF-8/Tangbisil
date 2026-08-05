package com.back.domain.chat.chatRoomMessage.repository;

import com.back.domain.chat.chatRoomMessage.entity.ChatMessage;
import com.back.domain.member.member.entity.Member;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Optional<ChatMessage> findByUuid(UUID uuid);

    @Modifying
    @Query("""
            DELETE FROM ChatMessage m
            WHERE m.chatRoom.id IN (
                SELECT r.id FROM ChatRoom r
                WHERE r.status = com.back.domain.chat.chatRoom.entity.ChatRoomStatus.CLOSED
                AND r.closedAt <= :threshold
            )
            """)
    int deleteMessagesInRoomsClosedBefore(@Param("threshold") LocalDateTime threshold);

    // 봇 응답 생성용 - 전체 조회 후 메모리에서 자르지 않고 DB에서 최근 N개만 가져온다.
    // JPQL은 LIMIT 문법이 없어서 Pageable로 개수를 제한한다 (예: PageRequest.of(0, 12)).
    @Query("""
       SELECT m FROM ChatMessage m
       JOIN FETCH m.participant p
       JOIN FETCH p.member
       WHERE m.chatRoom.id = :roomId
       ORDER BY m.createdAt DESC
       """)
    List<ChatMessage> findRecentByChatRoomId(@Param("roomId") Long roomId, Pageable pageable);

    // 신고 메시지 작성일시(createdAt) 기준 그 이전의 대화들을 최근 순서(DESC)로 최대 30개만 제한 조회
    // N+1 문제 해결: 30개 대화를 복사할 때 지연로딩 추가 쿼리(N+1) 방지를 위해 조인 페치 선언
    @EntityGraph(attributePaths = {"participant", "participant.member"})
    List<ChatMessage> findTop30ByChatRoomIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(Long chatRoomId, LocalDateTime createdAt);

    @Query("""
           SELECT m FROM ChatMessage m
           JOIN FETCH m.participant p
           JOIN FETCH p.member
           WHERE m.chatRoom.id = :roomId
           ORDER BY m.createdAt ASC
           """)
    List<ChatMessage> findByChatRoomIdOrderByCreatedAtAsc(@Param("roomId") Long roomId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
       DELETE FROM ChatMessage m 
       WHERE m.participant IN (
           SELECT p FROM ChatRoomParticipant p WHERE p.member = :member
       )
       """)
    void deleteByParticipantMember(@Param("member") Member member);
}