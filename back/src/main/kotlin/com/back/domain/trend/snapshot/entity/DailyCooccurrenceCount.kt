package com.back.domain.trend.snapshot.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

@Entity
@Table(
    name = "daily_cooccurrence_count",
    uniqueConstraints = [UniqueConstraint(columnNames = ["date", "keyword_a", "keyword_b"])]
)
class DailyCooccurrenceCount(
    date: LocalDate,
    keywordA: String,
    keywordB: String,
    frequency: Long
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    var date: LocalDate = date
        protected set

    @Column(name = "keyword_a")
    var keywordA: String = keywordA
        protected set

    @Column(name = "keyword_b")
    var keywordB: String = keywordB
        protected set

    var frequency: Long = frequency
        protected set

    fun updateFrequency(frequency: Long) {
        this.frequency = frequency
    }
}
