package com.back.domain.chat.chatRoom.controller;

import com.back.domain.chat.chatRoom.dto.ChatRoomDto;
import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.service.ChatRoomService;
import com.back.domain.chat.chatRoomParticipant.service.ChatRoomParticipantService;
import com.back.domain.match.matchRequest.entity.Situation;
import com.back.domain.match.matchRequest.service.MatchRequestService;
import com.back.domain.member.member.entity.Member;
import com.back.global.exception.ServiceException;
import com.back.global.rq.Rq;
import com.back.global.rsData.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@Tag(name = "ApiV1ChatRoomController", description = "API 채팅방 컨트롤러")
@SecurityRequirement(name = "bearerAuth")
public class ApiV1ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final ChatRoomParticipantService chatRoomParticipantService;
    private final MatchRequestService matchRequestService;
    private final Rq rq;

    @GetMapping("/{roomId}")
    @Operation(summary = "채팅방 정보 조회")
    public RsData<ChatRoomDto> getRoom(@PathVariable UUID roomId) {
        ChatRoom chatRoom = chatRoomService.getChatRoom(roomId);

        Member actor = rq.getActor();
        if (actor == null) {
            throw new ServiceException("401-1", "인증이 필요합니다.");
        }

        chatRoomParticipantService.validateAccess(chatRoom.getId(), actor);
        boolean isBot = chatRoomService.hasBotParticipant(chatRoom.getId());
        Situation opponentSituation = matchRequestService.findOpponentSituation(chatRoom.getId(), actor.getId());

        return new RsData<>(
                "200-1",
                "채팅방 정보 조회 성공",
                new ChatRoomDto(chatRoom, isBot, opponentSituation)
        );
    }

    @PatchMapping("/{roomId}")
    @Operation(summary = "채팅방 종료")
    public RsData<ChatRoomDto> closeRoom(@PathVariable UUID roomId) {
        Member actor = rq.getActor();
        if (actor == null) {
            throw new ServiceException("401-1", "인증이 필요합니다.");
        }

        ChatRoom chatRoom = chatRoomService.closeChatRoom(roomId, actor);
        boolean isBot = chatRoomService.hasBotParticipant(chatRoom.getId());
        Situation opponentSituation = matchRequestService.findOpponentSituation(chatRoom.getId(), actor.getId());

        return new RsData<>(
                "200-1",
                "채팅방 상태 수정 성공 (채팅방 종료)",
                new ChatRoomDto(chatRoom, isBot, opponentSituation)
        );
    }

    @GetMapping("/active")
    @Operation(summary = "현재 활성화된 채팅방 조회")
    public RsData<ChatRoomDto> getActiveRoom() {
        Member actor = rq.getActor();

        if (actor == null) {
            throw new ServiceException("401-1", "로그인 후 이용해주세요.");
        }
        return chatRoomService.findActiveChatRoom(actor)
                .map(chatRoom -> new RsData<>(
                        "200-1",
                        "현재 활성화된 채팅방 조회 성공",
                        new ChatRoomDto(chatRoom, chatRoomService.hasBotParticipant(chatRoom.getId()),
                        matchRequestService.findOpponentSituation(chatRoom.getId(), actor.getId())
                        )
                ))
                .orElseGet(() -> new RsData<>(
                        "200-2",
                        "진행 중인 채팅방이 존재하지 않습니다.",
                        null
                ));
    }
}