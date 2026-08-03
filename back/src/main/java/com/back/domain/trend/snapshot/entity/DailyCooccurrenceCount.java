package com.back.domain.trend.snapshot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "daily_cooccurrence_count",
        uniqueConstraints = @UniqueConstraint(columnNames ={"date","keywordA","keywordB"}))
public class DailyCooccurrenceCount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate date;
    private String keywordA;
    private String keywordB;
    private long frequency;
    private long updateFrequency;
}
