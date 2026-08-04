package com.back.domain.trend.dedup;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class MessageDuplicateCheckerTest {

    @Autowired
    private MessageDuplicateChecker messageDuplicateChecker;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));
    private static final String SIGNATURE_KEY = "trend:signatures:" + TODAY;

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(SIGNATURE_KEY);
    }

    @Test
    @DisplayName("처음 보는 메시지는 중복이 아니다")
    void firstMessageIsNotDuplicate() {
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, "오늘 점심 뭐 먹지")).isFalse();
    }

    @Test
    @DisplayName("완전히 같은 메시지가 다시 오면 두 번째부터는 중복이다")
    void repeatedIdenticalMessageIsDuplicate() {
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, "완전히 똑같은 메시지입니다")).isFalse();
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, "완전히 똑같은 메시지입니다")).isTrue();
    }

    @Test
    @DisplayName("전혀 다른 메시지는 앞서 본 메시지가 있어도 중복이 아니다")
    void unrelatedMessageIsNotDuplicate() {
        messageDuplicateChecker.isDuplicate(TODAY, "오늘 점심 뭐 먹지");

        assertThat(messageDuplicateChecker.isDuplicate(TODAY, "주식이 왜 이렇게 빠지지")).isFalse();
    }

    @Test
    @DisplayName("shingle을 만들 수 없을 만큼 짧은 메시지(예: ㅋㅋ)는 반복돼도 중복 처리되지 않는다")
    void tooShortMessageIsNeverDuplicate() {
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, "ㅋㅋ")).isFalse();
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, "ㅋㅋ")).isFalse();
    }

    @Test
    @DisplayName("null 메시지는 중복 검사 없이 항상 false를 반환한다")
    void nullMessageIsNeverDuplicate() {
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, null)).isFalse();
    }
}
