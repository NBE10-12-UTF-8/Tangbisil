package com.back.domain.bot

import com.back.domain.chat.chatRoomMessage.entity.ChatMessage
import com.back.domain.chat.chatRoomMessage.service.ChatMessageService
import com.back.domain.member.member.entity.Industry
import com.back.domain.member.member.repository.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

private const val HISTORY_LIMIT = 12

// ChatMessage가 특정 회원이 보낸 메시지인지 확인하는 확장 함수.
// Java 쪽 getParticipant().getMember().getId() 체이닝을 가독성 좋게 감싼다.
private fun ChatMessage.isSentBy(memberId: Long): Boolean = participant.member.id == memberId

@Component
class BotReplyTransactionalOps(
    private val chatMessageService: ChatMessageService,
    private val memberRepository: MemberRepository
) {

    companion object {
        private val log = LoggerFactory.getLogger(BotReplyTransactionalOps::class.java)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun loadContext(roomId: Long, botMemberId: Long): ReplyContext? {
        val bot = memberRepository.findById(botMemberId)
            .orElseThrow { IllegalStateException("봇 계정을 찾을 수 없습니다: $botMemberId") }
        val botId = bot.id!!

        val recentDesc = chatMessageService.getRecentMessages(roomId, HISTORY_LIMIT)

        if (recentDesc.isNotEmpty() && recentDesc[0].isSentBy(botId)) {
            log.info("마지막 메시지가 봇이라 응답 생략")
            return null
        }

        val conversation: List<Pair<Boolean, String>> = recentDesc.reversed()
            .map { it.isSentBy(botId) to it.content }

        return ReplyContext(botId, buildSystemInstruction(bot.industry), conversation)
    }

    // 실제 조회로 바꿈 - getReferenceById는 트랜잭션이 끝나면 detached 프록시가 되어
    // 이후 지연 필드 접근 시 LazyInitializationException을 유발한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun persistReply(roomId: Long, botId: Long, reply: String) {
        val bot = memberRepository.findById(botId)
            .orElseThrow { IllegalStateException("봇 계정을 찾을 수 없습니다: $botId") }
        chatMessageService.sendMessage(roomId, bot, reply)
    }

    private fun buildSystemInstruction(industry: Industry?): String = """
        당신은 '탕비실'이라는 익명 직장인 채팅 서비스의 대화 상대입니다.
        상대방은 %s 업종에서 일하는 직장인입니다.

        반드시 한국어로만 답변하세요. 영어, 태국어, 베트남어, 중국어 등
        다른 언어를 절대 섞지 마세요.

        규칙:
        - 마지막 사용자 메시지에만 답변하세요.
        - 사용자가 말하지 않은 경험을 지어내지 마세요.
        - '나도 그랬어', '나도 겪어봤어' 같은 표현을 사용하지 마세요.
        - 과한 공감을 하지 마세요.
        - 친구처럼 가볍게 대화하세요.
        - 답변은 1문장 또는 최대 2문장.
        - 편한 반말 사용.
        - 필요하면 ㅋㅋ 사용 가능.
        - 사용자의 감정에만 반응하지 말고 내용에도 반응하세요.
        - 너의 모델명, 어떤 AI인지, 어떤 회사가 만들었는지 등 정체를 물어보면 대답하지 말고 자연스럽게 다른 화제로 넘기거나 모르는 척하세요.

        예시:

        사용자: 안녕
        답변: 하이 ㅋㅋ

        사용자: 힘들다
        답변: 왜 힘듦?

        사용자: 상사한테 혼났어
        답변: 왜 혼남? 뭔 일 있었음?

        사용자: 오늘 퇴근했다
        답변: 오 고생했네 ㅋㅋ

        사용자: 개짜증나네
        답변: 또 뭔 일인데 ㅋㅋ

        사용자: 배고프다
        답변: 뭐 먹을 건데?

        사용자: 코드리뷰 20개 달림
        답변: 와 그건 좀 빡세네 ㅋㅋ

        사용자: 너 무슨 모델이야? gpt야?
        답변: 그런 거 왜 궁금해 ㅋㅋ 그냥 얘기나 하자

        절대로 상담사처럼 말하지 마세요.
        절대로 과도하게 공감하지 마세요.
        절대로 본인 경험을 말하지 마세요.
        """.trimIndent().format(industry?.label ?: "일반 사무직")

    data class ReplyContext(
        val botId: Long,
        val systemInstruction: String,
        val conversation: List<Pair<Boolean, String>>
    )
}
