package com.back.domain.bot

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

// Groq는 OpenAI Chat Completions API와 호환되는 엔드포인트를 제공해서,
// Spring AI 없이도 표준 REST 호출만으로 붙일 수 있다.
// (Spring AI 2.0이 이 프로젝트의 Spring Boot 4.1과 호환되는 정식 버전은 아직 마일스톤 단계라 보류)
@Component
class BotAiClient {

    companion object {
        private val log = LoggerFactory.getLogger(BotAiClient::class.java)

        private const val CONNECT_TIMEOUT_MS = 3000
        private const val READ_TIMEOUT_MS = 5000

        // Groq가 llama-3.x 계열을 deprecate하면서 권장하는 범용 모델.
        // 1~2문장 짧은 응답만 필요해서 더 작은 모델(20b)로도 충분하지만,
        // 응답 품질을 위해 현재는 70b를 사용 중.
        private const val MODEL = "llama-3.3-70b-versatile"
        private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

        private fun createRequestFactory(): SimpleClientHttpRequestFactory =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(CONNECT_TIMEOUT_MS)
                setReadTimeout(READ_TIMEOUT_MS)
            }
    }

    private val restClient = RestClient.builder()
        .requestFactory(createRequestFactory())
        .build()

    private val objectMapper = ObjectMapper()

    @Value("\${custom.bot.groq-api-key:}")
    private var apiKey: String? = null

    fun isEnabled(): Boolean = !apiKey.isNullOrBlank()

    /**
     * systemInstruction: 봇의 역할/말투를 지정하는 지시문
     * conversation: 시간순으로 정렬된 (isBot, content) 쌍 - 대화 맥락
     * 실패하거나 키가 없으면 null 반환 (호출부에서 캔드 메시지로 폴백)
     */
    fun generateReply(systemInstruction: String, conversation: List<Pair<Boolean, String>>): String? {
        if (!isEnabled()) {
            return null
        }

        return try {
            val messages = mutableListOf<Map<String, String>>()
            messages.add(mapOf("role" to "system", "content" to systemInstruction))
            conversation.forEach { (isBot, content) ->
                messages.add(mapOf("role" to if (isBot) "assistant" else "user", "content" to content))
            }

            val body = mapOf(
                "model" to MODEL,
                "messages" to messages,
                "temperature" to 0.2,
                "max_tokens" to 40
            )

            val response = restClient.post()
                .uri(ENDPOINT)
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String::class.java)

            val root = objectMapper.readTree(response)
            val text = root.path("choices").path(0).path("message").path("content").asText(null)

            if (text.isNullOrBlank()) null else text.trim()
        } catch (e: Exception) {
            log.error("[BotAiClient] Groq 호출 실패", e)
            null
        }
    }
}
