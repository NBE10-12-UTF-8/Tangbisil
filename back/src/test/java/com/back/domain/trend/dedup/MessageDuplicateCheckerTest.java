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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class MessageDuplicateCheckerTest {

    @Autowired
    private MessageDuplicateChecker messageDuplicateChecker;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));
    private static final String FINGERPRINT_KEY = "trend:fingerprints:" + TODAY;
    private static final UUID SENDER_A = UUID.randomUUID();
    private static final UUID SENDER_B = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(FINGERPRINT_KEY);
    }

    @Test
    @DisplayName("처음 보는 메시지는 중복이 아니다")
    void firstMessageIsNotDuplicate() {
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, SENDER_A, "오늘 점심 뭐 먹지")).isFalse();
    }

    @Test
    @DisplayName("같은 발신자가 완전히 같은 메시지를 다시 보내면 두 번째부터는 중복이다")
    void repeatedIdenticalMessageFromSameSenderIsDuplicate() {
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, SENDER_A, "완전히 똑같은 메시지입니다")).isFalse();
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, SENDER_A, "완전히 똑같은 메시지입니다")).isTrue();
    }

    @Test
    @DisplayName("서로 다른 발신자가 같은 메시지를 보내면 중복이 아니다 — 여러 사람이 같은 말을 하는 건 도배가 아니라 실제 트렌드 신호다")
    void sameMessageFromDifferentSendersIsNotDuplicate() {
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, SENDER_A, "장마 진짜 심하다")).isFalse();
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, SENDER_B, "장마 진짜 심하다")).isFalse();
    }

    @Test
    @DisplayName("전혀 다른 메시지는 앞서 본 메시지가 있어도 중복이 아니다")
    void unrelatedMessageIsNotDuplicate() {
        messageDuplicateChecker.isDuplicate(TODAY, SENDER_A, "오늘 점심 뭐 먹지");

        assertThat(messageDuplicateChecker.isDuplicate(TODAY, SENDER_A, "주식이 왜 이렇게 빠지지")).isFalse();
    }

    @Test
    @DisplayName("같은 발신자의 공백 차이만 있는 메시지는 정규화 후 같은 메시지로 취급되어 중복이다")
    void whitespaceOnlyDifferenceIsDuplicate() {
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, SENDER_A, "오늘 점심 뭐 먹지")).isFalse();

        assertThat(messageDuplicateChecker.isDuplicate(TODAY, SENDER_A, "오늘점심뭐먹지")).isTrue();
    }

    @Test
    @DisplayName("같은 발신자의 대소문자 차이만 있는 메시지는 정규화 후 같은 메시지로 취급되어 중복이다")
    void caseOnlyDifferenceIsDuplicate() {
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, SENDER_A, "Hello World")).isFalse();

        assertThat(messageDuplicateChecker.isDuplicate(TODAY, SENDER_A, "hello world")).isTrue();
    }

    @Test
    @DisplayName("같은 발신자가 짧은 메시지를 반복하면 중복으로 판단된다")
    void shortRepeatedMessageFromSameSenderIsDuplicate() {
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, SENDER_A, "ㅋㅋ")).isFalse();
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, SENDER_A, "ㅋㅋ")).isTrue();
    }

    @Test
    @DisplayName("서로 다른 발신자가 같은 짧은 인사말을 보내면 중복이 아니다")
    void shortMessageFromDifferentSendersIsNotDuplicate() {
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, SENDER_A, "ㅋㅋ")).isFalse();
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, SENDER_B, "ㅋㅋ")).isFalse();
    }

    @Test
    @DisplayName("null이거나 빈 문자열인 메시지는 중복 검사 없이 항상 false를 반환한다")
    void blankOrNullMessageIsNeverDuplicate() {
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, SENDER_A, null)).isFalse();
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, SENDER_A, "")).isFalse();
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, SENDER_A, "   ")).isFalse();
    }

    @Test
    @DisplayName("발신자 ID가 null이면 같은 내용이 반복돼도 서로 다른 발신자일 수 있어 중복 검사 없이 항상 false를 반환한다")
    void nullSenderIsNeverDuplicate() {
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, null, "발신자 없는 메시지")).isFalse();
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, null, "발신자 없는 메시지")).isFalse();
    }

    @Test
    @DisplayName("같은 발신자의 완전히 같은 메시지가 동시에 여러 건 도착해도 SADD의 원자성 덕분에 딱 하나만 통과한다")
    void concurrentIdenticalMessagesOnlyOnePasses() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, threadCount)
                    .<Callable<Boolean>>mapToObj(i -> () -> messageDuplicateChecker.isDuplicate(TODAY, SENDER_A, "동시에 도착한 도배 메시지"))
                    .toList();

            List<Future<Boolean>> futures = executor.invokeAll(tasks);
            List<Boolean> results = futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());

            long notDuplicateCount = results.stream().filter(r -> !r).count();
            assertThat(notDuplicateCount).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }
}
