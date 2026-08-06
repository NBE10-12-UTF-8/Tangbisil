package com.back.domain.chat.chatRoom.controller

import com.back.domain.chat.chatRoom.dto.ChatRoomDto
import com.back.domain.chat.chatRoom.service.ChatRoomService
import com.back.domain.chat.chatRoomParticipant.service.ChatRoomParticipantService
import com.back.domain.match.matchRequest.service.MatchRequestService
import com.back.global.exception.ServiceException
import com.back.global.rq.Rq
import com.back.global.rsData.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/rooms")
@Tag(name = "ApiV1ChatRoomController", description = "API 채팅방 컨트롤러")
@SecurityRequirement(name = "bearerAuth")
class ApiV1ChatRoomController(
    private val chatRoomService: ChatRoomService,
    private val chatRoomParticipantService: ChatRoomParticipantService,
    private val matchRequestService: MatchRequestService,
    private val rq: Rq
) {

    @GetMapping("/{roomId}")
    @Operation(summary = "채팅방 정보 조회")
    fun getRoom(@PathVariable roomId: UUID): RsData<ChatRoomDto> {
        val chatRoom = chatRoomService.getChatRoom(roomId)

        val actor = rq.actor ?: throw ServiceException("401-1", "인증이 필요합니다.")

        chatRoomParticipantService.validateAccess(chatRoom.id, actor)
        val isBot = chatRoomService.hasBotParticipant(chatRoom.id)
        val opponentSituation = matchRequestService.findOpponentSituation(chatRoom.id, actor.id)

        return RsData("200-1", "채팅방 정보 조회 성공", ChatRoomDto(chatRoom, isBot, opponentSituation))
    }

    @PatchMapping("/{roomId}")
    @Operation(summary = "채팅방 종료")
    fun closeRoom(@PathVariable roomId: UUID): RsData<ChatRoomDto> {
        val actor = rq.actor ?: throw ServiceException("401-1", "인증이 필요합니다.")

        val chatRoom = chatRoomService.closeChatRoom(roomId, actor)
        val isBot = chatRoomService.hasBotParticipant(chatRoom.id)
        val opponentSituation = matchRequestService.findOpponentSituation(chatRoom.id, actor.id)

        return RsData("200-1", "채팅방 상태 수정 성공 (채팅방 종료)", ChatRoomDto(chatRoom, isBot, opponentSituation))
    }

    @GetMapping("/active")
    @Operation(summary = "현재 활성화된 채팅방 조회")
    fun getActiveRoom(): RsData<ChatRoomDto> {
        val actor = rq.actor ?: throw ServiceException("401-1", "로그인 후 이용해주세요.")

        val chatRoom = chatRoomService.findActiveChatRoom(actor)
            ?: return RsData("200-2", "진행 중인 채팅방이 존재하지 않습니다.", null)

        val isBot = chatRoomService.hasBotParticipant(chatRoom.id)
        val opponentSituation = matchRequestService.findOpponentSituation(chatRoom.id, actor.id)
        return RsData("200-1", "현재 활성화된 채팅방 조회 성공", ChatRoomDto(chatRoom, isBot, opponentSituation))
    }
}
