package com.back.domain.chat.chatRoom.scheduler;

import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus;
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public class ChatRoomAutoCloseSchedulerTest {

    @Autowired
    private ChatRoomAutoCloseScheduler scheduler;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private EntityManager entityManager;

    private void updateCreatedAtInDb(Long roomId, LocalDateTime createdAt) {
        entityManager.createQuery("UPDATE ChatRoom c SET c.createdAt = :createdAt WHERE c.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", roomId)
                .executeUpdate();
        entityManager.clear();
    }

    @Test
    @DisplayName("생성된 지 10분이 지난 활성 채팅방은 CLOSED로 변경된다")
    void t1() {
        // Given
        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        updateCreatedAtInDb(chatRoom.getId(), LocalDateTime.now().minusMinutes(11));

        // When
        scheduler.closeExpiredChatRooms();

        // Then
        ChatRoom closedRoom = chatRoomRepository.findById(chatRoom.getId()).orElseThrow();
        assertThat(closedRoom.getStatus()).isEqualTo(ChatRoomStatus.CLOSED);
        assertThat(closedRoom.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("생성된 지 10분이 지나지 않은 활성 채팅방은 ACTIVE 상태를 유지한다")
    void t2() {
        // Given
        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        updateCreatedAtInDb(chatRoom.getId(), LocalDateTime.now().minusMinutes(5));

        // When
        scheduler.closeExpiredChatRooms();

        // Then
        ChatRoom activeRoom = chatRoomRepository.findById(chatRoom.getId()).orElseThrow();
        assertThat(activeRoom.getStatus()).isEqualTo(ChatRoomStatus.ACTIVE);
        assertThat(activeRoom.getClosedAt()).isNull();
    }
}
