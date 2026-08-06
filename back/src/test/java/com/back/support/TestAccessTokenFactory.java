package com.back.support;

import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.repository.MemberRepository;
import com.back.domain.member.member.service.MemberService;

// 로그인 자체가 테스트 대상이 아니라 "인증된 사용자가 있다"는 전제조건만 필요한 컨트롤러
// 테스트들이 공통으로 쓰는 헬퍼. 실제 /login 엔드포인트를 왕복하지 않고 토큰 발급 로직을
// 직접 호출해 Authorization 헤더에 쓸 토큰을 받아온다 — CustomAuthenticationFilter가
// 쿠키뿐 아니라 Authorization 헤더도 계속 지원하므로(모바일 앱 등 쿠키를 못 쓰는 클라이언트
// 대응) 유효한 인증 경로다. 로그인/재발급 엔드포인트 자체를 검증하는 테스트(예:
// ApiV1MemberControllerTest)는 실제 쿠키 흐름을 그대로 재현해야 하므로 이 헬퍼 대신
// MvcResult에서 쿠키를 직접 추출해 쓴다.
public final class TestAccessTokenFactory {

    private TestAccessTokenFactory() {
    }

    public static String accessTokenFor(MemberRepository memberRepository, MemberService memberService, String email) {
        Member member = memberRepository.findByEmail(email);
        return memberService.genAccessToken(member);
    }
}
