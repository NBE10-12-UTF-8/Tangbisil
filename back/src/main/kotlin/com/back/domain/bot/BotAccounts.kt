package com.back.domain.bot

import com.back.domain.member.member.entity.Industry
import java.util.UUID

// 지인 테스트용 봇 계정 관련 상수/유틸.
// 산업군마다 봇 하나씩 만들어서, 실제 유저가 매칭 요청을 넣으면 즉시 매칭되게 한다.
// 더 이상 필요 없어지면 이 bot 패키지 전체 + BaseInitData/MatchRequestService의
// 연동 코드만 지우면 깔끔하게 제거된다.
object BotAccounts {
    private const val EMAIL_PREFIX = "bot."
    private const val EMAIL_DOMAIN = "@tangbisil.bot"

    // 봇 계정은 아무도 로그인할 일이 없어서 비밀번호 값 자체는 의미 없음
    @JvmField
    val PASSWORD: String = UUID.randomUUID().toString()

    @JvmStatic
    fun emailFor(industry: Industry): String = EMAIL_PREFIX + industry.name.lowercase() + EMAIL_DOMAIN

    // 확장 함수로 선언하면서도 @JvmStatic을 붙여, Kotlin에서는 email.isBotEmail()로
    // 자연스럽게 쓰고 Java 쪽 기존 호출부(BotAccounts.isBotEmail(email))는 그대로 유지한다.
    @JvmStatic
    fun String?.isBotEmail(): Boolean =
        this != null && startsWith(EMAIL_PREFIX) && endsWith(EMAIL_DOMAIN)
}
