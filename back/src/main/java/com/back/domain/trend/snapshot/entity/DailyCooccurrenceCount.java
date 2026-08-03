package com.back.domain.trend.snapshot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "daily_cooccurrence_count",
        uniqueConstraints = @UniqueConstraint(columnNames ={"date","keyword_a","keyword_b"}))
public class DailyCooccurrenceCount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate date;

    @Column(name = "keyword_a")
    private String keywordA;

    @Column(name = "keyword_b")
    private String keywordB;

    private long frequency;

    public void updateFrequency(long frequency) {
        this.frequency = frequency;
    }

    public DailyCooccurrenceCount(LocalDate date, String keywordA, String keywordB, long frequency) {
        this.date = date;
        this.keywordA = keywordA;
        this.keywordB = keywordB;
        this.frequency = frequency;
    }
}
