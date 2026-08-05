package com.back.domain.match.matchRequest.service;

import com.back.domain.match.matchRequest.entity.MatchRequest;
import com.back.domain.match.matchRequest.repository.MatchRequestRepository;
import com.back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// MatchRequestRetryProcessor.java
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchRequestRetryProcessor {
    private final MatchRequestRepository matchRequestRepository;
    private final ApplicationContext applicationContext;

    // 매칭 신청 트랜잭션 커밋(afterCommit) 흐름에서 호출되므로, 요청 스레드를 블로킹하지 않도록 비동기로 실행한다.
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryOne(Long matchRequestId) {
        MatchRequest matchRequest = matchRequestRepository.findByIdWithMember(matchRequestId)
                .orElseThrow(() -> new ServiceException("404-1", "매칭 요청을 찾을 수 없습니다."));
        applicationContext.getBean(MatchRequestService.class).tryMatch(matchRequest);
    }
}