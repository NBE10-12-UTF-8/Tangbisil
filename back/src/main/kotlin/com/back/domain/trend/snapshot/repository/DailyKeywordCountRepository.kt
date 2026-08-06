package com.back.domain.trend.snapshot.repository

import com.back.domain.trend.snapshot.entity.DailyKeywordCount
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface DailyKeywordCountRepository : JpaRepository<DailyKeywordCount, Long> {
    fun findByDateAndKeyword(date: LocalDate, keyword: String): DailyKeywordCount?

    fun findAllByDate(date: LocalDate): List<DailyKeywordCount>
}
