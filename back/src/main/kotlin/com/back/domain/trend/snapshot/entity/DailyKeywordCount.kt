package com.back.domain.trend.snapshot.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

@Entity
@Table(
    name = "daily_keyword_count",
    uniqueConstraints = [UniqueConstraint(columnNames = ["date", "keyword"])]
)
class DailyKeywordCount(
    date: LocalDate,
    keyword: String,
    frequency: Long
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    var date: LocalDate = date
        protected set

    var keyword: String = keyword
        protected set

    var frequency: Long = frequency
        protected set

    fun updateFrequency(frequency: Long) {
        this.frequency = frequency
    }
}
