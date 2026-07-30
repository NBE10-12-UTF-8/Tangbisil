package com.back.domain.chat.chatRoomMessage.service;

import com.back.domain.bot.BotAccounts;
import com.back.domain.bot.BotReplyTriggerEvent;
import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus;
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomMessage.dto.ChatRoomMessageResponseDto;
import com.back.domain.chat.chatRoomMessage.entity.ChatMessage;
import com.back.domain.chat.chatRoomMessage.repository.ChatMessageRepository;
import com.back.domain.chat.chatRoomParticipant.entity.ChatRoomParticipant;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.member.member.entity.Member;
import com.back.global.exception.ServiceException;
import com.back.domain.chat.chatRoomMessage.dto.RedisChatMessageDto;
import com.back.domain.chat.chatRoomMessage.event.ChatMessageSentEvent;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ChatRoomMessageResponseDto sendMessage(UUID roomId, Member sender, String content) {
        if (content == null || content.isBlank()) {
            throw new ServiceException("400-1", "메시지 내용을 입력해주세요.");
        }
        if (content.length() > 500) {
            throw new ServiceException("400-2", "메시지는 500자를 초과할 수 없습니다.");
        }

        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ServiceException("404-1", "채팅방을 찾을 수 없습니다."));

        if (chatRoom.getStatus() == ChatRoomStatus.CLOSED) {
            throw new ServiceException("409-1", "종료된 채팅방에는 메시지를 보낼 수 없습니다.");
        }

        // 채팅방 참여자를 한 번만 조회해서 발신자 검증 + 봇 참여 여부 확인에 재사용
        List<ChatRoomParticipant> participants = chatRoomParticipantRepository.findByChatRoomId(roomId);

        ChatRoomParticipant participant = participants.stream()
                .filter(p -> p.getMember().getId().equals(sender.getId()))
                .findFirst()
                .orElseThrow(() -> new ServiceException("403-1", "채팅방 참여자만 메시지를 보낼 수 있습니다."));

        ChatMessage message = chatMessageRepository.save(
                new ChatMessage(chatRoom, participant, content)
        );

        // 저장 성공한 메시지 엔티티를 가벼운 Redis DTO 객체로 변환
        RedisChatMessageDto cacheDto = new RedisChatMessageDto(message);

        // 비동기 캐시 적재를 수행할 배달부(EventHandler)에게 이벤트 발행
        eventPublisher.publishEvent(new ChatMessageSentEvent(cacheDto));

        // 사람이(봇이 아닌 발신자가) 봇이 참여 중인 방에 메시지를 보내면, 봇이 맥락에 맞게 응답하게 트리거
        if (!BotAccounts.isBotEmail(sender.getEmail())) {
            participants.stream()
                    .map(ChatRoomParticipant::getMember)
                    .filter(m -> BotAccounts.isBotEmail(m.getEmail()))
                    .findFirst()
                    .ifPresent(bot -> eventPublisher.publishEvent(new BotReplyTriggerEvent(roomId, bot.getId())));
        }

        return new ChatRoomMessageResponseDto(message, sender.getId());
    }

    public ChatMessage getMessage(UUID messageId) {
        return chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ServiceException("404-2", "신고 대상 메시지를 찾을 수 없습니다."));
    }

    public List<ChatMessage> getMessagesByRoom(UUID roomId) {
        return chatMessageRepository.findByChatRoomIdOrderByCreatedAtDesc(roomId);
    }

    // 신고 유발 메시지 시점을 기준으로 그 이전에 전송된 대화만 최대 30개 핀포인트 조회
    public List<ChatMessage> getMessagesBeforeTarget(UUID roomId, UUID targetMessageId) {
        ChatMessage targetMessage = getMessage(targetMessageId);
        return chatMessageRepository.findTop30ByChatRoomIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                roomId,
                targetMessage.getCreatedAt()
        );
    }

    public List<ChatMessage> getRecentMessages(UUID roomId, int limit) {
        return chatMessageRepository.findRecentByChatRoomId(roomId, PageRequest.of(0, limit));
    }

    public List<ChatRoomMessageResponseDto> getMessages(UUID roomId, Member requester, LocalDateTime after) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ServiceException("404-1", "채팅방을 찾을 수 없습니다."));
        boolean isParticipant = chatRoomParticipantRepository
                .existsByChatRoomIdAndMemberId(roomId, requester.getId());
        if(!isParticipant){
            throw new ServiceException("403-1", "해당 채팅방에 접근 권한이 없습니다.");
        }

        if(chatRoom.getStatus() == ChatRoomStatus.CLOSED) {
            throw new ServiceException("200-3", "종료된 채팅방입니다.");
        }

        List<ChatMessage> messages = (after != null)
                ? chatMessageRepository.findByChatRoomIdAndCreatedAtAfterOrderByCreatedAtAsc(roomId, after)
                : chatMessageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId);

        return messages
                .stream()
                .map(message -> new ChatRoomMessageResponseDto(message, requester.getId()))
                .toList();
    }

    @Transactional
    public void deleteAllByMember(Member member) {
        chatMessageRepository.deleteByParticipantMember(member);
    }
}