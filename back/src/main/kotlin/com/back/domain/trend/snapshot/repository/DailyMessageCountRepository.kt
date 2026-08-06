package com.back.domain.trend.snapshot.repository

import com.back.domain.trend.snapshot.entity.DailyMessageCount
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface DailyMessageCountRepository : JpaRepository<DailyMessageCount, Long> {
    fun findByDate(date: LocalDate): DailyMessageCount?
}
