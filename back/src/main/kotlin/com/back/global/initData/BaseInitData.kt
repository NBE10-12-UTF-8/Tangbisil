package com.back.global.initData

import com.back.domain.bot.BotAccounts
import com.back.domain.member.member.entity.Industry
import com.back.domain.member.member.service.MemberService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.transaction.annotation.Transactional

@Configuration
class BaseInitData(
    private val memberService: MemberService
) {
    @Autowired
    @Lazy
    private lateinit var self: BaseInitData

    @Bean
    fun baseInitDataApplicationRunner(): ApplicationRunner =
        ApplicationRunner {
            self.work1()
        }

    @Transactional
    fun work1() {
        if (memberService.count() > 0) return

        // 시스템 및 관리자 계정 생성 (이메일 및 어드민 권한 매개변수 적용)
        // 주의: MemberService.join()을 직접 호출해 MemberSignupReq의 @NotNull industry 검증을 우회함.
        // 일반 회원가입은 반드시 컨트롤러(DTO 검증)를 거치므로 industry가 null일 수 없고,
        // admin만 이렇게 초기 데이터로 직접 생성되는 예외적인 경로임.
        memberService.joinWithoutEmailVerification("admin@test.com", "1234", null, "ADMIN")

        memberService.joinWithoutEmailVerification("user1@test.com", "1234", Industry.IT, "USER")
        memberService.joinWithoutEmailVerification("user2@test.com", "1234", Industry.OFFICE, "USER")
        memberService.joinWithoutEmailVerification("user3@test.com", "1234", Industry.FINANCE, "USER")

        for (industry in Industry.entries) {
            memberService.joinWithoutEmailVerification(BotAccounts.emailFor(industry), BotAccounts.PASSWORD, industry, "USER")
        }
    }
}
