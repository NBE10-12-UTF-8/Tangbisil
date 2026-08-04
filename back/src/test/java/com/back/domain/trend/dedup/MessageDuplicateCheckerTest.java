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

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(FINGERPRINT_KEY);
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
    @DisplayName("공백 차이만 있는 메시지는 정규화 후 같은 메시지로 취급되어 중복이다")
    void whitespaceOnlyDifferenceIsDuplicate() {
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, "오늘 점심 뭐 먹지")).isFalse();

        assertThat(messageDuplicateChecker.isDuplicate(TODAY, "오늘점심뭐먹지")).isTrue();
    }

    @Test
    @DisplayName("대소문자 차이만 있는 메시지는 정규화 후 같은 메시지로 취급되어 중복이다")
    void caseOnlyDifferenceIsDuplicate() {
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, "Hello World")).isFalse();

        assertThat(messageDuplicateChecker.isDuplicate(TODAY, "hello world")).isTrue();
    }

    @Test
    @DisplayName("짧은 메시지도 반복되면 동일하게 중복으로 판단된다")
    void shortRepeatedMessageIsDuplicate() {
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, "ㅋㅋ")).isFalse();
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, "ㅋㅋ")).isTrue();
    }

    @Test
    @DisplayName("null이거나 빈 문자열인 메시지는 중복 검사 없이 항상 false를 반환한다")
    void blankOrNullMessageIsNeverDuplicate() {
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, null)).isFalse();
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, "")).isFalse();
        assertThat(messageDuplicateChecker.isDuplicate(TODAY, "   ")).isFalse();
    }

    @Test
    @DisplayName("완전히 같은 메시지가 동시에 여러 건 도착해도 SADD의 원자성 덕분에 딱 하나만 통과한다")
    void concurrentIdenticalMessagesOnlyOnePasses() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, threadCount)
                    .<Callable<Boolean>>mapToObj(i -> () -> messageDuplicateChecker.isDuplicate(TODAY, "동시에 도착한 도배 메시지"))
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
