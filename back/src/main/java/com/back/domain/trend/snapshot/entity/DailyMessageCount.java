package com.back.domain.trend.snapshot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "daily_message_count",
        uniqueConstraints = @UniqueConstraint(columnNames ={"date"}))
public class DailyMessageCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate date;
    private long totalMessages;

    public DailyMessageCount(LocalDate date, long totalMessages) {
        this.date = date;
        this.totalMessages = totalMessages;
    }

    public void updateTotalMessages(long totalMessages) {
        this.totalMessages = totalMessages;
    }
}