package com.back.domain.chat.chatRoomMessage.controller

import com.back.domain.chat.chatRoom.entity.ChatRoom
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository
import com.back.domain.chat.chatRoomMessage.dto.ChatRoomMessageRequestDto
import com.back.domain.chat.chatRoomMessage.dto.ChatRoomMessageResponseDto
import com.back.domain.chat.chatRoomMessage.service.ChatMessageService
import com.back.global.exception.ServiceException
import com.back.global.rq.Rq
import com.back.global.rsData.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.util.UUID

@RestController
@RequestMapping("/api/v1/rooms")
@Tag(name = "ApiV1ChatMessageController", description = "API 채팅 메시지 컨트롤러")
@SecurityRequirement(name = "bearerAuth")
class ApiV1ChatMessageController(
    private val chatMessageService: ChatMessageService,
    private val chatRoomRepository: ChatRoomRepository,
    private val rq: Rq
) {

    private fun resolveRoom(roomId: UUID): ChatRoom =
        chatRoomRepository.findByUuid(roomId) ?: throw ServiceException("404-1", "채팅방을 찾을 수 없습니다.")

    @PostMapping("/{roomId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "메시지 전송")
    fun sendMessage(
        @PathVariable roomId: UUID,
        @RequestBody @Valid requestDto: ChatRoomMessageRequestDto
    ): RsData<ChatRoomMessageResponseDto> {
        val actor = rq.actor ?: throw ServiceException("401-1", "인증이 필요합니다.")

        val responseDto = chatMessageService.sendMessage(resolveRoom(roomId).id!!, actor, requestDto.content)

        return RsData("201-1", "메시지 생성 성공", responseDto)
    }

    @GetMapping("/{roomId}/messages")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "메시지 조회(폴링)")
    fun getMessages(
        @PathVariable roomId: UUID,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) after: LocalDateTime?
    ): RsData<List<ChatRoomMessageResponseDto>> {
        val actor = rq.actor ?: throw ServiceException("401-1", "인증이 필요합니다.")

        val messages = chatMessageService.getMessages(resolveRoom(roomId).id!!, actor, after)
        if (messages.isEmpty()) {
            return RsData("200-2", "신규 메시지 없음", null)
        }
        return RsData("200-1", "메시지 목록 조회 성공", messages)
    }
}
