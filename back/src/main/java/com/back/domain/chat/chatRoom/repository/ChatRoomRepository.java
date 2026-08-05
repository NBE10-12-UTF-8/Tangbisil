package com.back.domain.chat.chatRoom.repository;

import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    long countByStatus(ChatRoomStatus status);

    List<ChatRoom> findByStatusAndCreatedAtBefore(ChatRoomStatus status, LocalDateTime threshold);

    Optional<ChatRoom> findByUuid(UUID uuid);
}