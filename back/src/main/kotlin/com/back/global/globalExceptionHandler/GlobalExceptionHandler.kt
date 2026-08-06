package com.back.global.globalExceptionHandler

import com.back.global.exception.ServiceException
import com.back.global.rsData.RsData
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.NoSuchElementException

@RestControllerAdvice
class GlobalExceptionHandler {
    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handle(ex: NoSuchElementException): ResponseEntity<RsData<Void>> =
        ResponseEntity(
            RsData("404-1", "해당 데이터가 존재하지 않습니다."),
            HttpStatus.NOT_FOUND
        )

    @ExceptionHandler(ConstraintViolationException::class)
    fun handle(ex: ConstraintViolationException): ResponseEntity<RsData<Void>> {
        val message = ex.constraintViolations
            .map { violation ->
                val field = violation.propertyPath.toString().split(".", limit = 2)[1]
                val messageTemplateBits = violation.messageTemplate.split(".")
                val code = messageTemplateBits[messageTemplateBits.size - 2]
                val violationMessage = violation.message

                "$field-$code-$violationMessage"
            }
            .sortedBy { it }
            .joinToString("\n")

        return ResponseEntity(
            RsData("400-1", message),
            HttpStatus.BAD_REQUEST
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handle(ex: MethodArgumentNotValidException): ResponseEntity<RsData<Void>> {
        val msg = ex.bindingResult
            .allErrors
            .filterIsInstance<FieldError>()
            .map { "${it.field}-${it.code}-${it.defaultMessage}" }
            .sorted()
            .joinToString("\n")

        return ResponseEntity(
            RsData("400-1", msg),
            HttpStatus.BAD_REQUEST
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handle(ex: HttpMessageNotReadableException): ResponseEntity<RsData<Void>> {
        // Jackson이 @JsonCreator에서 던진 예외(예: enum 화이트리스트 검증 실패)를
        // InvalidFormatException 등으로 감싸버리므로, cause 체인에서 ServiceException을 찾아
        // 원래 메시지를 그대로 내려준다. 못 찾으면 기존 범용 메시지로 fallback.
        var cause = ex.cause
        while (cause != null) {
            if (cause is ServiceException) {
                return ResponseEntity(cause.rsData, HttpStatus.BAD_REQUEST)
            }
            cause = cause.cause
        }
        return ResponseEntity(
            RsData("400-1", "요청 본문이 올바르지 않습니다."),
            HttpStatus.BAD_REQUEST
        )
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handle(ex: MissingRequestHeaderException): ResponseEntity<RsData<Void>> =
        ResponseEntity(
            RsData("400-1", "${ex.headerName}-NotBlank-${ex.localizedMessage}"),
            HttpStatus.BAD_REQUEST
        )

    @ExceptionHandler(ServiceException::class)
    fun handle(ex: ServiceException, response: HttpServletResponse): RsData<Void> {
        val rsData = ex.rsData

        response.status = rsData.statusCode

        return rsData
    }

    // Jackson(HttpMessageNotReadableException)뿐 아니라, JPA/Hibernate가 AttributeConverter
    // 내부에서 던진 ServiceException을 자체 예외(예: DataAccessException 계열)로 감싸는 경우까지
    // 대비하는 범용 fallback. 어떤 종류로 감싸이든 cause 체인에서 ServiceException을 찾아
    // 원래 의도한 응답으로 복원하고, 못 찾으면 진짜 예상치 못한 버그이므로 500-1로 응답한다.
    // 단, ServiceException 자체와 위에서 이미 구체적으로 처리한 예외 타입들은 더 특이적인
    // 핸들러가 우선 매칭되므로 이 메서드까지 내려오지 않는다.
    @ExceptionHandler(RuntimeException::class)
    fun handle(ex: RuntimeException): ResponseEntity<RsData<Void>> {
        var cause: Throwable? = ex
        while (cause != null) {
            if (cause is ServiceException) {
                return ResponseEntity(cause.rsData, HttpStatusCode.valueOf(cause.rsData.statusCode))
            }
            cause = cause.cause
        }

        log.error("[GlobalExceptionHandler] 예상치 못한 예외 발생", ex)
        return ResponseEntity(
            RsData("500-1", "서버 오류가 발생했습니다."),
            HttpStatus.INTERNAL_SERVER_ERROR
        )
    }
}
