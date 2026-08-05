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
import com.back.standard.util.Ut;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.RedisTemplate;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisTemplate<String, String> redisTemplate;
    private static final Logger log = LoggerFactory.getLogger(ChatMessageService.class);

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
        List<ChatMessageSentEvent.BroadcastTarget> targets = participants.stream()
                .map(p -> new ChatMessageSentEvent.BroadcastTarget(
                        p.getId(),
                        p.getMember().getId().toString(),
                        BotAccounts.isBotEmail(p.getMember().getEmail())
                ))
                .toList();
        eventPublisher.publishEvent(new ChatMessageSentEvent(cacheDto, targets));

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
        UUID requesterParticipantId = chatRoomParticipantRepository
                .findByChatRoomIdAndMemberId(roomId, requester.getId())
                .map(p -> p.getId())
                .orElseThrow(() -> new ServiceException("403-1", "해당 채팅방에 접근 권한이 없습니다."));

        if(chatRoom.getStatus() == ChatRoomStatus.CLOSED) {
            throw new ServiceException("200-3", "종료된 채팅방입니다.");
        }

        String key = "chat:room:" + roomId + ":messages";
        List<RedisChatMessageDto> cachedMessages = null;

        // Redis 캐시 조회 시도
        try {
            // 레디스 캐시 키가 확실히 존재(hasKey)하는지 먼저 체크
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                // 키가 있다면 결과가 비어있더라도 캐시 히트(빈 리스트)로 취급하여 DB 조회를 차단
                cachedMessages = new ArrayList<>();

                Set<String> jsonPayloads;
                if (after != null) {
                    long minScore = Timestamp.valueOf(after).getTime();
                    jsonPayloads = redisTemplate.opsForZSet().rangeByScore(key, minScore, Double.MAX_VALUE);
                } else {
                    jsonPayloads = redisTemplate.opsForZSet().range(key, 0, -1);
                }

                if (jsonPayloads != null && !jsonPayloads.isEmpty()) {
                    for (String json : jsonPayloads) {
                        RedisChatMessageDto dto = Ut.json.objectMapper.readValue(json, RedisChatMessageDto.class);
                        cachedMessages.add(dto);
                    }
                }
            }
        } catch (Exception e) {
            // Redis 완전 다운 시 에러 로그만 남기고 조용히 DB 조회로 우회 (Fallback)
            log.error("Redis 조회 실패! DB 직접 조회로 Fallback합니다. roomId: {}", roomId, e);
            cachedMessages = null; // 에러가 나면 확실히 null로 밀어서 DB로 돌림
        }

        // 캐시 히트(Cache Hit) 성공 시 즉각 반환 (DB 쿼리 차단)
        if (cachedMessages != null) {
            if (after != null) {
                cachedMessages = cachedMessages.stream()
                        .filter(m -> m.getCreatedAt().isAfter(after))
                        .toList();
            }
            return cachedMessages.stream()
                    .map(cache -> new ChatRoomMessageResponseDto(cache, requesterParticipantId))
                    .toList();
        }

        // 캐시 미스(Cache Miss) 또는 레디스 장애 시: MySQL DB 조회 진행
        List<ChatMessage> messages = chatMessageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId);

        // DB 조회 데이터를 Redis ZSet 캐시에 재건
        try {
            if (!messages.isEmpty()) {
                for (ChatMessage msg : messages) {
                    RedisChatMessageDto dto = new RedisChatMessageDto(msg);
                    String json = Ut.json.toString(dto);
                    long score = java.sql.Timestamp.valueOf(dto.getCreatedAt()).getTime();
                    redisTemplate.opsForZSet().add(key, json, score);
                }
                // Active 방에 누수 방지용 Safety TTL (2시간) 지정
                redisTemplate.expire(key, Duration.ofHours(2));
            }
        } catch (Exception e) {
            log.warn("Redis 캐시 재건 실패! (레디스 서버 다운 상태일 수 있습니다.)", e);
        }

        // DB에서 조회한 원본 결과 반환
        if (after != null) {
            messages = messages.stream()
                    .filter(m -> m.getCreatedAt().isAfter(after))
                    .toList();
        }
        return messages.stream()
                .map(message -> new ChatRoomMessageResponseDto(message, requester.getId()))
                .toList();
    }

    @Transactional
    public void deleteAllByMember(Member member) {
        chatMessageRepository.deleteByParticipantMember(member);
    }
}