package com.back.domain.member.member.service;

import com.back.domain.chat.chatRoomMessage.service.ChatMessageService;
import com.back.domain.chat.chatRoomParticipant.service.ChatRoomParticipantService;
import com.back.domain.match.matchRequest.dto.MatchHistoryDto;
import com.back.domain.match.matchRequest.service.MatchRequestService;
import com.back.domain.member.member.dto.MemberAdmDto;
import com.back.domain.member.member.entity.AuthProvider;
import com.back.domain.member.member.entity.Industry;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.repository.MemberRepository;
import com.back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
    private final AuthTokenService authTokenService;
    private final PasswordEncoder passwordEncoder;
    private final MemberRepository memberRepository;
    private final MatchRequestService matchRequestService;
    private final ChatRoomParticipantService chatRoomParticipantService;
    private final ChatMessageService chatMessageService;

    public long count() {
        return memberRepository.count();
    }

    @Transactional
    public Member join(String email, String password, Industry industry, String role) {
        findByEmail(email).ifPresent(_ -> {
            throw new ServiceException("409-1", "이미 존재하는 이메일입니다.");
        });

        String encodedPassword = passwordEncoder.encode(password);
        Member member = new Member(email, encodedPassword, industry, role);

        return memberRepository.save(member);
    }

    @Transactional
    public Member findOrCreateOAuthMember(String email, AuthProvider provider, String providerId) {
        Optional<Member> existingMember = memberRepository.findByProviderAndProviderId(provider, providerId);

        if (existingMember.isPresent()) {
            return existingMember.get();
        }

        try {
            Member newMember = Member.ofOAuth(email, provider, providerId);
            return memberRepository.save(newMember);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 (provider, providerId)로 로그인 시도가 들어와 다른 요청이 먼저 저장한 경우
            return memberRepository.findByProviderAndProviderId(provider, providerId)
                    .orElseThrow(() -> e);
        }
    }

    public void checkPassword(Member member, String password) {
        if (!passwordEncoder.matches(password, member.getPassword()))
            throw new ServiceException("401-1", "비밀번호가 일치하지 않습니다.");
    }

    public Optional<Member> findByEmail(String email) {
        return memberRepository.findByEmail(email);
    }

    public String genAccessToken(Member member) {
        return authTokenService.genAccessToken(member);
    }

    @Transactional
    public UUID genRefreshToken(Member member) {
        UUID token = UUID.randomUUID();
        member.updateRefreshToken(token);
        return token;
    }
    public String refreshAccessToken(UUID refreshToken) {
        Member member = memberRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() ->
                        new ServiceException("401-1", "유효하지 않은 RefreshToken 입니다."));

        if (member.getRefreshTokenExpiresAt() == null ||
                member.getRefreshTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ServiceException("401-2", "RefreshToken이 만료되었습니다.");
        }

        return genAccessToken(member);
    }

    public Map<String, Object> payload(String accessToken) {
        return authTokenService.payload(accessToken);
    }

    public Optional<Member> findById(UUID id) {
        return memberRepository.findById(id);
    }

    public Optional<Member> findByIdentifier(String identifier) {
        try {
            UUID id = UUID.fromString(identifier);
            return findById(id);
        } catch (IllegalArgumentException e) {
            return findByEmail(identifier);
        }
    }

    @Transactional
    public void clearRefreshToken(Member member) {
        Member findMember = memberRepository.findById(member.getId())
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 회원입니다."));
        findMember.updateRefreshToken(null);
    }
    public Page<Member> findAll(Pageable pageable) {
        return memberRepository.findAll(pageable);
    }

    @Transactional
    public void updateIndustry(Member member, Industry industry) {
        Member findMember = memberRepository.findById(member.getId())
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 회원입니다."));
        findMember.updateIndustry(industry);
    }
    @Transactional
    public void delete(Member member) {
        Member findMember = memberRepository.findById(member.getId())
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 회원입니다."));

        if (matchRequestService.hasPendingRequest(findMember)) {
            throw new ServiceException("409-2", "진행 중인 매칭 요청이 있어 탈퇴할 수 없습니다. 매칭을 취소한 뒤 다시 시도해주세요.");
        }
        if (chatRoomParticipantService.findActiveChatRoomByMember(findMember).isPresent()) {
            throw new ServiceException("409-3", "진행 중인 채팅방이 있어 탈퇴할 수 없습니다. 채팅을 종료한 뒤 다시 시도해주세요.");
        }

        // 여기까지 왔으면 남은 건 전부 종료된 이력뿐.
        // FK 참조 순서(메시지 -> 참여자 -> 매칭요청)대로 정리한 뒤 회원을 삭제한다.
        chatMessageService.deleteAllByMember(findMember);
        chatRoomParticipantService.deleteAllByMember(findMember);
        matchRequestService.deleteAllByMember(findMember);

        memberRepository.delete(findMember);
    }

    public List<MatchHistoryDto> getMatchHistory(Member member) {
        return matchRequestService.findMatchHistoryByMember(member);
    }

    @Transactional
    public MemberAdmDto toggleMemberSuspension(UUID memberId, Member actor) {

        Member targetMember = memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 회원입니다."));

        if (targetMember.getId().equals(actor.getId())) {
            throw new ServiceException("400-1", "자기 자신은 제재할 수 없습니다.");
        }

        // 상태 토글 실행 (true <-> false)
        targetMember.toggleSuspended();

        return new MemberAdmDto(targetMember);
    }

}