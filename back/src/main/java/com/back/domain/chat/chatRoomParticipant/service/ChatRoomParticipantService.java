package com.back.domain.chat.chatRoomParticipant.service;

import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus;
import com.back.domain.chat.chatRoomParticipant.entity.ChatRoomParticipant;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.member.member.entity.Member;
import com.back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomParticipantService {

    private final ChatRoomParticipantRepository chatRoomParticipantRepository;

    public void validateAccess(UUID roomId, Member actor) {
        if (actor.isAdmin()) {
            return;
        }

        boolean isParticipant = chatRoomParticipantRepository.existsByChatRoomIdAndMemberId(roomId, actor.getId());
        if (!isParticipant) {
            throw new ServiceException("403-1", "접근 권한이 없습니다.");
        }
    }

    @Transactional
    public void createParticipants(ChatRoom chatRoom, List<Member> members) {
        for (Member member : members) {
            ChatRoomParticipant participant = new ChatRoomParticipant(chatRoom, member, "익명의 동료");
            chatRoomParticipantRepository.save(participant);
        }
    }

    public Optional<ChatRoom> findActiveChatRoomByMember(Member member) {
        return chatRoomParticipantRepository.findByMemberIdAndChatRoomStatus(member.getId(), ChatRoomStatus.ACTIVE)
                .map(ChatRoomParticipant::getChatRoom);
    }

    public boolean isParticipant(UUID roomId, UUID memberId) {
        return chatRoomParticipantRepository.existsByChatRoomIdAndMemberId(roomId, memberId);
    }

    public UUID getParticipantId(UUID roomId, UUID memberId) {
        return chatRoomParticipantRepository
                .findByChatRoomIdAndMemberId(roomId, memberId)
                .map(ChatRoomParticipant::getId)
                .orElse(null);
    }

    // 이 방의 전체 참여자 목록 조회 (member까지 fetch join 되어있어 N+1 없음)
    public List<ChatRoomParticipant> getParticipants(UUID roomId) {
        return chatRoomParticipantRepository.findByChatRoomId(roomId);
    }

    // 여러 방의 참여자를 한 번에 조회 (매칭 이력처럼 N개 방을 다룰 때 N+1 방지용)
    public List<ChatRoomParticipant> getParticipantsByRoomIds(Collection<UUID> roomIds) {
        if (roomIds.isEmpty()) {
            return List.of();
        }
        return chatRoomParticipantRepository.findByChatRoomIdIn(roomIds);
    }

    @Transactional
    public void deleteAllByMember(Member member) {
        chatRoomParticipantRepository.deleteByMember(member);
    }
}