# 탕비실 (Tangbisil)

> 직장인이 업무 중 쌓인 스트레스를 즉시 털어놓을 수 있도록, **같은 산업군·같은 상황의 익명 동료와 실시간으로 연결**해주는 매칭형 커뮤니티 서비스

사무실 탕비실에서 동료와 잠깐 수다 떨고 오는 경험을 디지털로 옮겼습니다. 상황 하나만 고르면 바로 연결되고(즉시성), 상대에게는 끝까지 '익명의 동료'로만 보이며(익명성), 대화는 10분 뒤 사라집니다(휘발성).

**🔗 배포 링크**: https://tangbisil-production.up.railway.app

> 프로그래머스 백엔드 데브코스 10기 12회차 2차 프로젝트 — 8팀 (2026.06.22 ~ 07.10, 3주)

---

## 핵심 기능

| 기능 | 설명 |
| --- | --- |
| 🤝 실시간 익명 매칭 | 산업군 + 상황 기반 3단계 우선순위 매칭 (완전 일치 → 유사 상황 그룹 → 산업군 일치). 대기 35초 초과 시 AI 봇 자동 매칭, 5분 경과 잔류 요청은 안전망 스케줄러가 정리 |
| 💬 휘발성 익명 채팅 | 1:1 채팅, 상대는 항상 "익명의 동료"로 표시. 10분 후 자동 종료, 메시지는 종료 24시간 뒤 완전 삭제. after 파라미터 증분 폴링으로 부하 최소화 |
| 🔒 동시성 제어 | `UPDATE ... WHERE status = 'PENDING'` 원자적 조건부 UPDATE(레코드 락 기반)로 중복 매칭 방지 — 30명 동시 요청 테스트 중복 0건 |
| 🤖 AI 봇 자동 응답 | Groq API(llama 3.3 70B) 기반. 최근 대화 12개를 맥락으로 전달, 산업군별 페르소나 프롬프트, 1.5~4.5초 랜덤 지연 연출, 실패 시 캔드 메시지 폴백 |
| 🛡️ 신고 및 제재 | 신고 시 이전 대화 30개 비동기 자동 백업(원본 삭제 대비 FK 미사용 스냅샷), 관리자 확인 후 계정 정지 — 인증 필터에서 실시간 차단 |
| 📊 관리자 대시보드 | 가입자·매칭·활성 채팅방 실시간 통계, 이메일 검색, 신고 처리 상태 관리 |
| 🔑 인증 | JWT(Access 30분 / Refresh 30일, 서버 저장·로그아웃 시 삭제), 카카오·구글 OAuth — 1회용 code 교환 방식으로 토큰 노출 방지 |

## 익명성 설계

- 수집 정보는 **이메일·비밀번호·산업군뿐** — 실명·회사명·연락처 없음
- 이메일 등 신원 정보는 MEMBER 테이블에만 존재, 매칭·채팅·신고 등 활동 테이블은 **UUID로만 연결**
- 모든 엔티티 PK를 UUID로 통일해 열거 공격 차단
- 매칭 이력에는 날짜·산업군·상황만 남고 대화 내용은 저장하지 않음

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1, Spring Security, Spring Data JPA, JWT(JJWT) |
| Frontend | React, Next.js 16 |
| Database | H2 (개발) / MySQL (배포) |
| AI | Groq API (OpenAI 호환 Chat Completions, llama 3.3 70B) |
| Infra | Docker, GitHub Actions, Railway |
| 모니터링·테스트 | Spring Actuator, Prometheus, Grafana, JMeter |

## 시스템 아키텍처

```
사용자 ──HTTPS──▶ Next.js (Frontend) ──REST API──▶ Spring Boot (Backend) ──▶ H2/MySQL
                                                        │
                                                        └──▶ Groq API (AI 봇 응답)

개발자 ──push──▶ GitHub ──▶ GitHub Actions (Docker 빌드) ──▶ Railway 자동 배포
```

## 성능 검증

같은 로컬 환경에서 develop(Redis 전)과 feat/back/144(Redis Cache-Aside 적용) 두 브랜치를 JMeter로 비교했습니다. (50명 동시 매칭 / 채팅방 1곳에 메시지 1,000건)

| 항목 | Redis 전 | Redis 후 | 개선 |
| --- | --- | --- | --- |
| 매칭 평균 응답시간 | 603 ms | 312 ms | 약 1.9배 |
| 매칭 처리량 | 37.0 건/초 | 91.9 건/초 | 약 2.5배 |
| 메시지 평균 응답시간 | 240 ms | 96 ms | 약 2.5배 |
| 메시지 처리량 | 91.9 건/초 | 132.5 건/초 | 약 1.4배 |

정합성: 양쪽 모두 50명 전원 매칭 · 중복 매칭 0건 · 에러율 0%. 이 외에 100명 동시 폴링 테스트에서 평균 응답 168ms(요구사항 1초 이내)를 확인했습니다.

> 관리자 기능(통계 대시보드, 회원 정지, 신고 처리)은 시연 영상을 참고해주세요.

## 실행 방법

### Backend

```bash
cd back
./gradlew bootRun   # 기본 dev 프로필, H2 파일 DB 자동 생성 (http://localhost:8080)
```

Swagger: `http://localhost:8080/swagger-ui/index.html`

AI 봇을 사용하려면 환경 변수 설정: `GROQ_API_KEY`, 소셜 로그인은 `GOOGLE_CLIENT_ID/SECRET`, `KAKAO_CLIENT_ID/SECRET`

### Frontend

```bash
cd front
npm install
npm run dev   # http://localhost:3000
```

### 모니터링 (선택)

```bash
cd monitoring
docker compose up -d
# Prometheus: http://localhost:9090 / Grafana: http://localhost:3001 (admin/admin)
```

### 부하 테스트 (선택)

`jmeter/` 폴더의 시나리오 3종: 매칭 동시성(30명) · 폴링 부하(100명×30초) · 전체 플로우(50명)

## 프로젝트 구조

```
back/src/main/java/com/back
├── domain
│   ├── member     # 회원·인증 (JWT, OAuth)
│   ├── match      # 매칭 엔진 (3단계 우선순위, 동시성 제어, 스케줄러)
│   ├── chat       # 채팅방·메시지·참여자 (자동 종료/휘발 스케줄러)
│   ├── bot        # AI 봇 자동 응답 (Groq)
│   ├── report     # 신고·증거 백업 (이벤트 기반 비동기)
│   └── dashboard  # 관리자 통계
└── global         # Security, 공통 응답, 예외 처리, 초기 데이터
front/             # Next.js 프론트엔드
monitoring/        # Prometheus + Grafana docker-compose
jmeter/            # 부하 테스트 시나리오
```

## 팀

| 이름 | 담당 |
| --- | --- |
| 여준 | 회원 API, AI 봇, 프론트엔드 |
| 이수민 | 매칭 API, 소셜 로그인(OAuth), 배포 |
| 이준형 | 채팅방 API, 신고 API, Redis 캐시 구조 설계 |
| 최성혁 | 채팅 메시지 API, 유사도 기반 매칭, 부하 테스트 |

멘토: 이택민 멘토님

## 협업 방식

학습형 Git Flow(main/develop + 파트·이슈 번호 브랜치), GitHub Project 이슈 관리, Slack Webhook PR 알림, GitHub Actions Gemini 자동 코드리뷰, TDD 기반 개발
