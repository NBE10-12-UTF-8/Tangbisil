package com.back.domain.chat.chatRoomMessage.dto

// 참고: import만 있고 @NotBlank가 실제로 안 붙어있던 원본 Java 그대로 유지 (검증 미적용).
data class ChatRoomMessageRequestDto(
    val content: String?
)
