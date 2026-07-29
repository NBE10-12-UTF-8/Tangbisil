package com.back.global.email;

import com.back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ResendEmailService {
    @Value("${custom.resend.api-key}")
    private String apiKey;

    @Value("${custom.resend.from-email}")
    private String fromEmail;

    private final RestClient restClient = RestClient.create("https://api.resend.com");

    public void send(String to, String subject, String html) {
        try {
            restClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", fromEmail,
                            "to", List.of(to),
                            "subject", subject,
                            "html", html
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new ServiceException("500-1", "이메일 발송에 실패했습니다.");
        }
    }
}