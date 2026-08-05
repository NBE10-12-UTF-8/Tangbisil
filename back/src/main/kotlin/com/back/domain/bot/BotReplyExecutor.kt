package com.back.domain.bot

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class BotReplyExecutor(
    private val ops: BotReplyTransactionalOps,
    private val botAiClient: BotAiClient
) {

    companion object {
        private val log = LoggerFactory.getLogger(BotReplyExecutor::class.java)
    }

    fun execute(event: BotReplyTriggerEvent) {
        val context = ops.loadContext(event.roomId, event.botMemberId) ?: return
        val reply = callAi(context)
        ops.persistReply(event.roomId, context.botId, reply)
    }

    private fun callAi(context: BotReplyTransactionalOps.ReplyContext): String {
        context.conversation.forEach { (isBot, content) ->
            log.debug("{} : {}", if (isBot) "BOT" else "USER", content)
        }

        val aiReply = botAiClient.generateReply(context.systemInstruction, context.conversation)
        log.info("AI 응답 = {}", aiReply)

        if (aiReply != null) {
            return aiReply
        }
        log.warn("AI 실패 -> 폴백 메시지 사용")
        val lines = BotReplyMessages.LINES
        return lines[Random.nextInt(lines.size)]
    }
}
