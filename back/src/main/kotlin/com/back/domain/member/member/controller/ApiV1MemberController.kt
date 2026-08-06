package com.back.domain.member.member.controller

import com.back.domain.match.matchRequest.dto.MatchHistoryDto
import com.back.domain.member.emailVerification.service.EmailVerificationService
import com.back.domain.member.member.dto.MemberDto
import com.back.domain.member.member.entity.Industry
import com.back.domain.member.member.service.LoginAttemptLimiter
import com.back.domain.member.member.service.MemberService
import com.back.domain.member.passwordReset.service.PasswordResetService
import com.back.global.exception.ServiceException
import com.back.global.rq.Rq
import com.back.global.rsData.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/members")
@Tag(name = "ApiV1MemberController", description = "API 회원 컨트롤러")
@SecurityRequirement(name = "bearerAuth")
class ApiV1MemberController(
    private val memberService: MemberService,
    private val emailVerificationService: EmailVerificationService,
    private val rq: Rq,
    private val passwordResetService: PasswordResetService,
    private val loginAttemptLimiter: LoginAttemptLimiter
) {
    @Value("\${custom.accessToken.expirationSeconds}")
    private var accessTokenExpirationSeconds: Int = 0

    @Value("\${custom.refreshToken.expirationSeconds}")
    private var refreshTokenExpirationSeconds: Int = 0

    data class MemberSignupReq(
        @field:NotBlank @field:Email @field:Size(min = 5, max = 50) val email: String,
        @field:NotBlank @field:Size(min = 4, max = 30) val password: String,
        @field:NotNull val industry: Industry?,
        @field:AssertTrue(message = "약관 및 개인정보 수집에 동의해야 합니다.") val agreedToTerms: Boolean
    )

    data class MemberLoginReq(
        @field:NotBlank @field:Email @field:Size(min = 5, max = 50) val email: String,
        @field:NotBlank @field:Size(min = 4, max = 30) val password: String
    )

    data class MemberMeRes(
        val email: String?,
        val industry: Industry?,
        val role: String?
    )

    data class MemberUpdateIndustryReq(
        @field:NotNull val industry: Industry
    )

    data class MemberUpdateIndustryRes(
        @field:NotNull val industry: Industry
    )

    data class EmailVerificationSendReq(
        @field:NotBlank @field:Email val email: String
    )

    data class EmailVerificationConfirmReq(
        @field:NotBlank @field:Email val email: String,
        @field:NotBlank val code: String
    )

    data class PasswordResetSendReq(
        @field:NotBlank @field:Email @field:Size(min = 5, max = 50) val email: String
    )

    data class PasswordResetConfirmReq(
        @field:NotBlank @field:Email @field:Size(min = 5, max = 50) val email: String,
        @field:NotBlank @field:Pattern(regexp = "^\\d{6}$", message = "인증 코드는 6자리 숫자여야 합니다.") val code: String,
        @field:NotBlank @field:Size(min = 4, max = 30) val newPassword: String
    )

    @PostMapping("/signup")
    @Operation(summary = "회원가입")
    fun signup(@Valid @RequestBody req: MemberSignupReq): RsData<MemberDto> {
        val member = memberService.join(req.email, req.password, req.industry, "USER")
        return RsData(
            "201-1",
            "회원 생성 성공",
            MemberDto(member) // dto 패키지의 MemberDto 활용
        )
    }

    @PostMapping("/login")
    @Operation(summary = "로그인")
    fun login(@Valid @RequestBody req: MemberLoginReq): RsData<MemberMeRes> {
        if (loginAttemptLimiter.isBlocked(req.email)) {
            throw ServiceException("429-1", "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요.")
        }

        val member = memberService.findByEmail(req.email).orElseThrow {
            loginAttemptLimiter.recordFailure(req.email)
            ServiceException("401-1", "존재하지 않는 이메일입니다.")
        }

        try {
            memberService.checkPassword(member, req.password)
        } catch (e: ServiceException) {
            loginAttemptLimiter.recordFailure(req.email)
            throw e
        }

        loginAttemptLimiter.recordSuccess(req.email)

        val accessToken = memberService.genAccessToken(member)
        val refreshToken = memberService.genRefreshToken(member)

        rq.setCookie("accessToken", accessToken, accessTokenExpirationSeconds)
        rq.setCookie(
            "refreshToken",
            refreshToken.toString(),
            refreshTokenExpirationSeconds,
            Rq.REFRESH_TOKEN_COOKIE_PATH
        )

        // 토큰은 HttpOnly 쿠키로만 내려간다. 응답 바디에 실으면 프론트가 JS(localStorage 등)에
        // 들고 있게 되어 HttpOnly의 XSS 방어 효과가 사라지므로, 로그인 성공 여부 확인에
        // 필요한 최소 정보(/me와 동일한 모양)만 반환한다.
        return RsData(
            "200-1",
            "로그인 생성 성공",
            MemberMeRes(member.email, member.industry, member.role)
        )
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃")
    fun logout(): RsData<Void> {
        val actor = rq.actor ?: throw ServiceException("401-1", "인증이 필요합니다.")
        memberService.clearRefreshToken(actor)
        rq.deleteCookie("accessToken")
        rq.deleteCookie("refreshToken", Rq.REFRESH_TOKEN_COOKIE_PATH)

        return RsData(
            "200-1",
            "로그아웃 생성 성공"
        )
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회")
    fun me(): RsData<MemberMeRes> {
        val loginActor = rq.actor ?: throw ServiceException("401-1", "인증이 필요합니다.")
        val actor = memberService.findById(loginActor.id!!)
            .orElseThrow { ServiceException("404-1", "존재하지 않는 회원입니다.") }

        return RsData(
            "200-1",
            "내 정보 조회 성공",
            MemberMeRes(actor.email, actor.industry, actor.role)
        )
    }

    @PatchMapping("/me")
    @Operation(summary = "산업군 수정")
    fun updateIndustry(@Valid @RequestBody req: MemberUpdateIndustryReq): RsData<MemberUpdateIndustryRes> {
        val actor = rq.actor ?: throw ServiceException("401-1", "인증이 필요합니다.")
        memberService.updateIndustry(actor, req.industry)

        return RsData(
            "200-1",
            "소속 산업군 수정 성공",
            MemberUpdateIndustryRes(req.industry)
        )
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "회원 탈퇴")
    fun delete(): RsData<Void> {
        val actor = rq.actor ?: throw ServiceException("401-1", "인증이 필요합니다.")
        memberService.delete(actor)
        rq.deleteCookie("accessToken")
        rq.deleteCookie("refreshToken", Rq.REFRESH_TOKEN_COOKIE_PATH)

        return RsData(
            "200-1",
            "회원 삭제 성공"
        )
    }

    @GetMapping("/me/matches")
    @Operation(summary = "매치 기록 조회")
    fun findMatchHistory(): RsData<List<MatchHistoryDto>> {
        val actor = rq.actor ?: throw ServiceException("401-1", "인증이 필요합니다.")
        return RsData(
            "200-1",
            "괴거 매칭 이력 조회 성공",
            memberService.getMatchHistory(actor)
        )
    }

    @PostMapping("/refresh")
    @Operation(summary = "AccessToken 재발급")
    fun refresh(): RsData<Void> {
        val refreshTokenValue = rq.getCookieValue("refreshToken", "")

        if (refreshTokenValue.isBlank()) {
            throw ServiceException(
                "401-1",
                "RefreshToken이 존재하지 않습니다."
            )
        }

        val refreshToken = UUID.fromString(refreshTokenValue)

        val accessToken = memberService.refreshAccessToken(refreshToken)

        rq.setCookie(
            "accessToken",
            accessToken,
            accessTokenExpirationSeconds
        )

        // 새 accessToken은 쿠키로만 내려간다. 재발급 성공 여부만 알면 프론트는
        // 원래 요청을 그대로 재시도할 수 있어 바디에 토큰을 실을 필요가 없다.
        return RsData(
            "200-1",
            "AccessToken 재발급 성공"
        )
    }

    @PostMapping("/email-verification/send")
    @Operation(summary = "이메일 인증 코드 발송")
    fun sendEmailVerification(@Valid @RequestBody req: EmailVerificationSendReq): RsData<Void> {
        emailVerificationService.sendVerificationCode(req.email)
        return RsData("200-1", "인증 코드 발송 성공")
    }

    @PostMapping("/email-verification/confirm")
    @Operation(summary = "이메일 인증 코드 확인")
    fun confirmEmailVerification(@Valid @RequestBody req: EmailVerificationConfirmReq): RsData<Void> {
        emailVerificationService.confirmVerificationCode(req.email, req.code)
        return RsData("200-1", "이메일 인증 성공")
    }

    @PostMapping("/password-reset/send")
    @Operation(summary = "비밀번호 재설정 코드 발송")
    fun sendPasswordReset(@Valid @RequestBody req: PasswordResetSendReq): RsData<Void> {
        passwordResetService.sendResetCode(req.email)
        return RsData("200-1", "재설정 코드 발송 성공")
    }

    @PostMapping("/password-reset/confirm")
    @Operation(summary = "비밀번호 재설정")
    fun confirmPasswordReset(@Valid @RequestBody req: PasswordResetConfirmReq): RsData<Void> {
        passwordResetService.resetPassword(req.email, req.code, req.newPassword)
        return RsData("200-1", "비밀번호 재설정 성공")
    }
}
