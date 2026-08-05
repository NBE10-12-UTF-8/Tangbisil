package com.back.domain.notification.service;

import com.back.domain.notification.dto.MatchNotificationDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class MatchNotificationServiceTest {

    @Autowired
    private MatchNotificationService matchNotificationService;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final List<Long> usedMemberIds = new ArrayList<>();
    private final java.util.concurrent.atomic.AtomicLong memberIdSeq = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());

    @AfterEach
    void cleanUp() {
        usedMemberIds.forEach(memberId -> redisTemplate.delete("notification:member:" + memberId));
        usedMemberIds.clear();
    }

    private Long newMemberId() {
        Long id = memberIdSeq.incrementAndGet();
        usedMemberIds.add(id);
        return id;
    }

    @Test
    @DisplayName("매칭 성사 알림을 발행하면 조회된다")
    void t1() {
        Long memberId = newMemberId();
        UUID roomId = UUID.randomUUID();

        matchNotificationService.notifyMatchSuccess(memberId, roomId);
        List<MatchNotificationDto> notifications = matchNotificationService.getNotifications(memberId, null);

        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType()).isEqualTo("MATCH_SUCCESS");
        assertThat(notifications.get(0).getRoomId()).isEqualTo(roomId);
    }

    @Test
    @DisplayName("알림이 없으면 빈 리스트를 반환한다")
    void t2() {
        Long memberId = newMemberId();

        List<MatchNotificationDto> notifications = matchNotificationService.getNotifications(memberId, null);

        assertThat(notifications).isEmpty();
    }

    @Test
    @DisplayName("after 이후에 발행된 알림만 조회된다")
    void t3() throws InterruptedException {
        Long memberId = newMemberId();
        UUID roomId1 = UUID.randomUUID();
        UUID roomId2 = UUID.randomUUID();

        matchNotificationService.notifyMatchSuccess(memberId, roomId1);
        LocalDateTime cursor = matchNotificationService.getNotifications(memberId, null)
                .get(0).getCreatedAt();

        Thread.sleep(10); // 밀리초 단위 score 충돌 방지
        matchNotificationService.notifyMatchSuccess(memberId, roomId2);

        List<MatchNotificationDto> newOnes = matchNotificationService.getNotifications(memberId, cursor);

        assertThat(newOnes).hasSize(1);
        assertThat(newOnes.get(0).getRoomId()).isEqualTo(roomId2);
    }

    @Test
    @DisplayName("서로 다른 회원의 알림은 섞이지 않는다")
    void t4() {
        Long memberA = newMemberId();
        Long memberB = newMemberId();

        matchNotificationService.notifyMatchSuccess(memberA, UUID.randomUUID());

        assertThat(matchNotificationService.getNotifications(memberA, null)).hasSize(1);
        assertThat(matchNotificationService.getNotifications(memberB, null)).isEmpty();
    }
}