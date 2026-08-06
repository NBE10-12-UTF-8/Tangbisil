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
    name = "daily_message_count",
    uniqueConstraints = [UniqueConstraint(columnNames = ["date"])]
)
class DailyMessageCount(
    date: LocalDate,
    totalMessages: Long
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    var date: LocalDate = date
        protected set

    var totalMessages: Long = totalMessages
        protected set

    fun updateTotalMessages(totalMessages: Long) {
        this.totalMessages = totalMessages
    }
}
