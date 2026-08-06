package com.back.domain.chat.chatRoomMessage.controller

import com.back.domain.chat.chatRoom.repository.ChatRoomRepository
import com.back.domain.chat.chatRoomMessage.dto.ChatRoomMessageRequestDto
import com.back.domain.chat.chatRoomMessage.service.ChatMessageService
import com.back.domain.member.member.service.MemberService
import com.back.global.exception.ServiceException
import jakarta.validation.Valid
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.stereotype.Controller
import java.security.Principal
import java.util.UUID

@Controller
class StompChatController(
    private val chatMessageService: ChatMessageService,
    private val memberService: MemberService,
    private val chatRoomRepository: ChatRoomRepository
) {

    @MessageMapping("/rooms/{roomId}/messages")
    fun sendMessage(
        @DestinationVariable roomId: UUID,
        @Payload @Valid requestDto: ChatRoomMessageRequestDto,
        principal: Principal?
    ) {
        if (principal == null) {
            throw ServiceException("401-1", "인증이 필요합니다.")
        }

        val memberId = UUID.fromString(principal.name)
        val actor = memberService.findByUuid(memberId)
            .orElseThrow { ServiceException("404-1", "존재하지 않는 회원입니다.") }
        val chatRoom = chatRoomRepository.findByUuid(roomId)
            ?: throw ServiceException("404-2", "채팅방을 찾을 수 없습니다.")
        chatMessageService.sendMessage(chatRoom.id!!, actor, requestDto.content)
    }

    @MessageExceptionHandler(ServiceException::class)
    @SendToUser("/queue/errors")
    fun handleException(e: ServiceException): Map<String, String> =
        mapOf("code" to e.rsData.resultCode, "message" to e.rsData.msg)
}
