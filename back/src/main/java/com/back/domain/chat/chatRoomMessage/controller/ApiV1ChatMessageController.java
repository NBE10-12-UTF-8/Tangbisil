package com.back.domain.chat.chatRoomMessage.controller;

import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomMessage.dto.ChatRoomMessageRequestDto;
import com.back.domain.chat.chatRoomMessage.dto.ChatRoomMessageResponseDto;
import com.back.domain.chat.chatRoomMessage.service.ChatMessageService;
import com.back.domain.member.member.entity.Member;
import com.back.global.exception.ServiceException;
import com.back.global.rq.Rq;
import com.back.global.rsData.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@Tag(name = "ApiV1ChatMessageController", description = "API 채팅 메시지 컨트롤러")
@SecurityRequirement(name = "bearerAuth")
public class ApiV1ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final ChatRoomRepository chatRoomRepository;
    private final Rq rq;

    private ChatRoom resolveRoom(UUID roomId) {
        return chatRoomRepository.findByUuid(roomId)
                .orElseThrow(() -> new ServiceException("404-1", "채팅방을 찾을 수 없습니다."));
    }

    @PostMapping("/{roomId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "메시지 전송")
    public RsData<ChatRoomMessageResponseDto> sendMessage(
            @PathVariable UUID roomId,
            @RequestBody @Valid ChatRoomMessageRequestDto requestDto
    ) {
        Member actor = rq.getActor();
        if (actor == null) {
            throw new ServiceException("401-1", "인증이 필요합니다.");
        }

        ChatRoomMessageResponseDto responseDto = chatMessageService.sendMessage(
                resolveRoom(roomId).getId(), actor, requestDto.getContent()
        );

        return new RsData<>("201-1", "메시지 생성 성공", responseDto);
    }

    @GetMapping("/{roomId}/messages")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "메시지 조회(폴링)")
    public RsData<List<ChatRoomMessageResponseDto>> getMessages(
            @PathVariable UUID roomId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)LocalDateTime after
            ) {
        Member actor = rq.getActor();
        if (actor == null) {
            throw new ServiceException("401-1", "인증이 필요합니다.");
        }

        List<ChatRoomMessageResponseDto> messages = chatMessageService.getMessages(resolveRoom(roomId).getId(), actor, after);
        if (messages.isEmpty()) {
            return new RsData<>("200-2", "신규 메시지 없음", null);
        }
        return new RsData<>("200-1", "메시지 목록 조회 성공", messages);
    }

}