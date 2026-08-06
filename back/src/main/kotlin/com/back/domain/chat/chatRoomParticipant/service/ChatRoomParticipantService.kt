package com.back.domain.chat.chatRoomParticipant.service

import com.back.domain.chat.chatRoom.entity.ChatRoom
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus
import com.back.domain.chat.chatRoomParticipant.entity.ChatRoomParticipant
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository
import com.back.domain.member.member.entity.Member
import com.back.global.exception.ServiceException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ChatRoomParticipantService(
    private val chatRoomParticipantRepository: ChatRoomParticipantRepository
) {
    fun validateAccess(roomId: Long, actor: Member) {
        if (actor.isAdmin) {
            return
        }

        val isParticipant = chatRoomParticipantRepository.existsByChatRoomIdAndMemberId(roomId, actor.id)
        if (!isParticipant) {
            throw ServiceException("403-1", "접근 권한이 없습니다.")
        }
    }

    @Transactional
    fun createParticipants(chatRoom: ChatRoom, members: List<Member>) {
        for (member in members) {
            val participant = ChatRoomParticipant(chatRoom, member, "익명의 동료")
            chatRoomParticipantRepository.save(participant)
        }
    }

    fun findActiveChatRoomByMember(member: Member): ChatRoom? =
        chatRoomParticipantRepository.findByMemberIdAndChatRoomStatus(member.id, ChatRoomStatus.ACTIVE)?.chatRoom

    fun isParticipant(roomId: Long, memberId: Long): Boolean =
        chatRoomParticipantRepository.existsByChatRoomIdAndMemberId(roomId, memberId)

    // 이 방의 전체 참여자 목록 조회 (member까지 fetch join 되어있어 N+1 없음)
    fun getParticipants(roomId: Long): List<ChatRoomParticipant> =
        chatRoomParticipantRepository.findByChatRoomId(roomId)

    // 여러 방의 참여자를 한 번에 조회 (매칭 이력처럼 N개 방을 다룰 때 N+1 방지용)
    fun getParticipantsByRoomIds(roomIds: Collection<Long>): List<ChatRoomParticipant> {
        if (roomIds.isEmpty()) {
            return emptyList()
        }
        return chatRoomParticipantRepository.findByChatRoomIdIn(roomIds)
    }

    @Transactional
    fun deleteAllByMember(member: Member) {
        chatRoomParticipantRepository.deleteByMember(member)
    }
}
