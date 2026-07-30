package com.back.domain.member.member.controller;
import com.back.domain.match.matchRequest.dto.MatchHistoryDto;
import com.back.domain.member.emailVerification.service.EmailVerificationService;
import com.back.domain.member.member.dto.MemberDto;
import com.back.domain.member.member.dto.OAuthExchangeResult;
import com.back.domain.member.member.entity.Industry;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.service.MemberService;
import com.back.global.exception.ServiceException;
import com.back.global.rq.Rq;
import com.back.global.rsData.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Tag(name = "ApiV1MemberController", description = "API 회원 컨트롤러")
@SecurityRequirement(name = "bearerAuth")
public class ApiV1MemberController {
    private final MemberService memberService;
    private final EmailVerificationService emailVerificationService;
    private final Rq rq;
    @Value("${custom.accessToken.expirationSeconds}")
    private int accessTokenExpirationSeconds;
    @Value("${custom.refreshToken.expirationSeconds}")
    private int refreshTokenExpirationSeconds;

    public record MemberSignupReq(
            @NotBlank
            @Email
            @Size(min = 5, max = 50)
            String email,
            @NotBlank
            @Size(min = 4, max = 30)
            String password,
            @NotNull
            Industry industry
    ) {}
    public record MemberLoginReq(
            @NotBlank
            @Email
            @Size(min = 5, max = 50)
            String email,
            @NotBlank
            @Size(min = 4, max = 30)
            String password
    ) {}

    public record OAuthLoginReq(
            @NotBlank
            String code
    ){}

    public record OAuthExchangeRes(
            String grantType,
            String accessToken,
            String refreshToken,
            int accessTokenExpiresIn,
            boolean needsOnboarding
    ){}

    public record MemberLoginRes(
            String grantType,
            String accessToken,
            String refreshToken,
            int accessTokenExpiresIn
    ) {}

    @PostMapping("/signup")
    @Operation(summary = "회원가입")
    public RsData<MemberDto> signup(@Valid @RequestBody MemberSignupReq req) {
        Member member = memberService.join(req.email(), req.password(), req.industry(), "USER");
        return new RsData<>(
                "201-1",
                "회원 생성 성공",
                new MemberDto(member) // dto 패키지의 MemberDto 활용
        );
    }

    @PostMapping("/login")
    @Operation(summary = "로그인")
    public RsData<MemberLoginRes> login(@Valid @RequestBody MemberLoginReq req) {
        Member member = memberService.findByEmail(req.email())
                .orElseThrow(() -> new ServiceException("401-1", "존재하지 않는 이메일입니다."));

        memberService.checkPassword(member, req.password());

        String accessToken = memberService.genAccessToken(member);
        UUID refreshToken = memberService.genRefreshToken(member);

        rq.setCookie("accessToken", accessToken, accessTokenExpirationSeconds);
        rq.setCookie(
                "refreshToken",
                refreshToken.toString(),
                refreshTokenExpirationSeconds
        );
        return new RsData<>(
                "200-1",
                "로그인 생성 성공",
                new MemberLoginRes(
                        "Bearer",
                        accessToken,
                        refreshToken.toString(),
                        accessTokenExpirationSeconds)
        );

    }

    @PostMapping("/oauth/exchange")
    @Operation(summary = "소셜 로그인 code 교환")
    public RsData<OAuthExchangeRes> oauthExchange(@Valid @RequestBody OAuthLoginReq req) {
        OAuthExchangeResult result = memberService.exchangeOAuthCode(req.code());

        rq.setCookie("accessToken", result.accessToken(), accessTokenExpirationSeconds);
        rq.setCookie("refreshToken", result.refreshToken().toString(), refreshTokenExpirationSeconds);

        return new RsData<>(
                "200-1",
                "소셜 로그인 성공",
                new OAuthExchangeRes(
                        "Bearer",
                        result.accessToken(),
                        result.refreshToken().toString(),
                        accessTokenExpirationSeconds,
                        result.needsOnboarding()
                )
        );
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃")
    public RsData<Void> logout() {
        Member actor = rq.getActor();
        memberService.clearRefreshToken(actor);
        rq.deleteCookie("accessToken");
        rq.deleteCookie("refreshToken");

        return new RsData<>(
                "200-1",
                "로그아웃 생성 성공"
        );
    }
    public record MemberMeRes(
            String email,
            Industry industry
    ) {}

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회")
    public RsData<MemberMeRes> me() {
        Member actor = memberService.findById(rq.getActor().getId())
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 회원입니다."));

        return new RsData<>(
                "200-1",
                "내 정보 조회 성공",
                new MemberMeRes(actor.getEmail(), actor.getIndustry())
        );
    }
    public record MemberUpdateIndustryReq(
            @NotNull
            Industry industry
    ) {}

    public record MemberUpdateIndustryRes(
            @NotNull
            Industry industry
    ) {}

    @PatchMapping("/me")
    @Operation(summary = "산업군 수정")
    public RsData<MemberUpdateIndustryRes> updateIndustry(
            @Valid @RequestBody MemberUpdateIndustryReq req
    ) {
        Member actor = rq.getActor();
        memberService.updateIndustry(actor, req.industry());

        return new RsData<>(
                "200-1",
                "소속 산업군 수정 성공",
                new MemberUpdateIndustryRes(req.industry())
        );
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "회원 탈퇴")
    public RsData<Void> delete() {
        Member actor = rq.getActor();
        memberService.delete(actor);
        rq.deleteCookie("accessToken");
        rq.deleteCookie("refreshToken");

        return new RsData<>(
                "200-1",
                "회원 삭제 성공"
        );
    }

    @GetMapping("/me/matches")
    @Operation(summary = "매치 기록 조회")
    public RsData<List<MatchHistoryDto>> findMatchHistory() {
        Member actor = rq.getActor();
        return new RsData<>(
                "200-1",
                "괴거 매칭 이력 조회 성공",
                memberService.getMatchHistory(actor)
        );
    }
    @PostMapping("/refresh")
    @Operation(summary = "AccessToken 재발급")
    public RsData<MemberLoginRes> refresh() {

        String refreshTokenValue =
                rq.getCookieValue("refreshToken", "");

        if (refreshTokenValue.isBlank()) {
            throw new ServiceException(
                    "401-1",
                    "RefreshToken이 존재하지 않습니다."
            );
        }

        UUID refreshToken =
                UUID.fromString(refreshTokenValue);

        String accessToken =
                memberService.refreshAccessToken(refreshToken);

        rq.setCookie(
                "accessToken",
                accessToken,
                accessTokenExpirationSeconds
        );

        return new RsData<>(
                "200-1",
                "AccessToken 재발급 성공",
                new MemberLoginRes(
                        "Bearer",
                        accessToken,
                        refreshTokenValue,
                        accessTokenExpirationSeconds
                )
        );
    }
    public record EmailVerificationSendReq(
            @NotBlank @Email String email
    ) {}

    public record EmailVerificationConfirmReq(
            @NotBlank @Email String email,
            @NotBlank String code
    ) {}

    @PostMapping("/email-verification/send")
    @Operation(summary = "이메일 인증 코드 발송")
    public RsData<Void> sendEmailVerification(@Valid @RequestBody EmailVerificationSendReq req) {
        emailVerificationService.sendVerificationCode(req.email());
        return new RsData<>("200-1", "인증 코드 발송 성공");
    }

    @PostMapping("/email-verification/confirm")
    @Operation(summary = "이메일 인증 코드 확인")
    public RsData<Void> confirmEmailVerification(@Valid @RequestBody EmailVerificationConfirmReq req) {
        emailVerificationService.confirmVerificationCode(req.email(), req.code());
        return new RsData<>("200-1", "이메일 인증 성공");
    }
}
