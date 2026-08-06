package com.back.domain.chat.chatRoomParticipant.repository

import com.back.domain.chat.chatRoom.entity.ChatRoomStatus
import com.back.domain.chat.chatRoomParticipant.entity.ChatRoomParticipant
import com.back.domain.member.member.entity.Member
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ChatRoomParticipantRepository : JpaRepository<ChatRoomParticipant, Long> {
    fun existsByChatRoomIdAndMemberId(roomId: Long, memberId: Long): Boolean

    fun findByChatRoomIdAndMemberId(roomId: Long, memberId: Long): ChatRoomParticipant?

    fun findByUuid(uuid: UUID): ChatRoomParticipant?

    // 참여자 조회 시 Member까지 fetch join - ChatMessageService.sendMessage에서
    // 참여자별 member.getId()/getEmail()을 순회 참조하므로 N+1 방지 필요
    @Query(
        """
           SELECT p FROM ChatRoomParticipant p
           JOIN FETCH p.member
           WHERE p.chatRoom.id = :chatRoomId
           """
    )
    fun findByChatRoomId(@Param("chatRoomId") chatRoomId: Long): List<ChatRoomParticipant>

    // 여러 채팅방의 참여자를 한 번에 조회 - 매칭 이력(N개 방)에서 봇 여부 배치 판별 시
    // 방마다 개별 쿼리 나가는 N+1 방지용
    @Query(
        """
       SELECT p FROM ChatRoomParticipant p
       JOIN FETCH p.member
       JOIN FETCH p.chatRoom
       WHERE p.chatRoom.id IN :chatRoomIds
       """
    )
    fun findByChatRoomIdIn(@Param("chatRoomIds") chatRoomIds: Collection<Long>): List<ChatRoomParticipant>

    fun findByMemberIdAndChatRoomStatus(memberId: Long, status: ChatRoomStatus): ChatRoomParticipant?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ChatRoomParticipant p WHERE p.member = :member")
    fun deleteByMember(@Param("member") member: Member)
}
