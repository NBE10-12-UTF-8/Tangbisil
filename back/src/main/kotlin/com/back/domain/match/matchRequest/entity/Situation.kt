package com.back.domain.match.matchRequest.entity

import com.back.global.exception.ServiceException
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

// 테크스펙 FR-02 상황 목록과 반드시 동기화해야 함
enum class Situation(@get:JsonValue val label: String) {
    NIGHT_WORK("야근 중"),
    MEETING_BOMB("회의 폭탄"),
    OFFICE_ROMANCE_LEAK("사내 연애 폭로"),
    BOSS_BLAME("상사 억까"),
    OFFICE_POLITICS_FATIGUE("사내 정치 피로"),
    JOB_CHANGE_URGE("이직 마려움"),
    SALARY_NEGOTIATION("연봉 협상 앞둠"),
    SLACKING("몰래 루팡중"),
    OTHER("기타");

    companion object {
        private val LABEL_MAP: Map<String, Situation> = entries.associateBy { it.label }

        @JvmStatic
        @JsonCreator
        fun fromLabel(label: String): Situation =
            LABEL_MAP[label] ?: throw ServiceException("400-1", "허용되지 않는 상황 값입니다.")
    }
}
