package com.back.domain.trend.snapshot.repository;

import com.back.domain.trend.snapshot.entity.DailyKeywordCount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyKeywordCountRepository extends JpaRepository<DailyKeywordCount, Long> {

    Optional<DailyKeywordCount> findByDateAndKeyword(LocalDate date, String keyword);
}
