package com.back.domain.member.member.service;

import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static com.back.domain.member.member.entity.Industry.IT;
import static org.assertj.core.api.Assertions.assertThat;

// 클래스 레벨 @Transactional로 감싸면 테스트 안의 모든 서비스 호출이 하나의 세션/트랜잭션을
// 공유해서 detached 엔티티 저장 누락 버그를 못 잡는다(엔티티가 계속 같은 영속성 컨텍스트에
// 붙어있으므로). 그래서 이 테스트는 일부러 @Transactional을 안 붙이고, 실제 운영처럼
// 서비스 호출마다 트랜잭션 경계가 갈라지게 둔다.
@ActiveProfiles("test")
@SpringBootTest
class MemberServiceTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("genRefreshToken으로 발급한 토큰은 실제로 DB에 저장되어 findByRefreshToken으로 조회된다")
    void t1() {
        // Given - member는 이 시점 이후 트랜잭션이 끝나서 detached 상태가 된다
        Member member = memberService.joinWithoutEmailVerification("refresh-persist@test.com", "1234", IT, "USER");

        // When - 로그인 컨트롤러/OAuth2LoginSuccessHandler와 동일하게, detached된 member로 별도 호출
        UUID token = memberService.genRefreshToken(member);

        // Then
        assertThat(memberRepository.findByRefreshToken(token)).isPresent();
    }
}
