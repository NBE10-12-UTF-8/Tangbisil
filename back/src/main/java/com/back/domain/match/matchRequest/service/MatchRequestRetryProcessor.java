package com.back.domain.match.matchRequest.service;

import com.back.domain.member.member.entity.Industry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchRequestRetryProcessor {
    private final ApplicationContext applicationContext;

    // 요청 스레드를 블로킹하지 않도록 비동기 실행. @Transactional은 걸지 않는다 -
    // Redisson 락 대기 중에는 커넥션을 쥐지 않아야 하기 때문.
    @Async
    public void retryOne(UUID matchRequestId, Industry industry) {
        applicationContext.getBean(MatchRequestService.class).tryMatch(matchRequestId, industry);
    }
}
