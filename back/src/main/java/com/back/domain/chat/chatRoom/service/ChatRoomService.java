package com.back.domain.chat.chatRoom.service;

import com.back.domain.bot.BotAccounts;
import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus;
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomParticipant.entity.ChatRoomParticipant;
import com.back.domain.chat.chatRoomParticipant.service.ChatRoomParticipantService;
import com.back.global.exception.ServiceException;
import com.back.domain.member.member.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.RedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {
    private static final Logger log = LoggerFactory.getLogger(ChatRoomService.class);

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantService chatRoomParticipantService;
    private final RedisTemplate<String, String> redisTemplate;

    public ChatRoom getChatRoom(UUID roomId) {
        return chatRoomRepository.findByUuid(roomId)
                .orElseThrow(() -> new ServiceException("404-1", "채팅방을 찾을 수 없습니다."));
    }

    public boolean hasBotParticipant(Long roomId) {
        return chatRoomParticipantService.getParticipants(roomId).stream()
                .map(ChatRoomParticipant::getMember)
                .anyMatch(member -> BotAccounts.isBotEmail(member.getEmail()));
    }

    @Transactional
    public ChatRoom createChatRoom(List<Member> members) {
        ChatRoom chatRoom = new ChatRoom(ChatRoomStatus.ACTIVE, members.size());
        ChatRoom savedRoom = chatRoomRepository.save(chatRoom);

        chatRoomParticipantService.createParticipants(savedRoom, members);
        return savedRoom;
    }

    @Transactional
    public ChatRoom closeChatRoom(UUID roomId, Member actor) {
        ChatRoom chatRoom = getChatRoom(roomId);

        chatRoomParticipantService.validateAccess(chatRoom.getId(), actor);

        if (chatRoom.getStatus() == ChatRoomStatus.CLOSED) {
            throw new ServiceException("409-1", "이미 종료된 채팅방입니다.");
        }

        chatRoom.close();

        try {
            String key = "chat:room:" + chatRoom.getUuid() + ":messages";
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("대화방 종료 후 Redis 캐시 삭제 실패 - roomId: {}", roomId, e);
        }

        return chatRoom;
    }

    public Optional<ChatRoom> findActiveChatRoom(Member actor) {
        return chatRoomParticipantService.findActiveChatRoomByMember(actor);
    }

    // 여러 채팅방의 봇 참여 여부를 한 번에 조회 (roomId -> isBot)
    public Map<Long, Boolean> hasBotParticipantMap(Collection<Long> roomIds) {
        if (roomIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Boolean> result = new HashMap<>();
        roomIds.forEach(id -> result.put(id, false));

        chatRoomParticipantService.getParticipantsByRoomIds(roomIds).stream()
                .filter(p -> BotAccounts.isBotEmail(p.getMember().getEmail()))
                .forEach(p -> result.put(p.getChatRoom().getId(), true));

        return result;
    }
}