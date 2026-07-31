package com.back.domain.trend.snapshot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "daily_keyword_count",
        uniqueConstraints = @UniqueConstraint(columnNames ={"date","keyword"}))
public class DailyKeywordCount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate date;
    private String keyword;
    private long frequency;

    public DailyKeywordCount(LocalDate date, String keyword, long frequency) {
        this.date = date;
        this.keyword = keyword;
        this.frequency = frequency;
    }

    public void updateFrequency(long frequency) {
        this.frequency = frequency;
    }
}
