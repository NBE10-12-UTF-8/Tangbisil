package com.back.global.email

import com.back.global.exception.ServiceException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class ResendEmailService {
    private val log = LoggerFactory.getLogger(ResendEmailService::class.java)

    @Value("\${custom.resend.api-key}")
    private lateinit var apiKey: String

    @Value("\${custom.resend.from-email}")
    private lateinit var fromEmail: String

    private val restClient: RestClient = RestClient.create("https://api.resend.com")

    fun send(to: String, subject: String, html: String) {
        try {
            restClient.post()
                .uri("/emails")
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    mapOf(
                        "from" to fromEmail,
                        "to" to listOf(to),
                        "subject" to subject,
                        "html" to html
                    )
                )
                .retrieve()
                .toBodilessEntity()
        } catch (e: Exception) {
            log.error("Resend 이메일 발송 실패: to={}", to, e)
            throw ServiceException("500-1", "이메일 발송에 실패했습니다.")
        }
    }
}
