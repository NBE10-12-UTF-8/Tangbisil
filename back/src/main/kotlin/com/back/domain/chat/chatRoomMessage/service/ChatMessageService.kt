package com.back.domain.chat.chatRoomMessage.service

import com.back.domain.bot.BotAccounts.isBotEmail
import com.back.domain.bot.BotReplyTriggerEvent
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository
import com.back.domain.chat.chatRoomMessage.dto.ChatRoomMessageResponseDto
import com.back.domain.chat.chatRoomMessage.dto.RedisChatMessageDto
import com.back.domain.chat.chatRoomMessage.entity.ChatMessage
import com.back.domain.chat.chatRoomMessage.event.ChatMessageSentEvent
import com.back.domain.chat.chatRoomMessage.repository.ChatMessageRepository
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository
import com.back.domain.member.member.entity.Member
import com.back.global.exception.ServiceException
import com.back.standard.util.Ut
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ChatMessageService(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomParticipantRepository: ChatRoomParticipantRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val redisTemplate: RedisTemplate<String, String>
) {
    companion object {
        private val log = LoggerFactory.getLogger(ChatMessageService::class.java)
    }

    @Transactional
    fun sendMessage(roomId: Long, sender: Member, content: String?): ChatRoomMessageResponseDto {
        if (content.isNullOrBlank()) {
            throw ServiceException("400-1", "메시지 내용을 입력해주세요.")
        }
        if (content.length > 500) {
            throw ServiceException("400-2", "메시지는 500자를 초과할 수 없습니다.")
        }

        val chatRoom = chatRoomRepository.findById(roomId)
            .orElseThrow { ServiceException("404-1", "채팅방을 찾을 수 없습니다.") }

        if (chatRoom.status == ChatRoomStatus.CLOSED) {
            throw ServiceException("409-1", "종료된 채팅방에는 메시지를 보낼 수 없습니다.")
        }

        // 채팅방 참여자를 한 번만 조회해서 발신자 검증 + 봇 참여 여부 확인에 재사용
        val participants = chatRoomParticipantRepository.findByChatRoomId(roomId)

        val participant = participants.firstOrNull { it.member.id == sender.id }
            ?: throw ServiceException("403-1", "채팅방 참여자만 메시지를 보낼 수 있습니다.")

        val message = chatMessageRepository.save(ChatMessage(chatRoom, participant, content))
        val cacheDto = RedisChatMessageDto(message)

        // 실제 Redis 캐시 적재는 이 이벤트를 받는 EventHandler가 비동기로 수행한다
        val targets = participants.map {
            ChatMessageSentEvent.BroadcastTarget(
                it.uuid,
                it.member.uuid.toString(),
                it.member.email.isBotEmail()
            )
        }
        eventPublisher.publishEvent(ChatMessageSentEvent(cacheDto, targets))

        // 사람이(봇이 아닌 발신자가) 봇이 참여 중인 방에 메시지를 보내면, 봇이 맥락에 맞게 응답하게 트리거
        if (!sender.email.isBotEmail()) {
            participants.map { it.member }
                .firstOrNull { it.email.isBotEmail() }
                ?.let { bot -> eventPublisher.publishEvent(BotReplyTriggerEvent(roomId, bot.id!!)) }
        }

        return ChatRoomMessageResponseDto(message, sender.uuid)
    }

    fun getMessage(messageId: UUID): ChatMessage =
        chatMessageRepository.findByUuid(messageId) ?: throw ServiceException("404-2", "신고 대상 메시지를 찾을 수 없습니다.")

    // 신고 유발 메시지 시점을 기준으로 그 이전에 전송된 대화만 최대 30개 핀포인트 조회
    fun getMessagesBeforeTarget(roomId: Long, targetMessageId: UUID): List<ChatMessage> {
        val targetMessage = getMessage(targetMessageId)
        return chatMessageRepository.findTop30ByChatRoomIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
            roomId, targetMessage.createdAt!!
        )
    }

    fun getRecentMessages(roomId: Long, limit: Int): List<ChatMessage> =
        chatMessageRepository.findRecentByChatRoomId(roomId, PageRequest.of(0, limit))

    fun getMessages(roomId: Long, requester: Member, after: LocalDateTime?): List<ChatRoomMessageResponseDto> {
        val chatRoom = chatRoomRepository.findById(roomId)
            .orElseThrow { ServiceException("404-1", "채팅방을 찾을 수 없습니다.") }
        val requesterParticipantId = chatRoomParticipantRepository
            .findByChatRoomIdAndMemberId(roomId, requester.id!!)?.uuid
            ?: throw ServiceException("403-1", "해당 채팅방에 접근 권한이 없습니다.")

        if (chatRoom.status == ChatRoomStatus.CLOSED) {
            throw ServiceException("200-3", "종료된 채팅방입니다.")
        }

        val key = "chat:room:${chatRoom.uuid}:messages"
        var cachedMessages: MutableList<RedisChatMessageDto>? = null

        try {
            // 키 자체가 존재하면 결과가 빈 리스트여도 캐시 히트로 취급해 DB 조회를 막는다
            if (java.lang.Boolean.TRUE == redisTemplate.hasKey(key)) {
                val messages = mutableListOf<RedisChatMessageDto>()

                val jsonPayloads = if (after != null) {
                    val minScore = Timestamp.valueOf(after).time
                    redisTemplate.opsForZSet().rangeByScore(key, minScore.toDouble(), Double.MAX_VALUE)
                } else {
                    redisTemplate.opsForZSet().range(key, 0, -1)
                }

                if (!jsonPayloads.isNullOrEmpty()) {
                    for (json in jsonPayloads) {
                        messages.add(Ut.json.objectMapper!!.readValue(json, RedisChatMessageDto::class.java))
                    }
                }
                cachedMessages = messages
            }
        } catch (e: Exception) {
            log.error("Redis 조회 실패! DB 직접 조회로 우회합니다. roomId: {}", roomId, e)
            cachedMessages = null
        }

        if (cachedMessages != null) {
            var result: List<RedisChatMessageDto> = cachedMessages
            if (after != null) {
                result = result.filter { it.createdAt.isAfter(after) }
            }
            return result.map { ChatRoomMessageResponseDto(it, requesterParticipantId) }
        }

        var messages = chatMessageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId)

        try {
            if (messages.isNotEmpty()) {
                for (msg in messages) {
                    val dto = RedisChatMessageDto(msg)
                    val json = Ut.json.toString(dto)!!
                    val score = Timestamp.valueOf(dto.createdAt).time
                    redisTemplate.opsForZSet().add(key, json, score.toDouble())
                }
                // 활성 방에서 캐시가 계속 남아있지 않도록 누수 방지용 TTL을 건다
                redisTemplate.expire(key, Duration.ofHours(2))
            }
        } catch (e: Exception) {
            log.warn("Redis 캐시 재건 실패! (레디스 서버 다운 상태일 수 있습니다.)", e)
        }

        if (after != null) {
            messages = messages.filter { it.createdAt!!.isAfter(after) }
        }
        return messages.map { ChatRoomMessageResponseDto(it, requester.uuid) }
    }

    @Transactional
    fun deleteAllByMember(member: Member) {
        chatMessageRepository.deleteByParticipantMember(member)
    }
}
