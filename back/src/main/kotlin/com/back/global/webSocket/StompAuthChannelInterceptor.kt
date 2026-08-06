package com.back.global.webSocket

import com.back.domain.chat.chatRoom.repository.ChatRoomRepository
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository
import com.back.domain.member.member.service.MemberService
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class StompAuthChannelInterceptor(
    private val memberService: MemberService,
    private val chatRoomParticipantRepository: ChatRoomParticipantRepository,
    private val chatRoomRepository: ChatRoomRepository
) : ChannelInterceptor {
    companion object {
        private const val ROOM_QUEUE_PREFIX = "/user/queue/rooms/"
    }

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message

        if (StompCommand.CONNECT == accessor.command) {
            val id: UUID
            val role: String

            // 핸드셰이크 단계(CookieHandshakeInterceptor)에서 accessToken 쿠키로 이미 검증됐다면
            // 세션 attributes에 신원이 들어있다 — 이 경우 CONNECT 프레임에 Authorization 헤더가
            // 없어도(JS가 토큰을 안 들고 있어도) 인증할 수 있다. 없으면 기존처럼 헤더로 폴백한다.
            val sessionAttributes = accessor.sessionAttributes
            val sessionMemberId = sessionAttributes?.get("memberId")

            if (sessionMemberId is UUID) {
                id = sessionMemberId
                role = sessionAttributes["role"] as String
            } else {
                val authHeader = accessor.getFirstNativeHeader("Authorization")
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    throw AccessDeniedException("WebSocket 연결에 토큰이 필요합니다.")
                }

                val token = authHeader.substring(7)
                val payload = memberService.payload(token)
                    ?: throw AccessDeniedException("유효하지 않은 토큰입니다.")

                val rawId = payload["id"] ?: throw AccessDeniedException("유효하지 않은 토큰입니다.")
                id = try {
                    if (rawId is UUID) rawId else UUID.fromString(rawId.toString())
                } catch (e: IllegalArgumentException) {
                    throw AccessDeniedException("유효하지 않은 토큰입니다.")
                }

                val rawRole = payload["role"] ?: throw AccessDeniedException("유효하지 않은 토큰입니다.")
                role = rawRole.toString()
            }

            val auth = UsernamePasswordAuthenticationToken(
                id.toString(),
                null,
                listOf(SimpleGrantedAuthority("ROLE_$role"))
            )
            accessor.user = auth
        }

        if (StompCommand.SUBSCRIBE == accessor.command) {
            val destination = accessor.destination ?: throw AccessDeniedException("구독 경로가 필요합니다.")

            val auth = accessor.user as? UsernamePasswordAuthenticationToken
                ?: throw AccessDeniedException("인증 정보가 올바르지 않습니다.")
            val memberId = UUID.fromString(auth.name)

            if (destination == "/user/queue/errors") return message
            if (!destination.startsWith(ROOM_QUEUE_PREFIX)) throw AccessDeniedException("허용되지 않은 구독 경로입니다.")

            val roomIdStr = destination.substring(ROOM_QUEUE_PREFIX.length)
            val roomId = try {
                UUID.fromString(roomIdStr)
            } catch (e: IllegalArgumentException) {
                throw AccessDeniedException("유효하지 않은 구독 경로입니다.")
            }

            val room = chatRoomRepository.findByUuid(roomId) ?: throw AccessDeniedException("존재하지 않는 채팅방입니다.")
            val member = memberService.findByUuid(memberId)
                .orElseThrow { AccessDeniedException("존재하지 않는 회원입니다.") }

            if (!chatRoomParticipantRepository.existsByChatRoomIdAndMemberId(room.id!!, member.id!!)) {
                throw AccessDeniedException("해당 채팅방의 참여자가 아닙니다.")
            }
        }
        return message
    }
}
