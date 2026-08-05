package com.back.domain.bot

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import kotlin.random.Random

private const val MIN_DELAY_MS = 1500
private const val MAX_EXTRA_DELAY_MS = 3000

@Service
class BotReplyService(
    private val botReplyExecutor: BotReplyExecutor
) {

    companion object {
        private val log = LoggerFactory.getLogger(BotReplyService::class.java)
    }

    // 매칭 트랜잭션/메시지 전송 트랜잭션이 실제로 커밋된 뒤에만 실행 - 커밋 전에 실행하면
    // 다른 스레드(비동기)가 아직 존재하지 않는 채팅방/메시지를 조회하게 되어 실패한다.
    //
    // sleep은 여기서 트랜잭션 없이 소비한다 - @Transactional 안에서 sleep하면
    // 그동안 DB 커넥션을 붙잡고 있어서 동시 요청이 몰릴 때 HikariCP 풀이 고갈될 수 있다.
    // 실제 DB 조회/전송은 별도 빈(BotReplyExecutor)의 트랜잭션 메서드에서 처리한다.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleReplyTrigger(event: BotReplyTriggerEvent) {
        try {
            Thread.sleep((MIN_DELAY_MS + Random.nextInt(MAX_EXTRA_DELAY_MS)).toLong())
            botReplyExecutor.execute(event)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            log.warn("[BotReplyService] 응답 대기 중 인터럽트 발생 - roomId: {}", event.roomId)
        } catch (e: Exception) {
            log.error("[BotReplyService] 응답 실패 - roomId: {}", event.roomId, e)
        }
    }
}
