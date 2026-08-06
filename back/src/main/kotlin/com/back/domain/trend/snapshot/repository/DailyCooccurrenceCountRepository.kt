package com.back.domain.trend.snapshot.repository

import com.back.domain.trend.snapshot.entity.DailyCooccurrenceCount
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface DailyCooccurrenceCountRepository : JpaRepository<DailyCooccurrenceCount, Long> {
    fun findByDateAndKeywordAAndKeywordB(date: LocalDate, keywordA: String, keywordB: String): DailyCooccurrenceCount?

    fun findAllByDate(date: LocalDate): List<DailyCooccurrenceCount>

    // keywordA/keywordB 둘 다 후보 키워드 집합에 속한 쌍만 조회한다.
    // 어느 쪽이 A/B로 저장됐는지는 쌍마다 다르므로(KeywordPairKey 정렬 규칙),
    // 두 IN절 모두에 같은 후보 집합을 넘겨야 "양쪽 다 후보인 쌍"이 정확히 걸러진다.
    fun findAllByDateAndKeywordAInAndKeywordBIn(
        date: LocalDate,
        keywordsA: Collection<String>,
        keywordsB: Collection<String>
    ): List<DailyCooccurrenceCount>
}
