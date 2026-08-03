package com.back.domain.trend.snapshot.repository;

import com.back.domain.trend.snapshot.entity.DailyCooccurrenceCount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyCooccurrenceCountRepository extends JpaRepository<DailyCooccurrenceCount, Long> {
    Optional<DailyCooccurrenceCount>findByDateAndKeywordAAndKeywordB(LocalDate date, String keywordA, String keywordB);
    List<DailyCooccurrenceCount> findAllByDate(LocalDate date);
}
