package com.back.domain.trend.snapshot.scheduler;

import com.back.domain.trend.snapshot.service.TrendSnapshotService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * 실제 스냅샷 로직은 TrendSnapshotService로 분리됐으므로(작업 일지 참고),
 * 이 스케줄러가 검증해야 할 건 "어제 날짜로 위임하는지"와
 * "서비스에서 예외가 나도 밖으로 전파되지 않는지" 두 가지뿐이다.
 */
@ActiveProfiles("test")
@SpringBootTest
class TrendSnapshotSchedulerTest {

    @Autowired
    private TrendSnapshotScheduler trendSnapshotScheduler;

    @MockitoBean
    private TrendSnapshotService trendSnapshotService;

    private static final LocalDate YESTERDAY = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);

    @Test
    @DisplayName("어제 날짜로 TrendSnapshotService.snapshotDate를 호출한다")
    void delegatesToServiceWithYesterday() {
        trendSnapshotScheduler.snapshotYesterday();

        verify(trendSnapshotService).snapshotDate(YESTERDAY);
    }

    @Test
    @DisplayName("서비스에서 예외가 나도 스케줄러 밖으로 전파되지 않는다")
    void exceptionFromServiceDoesNotPropagate() {
        doThrow(new RuntimeException("스냅샷 실패")).when(trendSnapshotService).snapshotDate(YESTERDAY);

        assertThatCode(() -> trendSnapshotScheduler.snapshotYesterday()).doesNotThrowAnyException();
    }
}
