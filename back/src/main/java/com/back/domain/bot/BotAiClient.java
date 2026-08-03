package com.back.domain.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.List;
import java.util.Map;

// Groq는 OpenAI Chat Completions API와 호환되는 엔드포인트를 제공해서,
// Spring AI 없이도 표준 REST 호출만으로 붙일 수 있다.
// (Spring AI 2.0이 이 프로젝트의 Spring Boot 4.1과 호환되는 정식 버전은 아직 마일스톤 단계라 보류)
@Slf4j
@Component
public class BotAiClient {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;

    private final RestClient restClient = RestClient.builder()
            .requestFactory(createRequestFactory())
            .build();

    private final ObjectMapper objectMapper =new ObjectMapper();

    private static SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return factory;
    }

    @Value("${custom.bot.groq-api-key:}")
    private String apiKey;

    // Groq가 llama-3.x 계열을 deprecate하면서 권장하는 범용 모델.
    // 1~2문장 짧은 응답만 필요해서 더 작은 모델(20b)로도 충분하지만,
    // 응답 품질을 위해 현재는 70b를 사용 중.
    private static final String MODEL = "llama-3.3-70b-versatile";
    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * systemInstruction: 봇의 역할/말투를 지정하는 지시문
     * conversation: 시간순으로 정렬된 (isBot, content) 쌍 - 대화 맥락
     * 실패하거나 키가 없으면 null 반환 (호출부에서 캔드 메시지로 폴백)
     */
    public String generateReply(String systemInstruction, List<Map.Entry<Boolean, String>> conversation) {
        if (!isEnabled()) {
            return null;
        }

        try {
            List<Map<String, String>> messages = new java.util.ArrayList<>();
            messages.add(Map.of(
                    "role",
                    "system",
                    "content",
                    systemInstruction
            ));

            conversation.forEach(turn -> messages.add(Map.of(
                    "role",
                    turn.getKey() ? "assistant" : "user",
                    "content",
                    turn.getValue()
            )));

            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "messages", messages,
                    "temperature", 0.2,
                    "max_tokens", 40
            );

            String response = restClient.post()
                    .uri(ENDPOINT)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String text = root.path("choices").path(0)
                    .path("message").path("content")
                    .asText(null);

            return (text == null || text.isBlank()) ? null : text.trim();
        } catch (Exception e) {
            log.error("[BotAiClient] Groq 호출 실패", e);
            return null;
        }
    }
}