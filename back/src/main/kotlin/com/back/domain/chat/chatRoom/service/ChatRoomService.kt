package com.back.domain.chat.chatRoom.service

import com.back.domain.bot.BotAccounts.isBotEmail
import com.back.domain.chat.chatRoom.entity.ChatRoom
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus
import com.back.domain.chat.chatRoom.event.ChatRoomClosedEvent
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository
import com.back.domain.chat.chatRoomParticipant.service.ChatRoomParticipantService
import com.back.domain.member.member.entity.Member
import com.back.global.exception.ServiceException
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ChatRoomService(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomParticipantService: ChatRoomParticipantService,
    private val redisTemplate: RedisTemplate<String, String>,
    private val eventPublisher: ApplicationEventPublisher
) {
    companion object {
        private val log = LoggerFactory.getLogger(ChatRoomService::class.java)
    }

    fun getChatRoom(roomId: UUID): ChatRoom =
        chatRoomRepository.findByUuid(roomId) ?: throw ServiceException("404-1", "채팅방을 찾을 수 없습니다.")

    fun hasBotParticipant(roomId: Long): Boolean =
        chatRoomParticipantService.getParticipants(roomId)
            .map { it.member }
            .any { it.email.isBotEmail() }

    @Transactional
    fun createChatRoom(members: List<Member>): ChatRoom {
        val chatRoom = ChatRoom(ChatRoomStatus.ACTIVE, members.size)
        val savedRoom = chatRoomRepository.save(chatRoom)

        chatRoomParticipantService.createParticipants(savedRoom, members)
        return savedRoom
    }

    @Transactional
    fun closeChatRoom(roomId: UUID, actor: Member): ChatRoom {
        val chatRoom = getChatRoom(roomId)

        chatRoomParticipantService.validateAccess(chatRoom.id, actor)

        if (chatRoom.status == ChatRoomStatus.CLOSED) {
            throw ServiceException("409-1", "이미 종료된 채팅방입니다.")
        }

        chatRoom.close()

        try {
            val key = "chat:room:${chatRoom.uuid}:messages"
            redisTemplate.delete(key)
        } catch (e: Exception) {
            log.error("대화방 종료 후 Redis 캐시 삭제 실패 - roomId: {}", roomId, e)
        }

        // 상대방은 이 요청을 모르므로, 직접 메시지를 보내야만 종료를 알게 되는 걸 막기 위해
        // 실시간으로 알려준다. WebSocket 브로드캐스트는 커밋 이후 비동기로 처리해야 하므로
        // (ChatMessageEventHandler와 동일한 패턴) 여기선 직접 보내지 않고 이벤트만 발행한다 —
        // ChatRoomService가 SimpMessagingTemplate을 직접 물면 WebSocketConfig(→
        // StompAuthChannelInterceptor→MemberService→MatchRequestService→ChatRoomService)로
        // 이어지는 빈 순환참조가 생긴다.
        val otherMemberUuids = chatRoomParticipantService.getParticipants(chatRoom.id)
            .filter { it.member.id != actor.id }
            .map { it.member.uuid.toString() }
        eventPublisher.publishEvent(ChatRoomClosedEvent(chatRoom.uuid, otherMemberUuids))

        return chatRoom
    }

    fun findActiveChatRoom(actor: Member): ChatRoom? =
        chatRoomParticipantService.findActiveChatRoomByMember(actor)

    // 여러 채팅방의 봇 참여 여부를 한 번에 조회 (roomId -> isBot)
    fun hasBotParticipantMap(roomIds: Collection<Long>): Map<Long, Boolean> {
        if (roomIds.isEmpty()) {
            return emptyMap()
        }

        val result = roomIds.associateWithTo(HashMap()) { false }

        chatRoomParticipantService.getParticipantsByRoomIds(roomIds)
            .filter { it.member.email.isBotEmail() }
            .forEach { result[it.chatRoom.id] = true }

        return result
    }
}
