package com.back.domain.member.member.entity

import com.back.global.exception.ServiceException
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

// 테크스펙 FR-02 산업군 목록과 반드시 동기화해야 함
enum class Industry(@get:JsonValue val label: String) {
    IT("IT/개발"),
    SERVICE("서비스업"),
    FINANCE("금융업"),
    MEDICAL("의료서비스"),
    RETAIL("유통"),
    MEDIA("미디어/디자인"),
    OFFICE("사무업");

    companion object {
        private val LABEL_MAP: Map<String, Industry> = entries.associateBy { it.label }

        @JvmStatic
        @JsonCreator
        fun fromLabel(label: String): Industry =
            LABEL_MAP[label] ?: throw ServiceException("400-1", "허용되지 않는 산업군입니다.")
    }
}
