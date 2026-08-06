package com.back.domain.chat.chatRoom.dto

// 상대방이 채팅방을 종료했을 때 실시간으로 알려주는 용도.
// 프론트는 messageId 필드 유무로 이 알림과 일반 채팅 메시지를 구분한다.
data class RoomClosedNotificationDto(val type: String) {
    companion object {
        @JvmStatic
        fun of(): RoomClosedNotificationDto = RoomClosedNotificationDto("ROOM_CLOSED")
    }
}
