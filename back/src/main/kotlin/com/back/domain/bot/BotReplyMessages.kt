package com.back.domain.bot

// 봇이 채팅방에서 자동으로 보낼 문구 목록.
// 실제 상황(situation)에 안 맞아도 자연스럽게 들리도록 범용적인 공감 문구로 구성.
// 자유롭게 추가/수정해도 됨.
object BotReplyMessages {
    @JvmField
    val LINES: List<String> = listOf(
        "오 완전 공감 ㅋㅋ",
        "무슨 일 있었음?",
        "아 진짜 힘들듯...",
        "나도 비슷한 일 겪은 적 있어서 더 와닿는다",
        "나만 이런 거 아니네 ㅎㅎ",
        "그거 완전 스트레스일 것 같아",
        "잠깐 이렇게 얘기하니까 좀 낫네"
    )
}
