package com.back.domain.member.member.service

import com.back.domain.chat.chatRoomMessage.service.ChatMessageService
import com.back.domain.chat.chatRoomParticipant.service.ChatRoomParticipantService
import com.back.domain.match.matchRequest.dto.MatchHistoryDto
import com.back.domain.match.matchRequest.service.MatchRequestService
import com.back.domain.member.emailVerification.service.EmailVerificationService
import com.back.domain.member.member.dto.MemberAdmDto
import com.back.domain.member.member.entity.AuthProvider
import com.back.domain.member.member.entity.Industry
import com.back.domain.member.member.entity.Member
import com.back.domain.member.member.repository.MemberRepository
import com.back.global.exception.ServiceException
import com.back.global.util.EmailNormalizer
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MemberService(
    private val authTokenService: AuthTokenService,
    private val passwordEncoder: PasswordEncoder,
    private val memberRepository: MemberRepository,
    private val matchRequestService: MatchRequestService,
    private val chatRoomParticipantService: ChatRoomParticipantService,
    private val chatMessageService: ChatMessageService,
    private val emailVerificationService: EmailVerificationService
) {
    fun count(): Long = memberRepository.count()

    @Transactional
    fun join(email: String, password: String, industry: Industry?, role: String): Member {
        val normalizedEmail = EmailNormalizer.normalize(email)

        findByEmail(normalizedEmail).ifPresent {
            throw ServiceException("409-1", "이미 존재하는 이메일입니다.")
        }

        emailVerificationService.consumeVerifiedEmail(normalizedEmail)

        val encodedPassword = passwordEncoder.encode(password)!!
        val member = Member(normalizedEmail, encodedPassword, industry, role)
        member.markConsented()

        return memberRepository.save(member)
    }

    // 초기 데이터(BaseInitData) 시딩 전용 - 이메일 인증 절차를 건너뛴다.
    // 일반 회원가입 API(join)는 반드시 이메일 인증을 거쳐야 하지만,
    // 시스템 초기 계정/봇 계정은 실제 이메일 소유자가 없으므로 이 경로로 생성한다.
    @Transactional
    fun joinWithoutEmailVerification(email: String, password: String, industry: Industry?, role: String): Member {
        val normalizedEmail = EmailNormalizer.normalize(email)

        findByEmail(normalizedEmail).ifPresent {
            throw ServiceException("409-1", "이미 존재하는 이메일입니다.")
        }

        val encodedPassword = passwordEncoder.encode(password)!!
        val member = Member(normalizedEmail, encodedPassword, industry, role)

        return memberRepository.save(member)
    }

    @Transactional
    fun findOrCreateOAuthMember(email: String, provider: AuthProvider, providerId: String): Member {
        val existingMember = memberRepository.findByProviderAndProviderId(provider, providerId)
        if (existingMember != null) {
            return existingMember
        }

        return try {
            val newMember = Member.ofOAuth(email, provider, providerId)
            memberRepository.save(newMember)
        } catch (e: DataIntegrityViolationException) {
            // 동시에 같은 (provider, providerId)로 로그인 시도가 들어와 다른 요청이 먼저 저장한 경우
            memberRepository.findByProviderAndProviderId(provider, providerId) ?: throw e
        }
    }

    fun checkPassword(member: Member, password: String) {
        if (!passwordEncoder.matches(password, member.password)) {
            throw ServiceException("401-1", "비밀번호가 일치하지 않습니다.")
        }
    }

    // MemberService의 finder는 다양한 Java/Kotlin 호출부(보안 인프라 포함)가 Optional을
    // 전제로 하고 있어, 리포지토리 계층(nullable)과 달리 여기서는 Optional 경계를 유지한다.
    fun findByEmail(email: String): Optional<Member> = Optional.ofNullable(memberRepository.findByEmail(email))

    fun genAccessToken(member: Member): String = authTokenService.genAccessToken(member)

    @Transactional
    fun genRefreshToken(member: Member): UUID {
        // 호출부(로그인 컨트롤러/OAuth2LoginSuccessHandler)에서 넘어오는 member는 이 메서드의
        // 트랜잭션 밖에서 조회된 detached 엔티티라, save 없이 필드만 바꾸면 DB에 반영되지 않는다.
        val token = UUID.randomUUID()
        member.updateRefreshToken(token)
        memberRepository.save(member)
        return token
    }

    fun refreshAccessToken(refreshToken: UUID): String {
        val member = memberRepository.findByRefreshToken(refreshToken)
            ?: throw ServiceException("401-1", "유효하지 않은 RefreshToken 입니다.")

        val expiresAt = member.refreshTokenExpiresAt
        if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now())) {
            throw ServiceException("401-2", "RefreshToken이 만료되었습니다.")
        }

        return genAccessToken(member)
    }

    fun payload(accessToken: String): Map<String, Any?>? = authTokenService.payload(accessToken)

    fun findById(id: Long): Optional<Member> = memberRepository.findById(id)

    // 외부(URL/JWT)에서 넘어오는 공개 식별자(UUID)로 조회 — 여기서 내부 PK(Long)를 가진
    // 엔티티로 변환한 뒤, 그 아래 계층은 전부 Long PK로 처리한다.
    fun findByUuid(uuid: UUID): Optional<Member> = Optional.ofNullable(memberRepository.findByUuid(uuid))

    fun findByIdentifier(identifier: String): Optional<Member> {
        return try {
            val uuid = UUID.fromString(identifier)
            findByUuid(uuid)
        } catch (e: IllegalArgumentException) {
            findByEmail(identifier)
        }
    }

    @Transactional
    fun clearRefreshToken(member: Member) {
        val findMember = memberRepository.findById(member.id!!)
            .orElseThrow { ServiceException("404-1", "존재하지 않는 회원입니다.") }
        findMember.updateRefreshToken(null)
    }

    fun findAll(isSuspended: Boolean?, pageable: Pageable): Page<Member> {
        if (isSuspended == null) {
            return memberRepository.findAll(pageable)
        }
        return memberRepository.findAllByIsSuspended(isSuspended, pageable)
    }

    @Transactional
    fun updateIndustry(member: Member, industry: Industry) {
        val findMember = memberRepository.findById(member.id!!)
            .orElseThrow { ServiceException("404-1", "존재하지 않는 회원입니다.") }
        findMember.updateIndustry(industry)
    }

    @Transactional
    fun delete(member: Member) {
        val findMember = memberRepository.findById(member.id!!)
            .orElseThrow { ServiceException("404-1", "존재하지 않는 회원입니다.") }

        if (matchRequestService.hasPendingRequest(findMember)) {
            throw ServiceException("409-2", "진행 중인 매칭 요청이 있어 탈퇴할 수 없습니다. 매칭을 취소한 뒤 다시 시도해주세요.")
        }
        if (chatRoomParticipantService.findActiveChatRoomByMember(findMember) != null) {
            throw ServiceException("409-3", "진행 중인 채팅방이 있어 탈퇴할 수 없습니다. 채팅을 종료한 뒤 다시 시도해주세요.")
        }

        // 여기까지 왔으면 남은 건 전부 종료된 이력뿐.
        // FK 참조 순서(메시지 -> 참여자 -> 매칭요청)대로 정리한 뒤 회원을 삭제한다.
        chatMessageService.deleteAllByMember(findMember)
        chatRoomParticipantService.deleteAllByMember(findMember)
        matchRequestService.deleteAllByMember(findMember)

        memberRepository.delete(findMember)
    }

    fun getMatchHistory(member: Member): List<MatchHistoryDto> = matchRequestService.findMatchHistoryByMember(member)

    @Transactional
    fun toggleMemberSuspension(memberId: UUID, actor: Member): MemberAdmDto {
        val targetMember = memberRepository.findByUuid(memberId)
            ?: throw ServiceException("404-1", "존재하지 않는 회원입니다.")

        if (targetMember.id == actor.id) {
            throw ServiceException("400-1", "자기 자신은 제재할 수 없습니다.")
        }

        // 상태 토글 실행 (true <-> false)
        targetMember.toggleSuspended()

        return MemberAdmDto(targetMember)
    }
}
