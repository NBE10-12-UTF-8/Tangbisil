package com.back.domain.trend.snapshot.scheduler;

import com.back.domain.trend.snapshot.service.TrendSnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class TrendSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrendSnapshotScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TrendSnapshotService trendSnapshotService;

    public TrendSnapshotScheduler(TrendSnapshotService trendSnapshotService) {
        this.trendSnapshotService = trendSnapshotService;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void snapshotYesterday() {
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        try {
            trendSnapshotService.snapshotDate(yesterday);
        } catch (Exception e) {
            // 이 배치가 실패해도 다음 스케줄 실행 자체는 끊기지 않지만(Spring 기본 동작),
            // 어제 날짜는 다시 스냅샷되지 않으므로 실패를 놓치지 않도록 로그를 남긴다.
            // try가 트랜잭션 경계 밖에 있어서, snapshotDate 내부에서 예외가 나면
            // TrendSnapshotService의 @Transactional이 정상적으로 롤백을 수행한 뒤 여기로 전파된다.
            log.error("트렌드 스냅샷 실패 - date={}", yesterday, e);
        }
    }
}
