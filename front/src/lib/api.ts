import isEmail from "validator/lib/isEmail";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";

const BASE = "";

// 백엔드가 직접 처리하는 OAuth2 인가 엔드포인트(풀 리다이렉트용) — /api 프록시 대상이 아니라 백엔드 origin이 그대로 필요함
export const OAUTH_SERVER_BASE = (
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"
).replace(/\/$/, "");

// 이메일 형식 검증 — 직접 만든 정규식은 허용 문자를 빠뜨리기 쉬워서(예: [^\s@]류 부정 클래스는
// 한글도 통과시켜버림) validator 라이브러리의 검증 로직을 그대로 사용함
// isEmail은 문자열이 아니면 예외를 던지므로, 타입상 string이어도 런타임 null/undefined를 방어함
export const isValidEmail = (value: string) =>
  typeof value === "string" && !!value && isEmail(value);

/* ── Session / admin storage ────────────────────────────────────── */
// 액세스·리프레시 토큰은 HttpOnly 쿠키로만 관리한다(JS가 값을 읽을 수 없음).
// 여기 남은 localStorage 플래그들은 전부 "로그인/관리자 상태를 UI에 표시하기 위한
// 힌트"일 뿐, 실제 인증에는 전혀 쓰이지 않는다 — 지워져도 다음 API 호출이 401을
// 반환하면서 다시 로그인 페이지로 보내질 뿐이다.
export const clearTokens = () => {
  localStorage.removeItem("isAdmin");
  localStorage.removeItem("hasSession");
};

export const setAdmin = () => localStorage.setItem("isAdmin", "1");
export const isAdmin = () =>
  typeof window !== "undefined" && localStorage.getItem("isAdmin") === "1";

// 로그인/회원가입 성공(= 쿠키가 심어짐) 후 이 플래그를 세워 로그인 상태를 표시한다.
export const markSession = () => localStorage.setItem("hasSession", "1");
export const isLoggedIn = () =>
  typeof window !== "undefined" && localStorage.getItem("hasSession") === "1";

// 정지된 계정으로 로그인 시 /me 페이지에 정지 안내를 띄우기 위한 1회성 플래그 키
export const SUSPENDED_STORAGE_KEY = "tangbisil_suspended";


/* ── Industry mapping ────────────────────────────────────────────── */
// 백엔드 Industry enum @JsonValue 가 한글 라벨을 그대로 직렬화하므로 표시명 = API 값
export const INDUSTRY_CODES: Record<string, string> = {
  "IT/개발": "IT/개발",
  서비스업: "서비스업",
  금융업: "금융업",
  의료서비스: "의료서비스",
  유통: "유통",
  "미디어/디자인": "미디어/디자인",
  사무업: "사무업",
};

export const INDUSTRY_NAMES: Record<string, string> = Object.fromEntries(
  Object.entries(INDUSTRY_CODES).map(([k, v]) => [v, k]),
);

/* ── Base fetch ─────────────────────────────────────────────────── */
// 401 재시도 루프/인증 흐름 자체의 401(로그인 실패 등)에 재발급을 걸지 않기 위한 제외 목록
const NO_REFRESH_RETRY_PATHS = [
  "/api/v1/members/login",
  "/api/v1/members/signup",
  "/api/v1/members/refresh",
];

// 동시에 여러 요청이 401을 맞아도 재발급 호출은 한 번만 나가도록 공유하는 in-flight promise
let refreshPromise: Promise<boolean> | null = null;

// 재발급된 accessToken은 서버가 쿠키로만 내려주므로(바디에 없음), 여기서는
// 재발급 성공 여부만 반환한다 — 성공하면 브라우저가 새 쿠키를 이미 들고 있어
// 원요청을 credentials:"include"로 그냥 재시도하면 된다.
function refreshAccessToken(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = fetch(`${BASE}/api/v1/members/refresh`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
    })
      .then((res) => res.ok)
      .catch(() => false)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

async function req<T>(
  path: string,
  options?: RequestInit,
  _isRetry = false,
): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    ...options,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(options?.headers ?? {}),
    },
  });

  // accessToken 만료로 401을 받으면, 로그인 흐름 자체의 요청이 아닌 한
  // refreshToken 쿠키로 한 번만 자동 재발급받아 원요청을 재시도한다.
  // 재시도한 요청마저 401이면(리프레시 토큰/쿠키 만료 등) 세션이 완전히 끝난 것이므로
  // 여기서도 반드시 로컬 세션 정보를 정리하고 로그인 페이지로 보내야 한다.
  if (res.status === 401 && !NO_REFRESH_RETRY_PATHS.includes(path)) {
    if (!_isRetry) {
      const refreshed = await refreshAccessToken();
      if (refreshed) {
        return req<T>(path, options, true);
      }
    }
    clearTokens();
    if (typeof window !== "undefined") {
      window.location.href = "/login";
    }
  }

  if (res.status === 204) return null as T;
  const text = await res.text();
  const body = text ? safeJsonParse(text) : null;
  if (!res.ok)
    throw Object.assign(new Error(body?.msg ?? text ?? res.statusText), {
      status: res.status,
    });
  return body.data as T;
}

function safeJsonParse(text: string) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

/* ── Auth ───────────────────────────────────────────────────────── */
// 토큰은 응답 바디가 아니라 HttpOnly 쿠키로만 내려온다 — 로그인 성공 여부와
// 관리자 라우팅 판단에 필요한 최소 정보(/me와 동일한 모양)만 돌려받는다.
export const apiLogin = (email: string, password: string) =>
  req<{
    email: string;
    industry: string;
    role: string;
  }>("/api/v1/members/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });

export const apiSignup = (
  email: string,
  password: string,
  industry: string,
  agreedToTerms: boolean,
) =>
  req<{ id: string; email: string; industry: string }>(
    "/api/v1/members/signup",
    { method: "POST", body: JSON.stringify({ email, password, industry, agreedToTerms }) },
  );

export const apiLogout = () =>
  req<null>("/api/v1/members/logout", { method: "POST" });

// 이메일 인증 코드 발송 (회원가입 전) — 이미 가입된 이메일이면 409, 60초 내 재요청이면 429
export const apiSendEmailVerification = (email: string) =>
  req<null>("/api/v1/members/email-verification/send", {
    method: "POST",
    body: JSON.stringify({ email }),
  });

// 이메일 인증 코드 확인
export const apiConfirmEmailVerification = (email: string, code: string) =>
  req<null>("/api/v1/members/email-verification/confirm", {
    method: "POST",
    body: JSON.stringify({ email, code }),
  });

// 비밀번호 재설정 코드 발송 — 가입되지 않은 이메일이어도 항상 200으로 응답(이메일 존재 여부 노출 방지)
export const apiSendPasswordReset = (email: string) =>
  req<null>("/api/v1/members/password-reset/send", {
    method: "POST",
    body: JSON.stringify({ email }),
  });

// 비밀번호 재설정 코드 확인 + 새 비밀번호 적용
export const apiConfirmPasswordReset = (
  email: string,
  code: string,
  newPassword: string,
) =>
  req<null>("/api/v1/members/password-reset/confirm", {
    method: "POST",
    body: JSON.stringify({ email, code, newPassword }),
  });

export const apiRefreshToken = () =>
  req<null>("/api/v1/members/refresh", { method: "POST" });

export const apiGetMe = () =>
  req<{ email: string; industry: string | null; role: string }>(
    "/api/v1/members/me",
  );

export const apiUpdateMe = (industry: string) =>
  req<{ industry: string }>("/api/v1/members/me", {
    method: "PATCH",
    body: JSON.stringify({ industry }),
  });

export const apiDeleteMe = () =>
  req<null>("/api/v1/members/me", { method: "DELETE" });

export type MatchHistoryDto = {
  matchedAt: string;
  industry: string;
  situation: string;
  status: 'ACTIVE' | 'CLOSED';
  isBot: boolean;
};

export const apiGetMatchHistory = () =>
  req<MatchHistoryDto[]>("/api/v1/members/me/matches");

/* ── Match ──────────────────────────────────────────────────────── */
export type MatchResponseDto = {
  matchRequestId: string;
  status: 'PENDING' | 'MATCHED';
  requestedAt: string;
  chatRoomId?: string;
};

export const apiCreateMatch = (situation: string) =>
  req<MatchResponseDto>("/api/v1/matches", { method: "POST", body: JSON.stringify({ situation }) });

export const apiGetMatch = (matchRequestId: string) =>
  req<MatchResponseDto>(`/api/v1/matches/${matchRequestId}`);

export const apiCancelMatch = (matchRequestId: string) =>
  req<null>(`/api/v1/matches/${matchRequestId}`, { method: "DELETE" });

export type HomeStats = {
  totalActiveUsers: number;
  situationStats: Array<{ situation: string; count: number }>;
};

// 홈 화면 실시간 통계(총 이용자 수 + 상황별 대화 인원) — 비로그인 사용자도 호출 가능
export const apiGetHomeStats = () =>
  req<HomeStats>("/api/v1/matches/stats/home");

export type TrendKeyword = {
  rank: number;
  label: string;
  trend: "up" | "down" | "flat";
};

// 어제(KST) 기준 집계된 실시간 HOT 키워드 최대 10개 — 비로그인 사용자도 호출 가능, 데이터 없으면 빈 배열
export const apiGetTrendKeywords = () =>
  req<TrendKeyword[]>("/api/v1/trend-keywords");

/* ── Chat ───────────────────────────────────────────────────────── */
export type ChatRoom = {
  roomId: string;
  status: "ACTIVE" | "CLOSED";
  maxParticipants: number;
  createdAt: string;
  closedAt?: string;
  isBot: boolean;
  opponentSituation?: string;
};

export const apiGetRoom = (roomId: string) =>
  req<ChatRoom>(`/api/v1/rooms/${roomId}`);

// 로그인한 회원이 현재 참여 중인 활성 채팅방 조회. 없으면 data: null
export const apiGetActiveRoom = () =>
  req<ChatRoom | null>("/api/v1/rooms/active");

// 채팅방 종료 (status -> CLOSED)
export const apiCloseRoom = (roomId: string) =>
  req<ChatRoom>(`/api/v1/rooms/${roomId}`, { method: "PATCH" });

export const apiSendMessage = (roomId: string, content: string) =>
  req<ChatMsg>(`/api/v1/rooms/${roomId}/messages`, {
    method: "POST",
    body: JSON.stringify({ content }),
  });

export type ChatMsg = {
  messageId: string;
  roomId: string;
  senderNickname: string;
  senderParticipantId?: string;
  content: string;
  createdAt: string;
  isMine: boolean;
};

// after: LocalDateTime ISO 문자열(마지막 수신 메시지의 createdAt). 없으면 전체 조회.
// 백엔드가 종료된 방에 대해 HTTP 200 + resultCode "200-3"을 반환하므로 closed 플래그로 구분.
export async function apiGetMessages(
  roomId: string,
  after?: string,
  _isRetry = false,
): Promise<{ msgs: ChatMsg[] | null; closed: boolean }> {
  const url = `/api/v1/rooms/${roomId}/messages${after ? `?after=${encodeURIComponent(after)}` : ""}`;
  const res = await fetch(url, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
    },
  });
  if (res.status === 204) return { msgs: null, closed: false };

  // accessToken 만료 시 한 번 재발급받고 재시도
  if ((res.status === 401 || res.status === 403) && !_isRetry) {
    const refreshed = await refreshAccessToken();
    if (refreshed) return apiGetMessages(roomId, after, true);
  }

  const body = await res.json();
  if (!res.ok)
    throw Object.assign(new Error(body?.msg ?? res.statusText), {
      status: res.status,
    });
  if (body.resultCode === "200-3") return { msgs: null, closed: true };
  return { msgs: body.data as ChatMsg[] | null, closed: false };
}

/* ── Notifications ──────────────────────────────────────────────── */
export type MatchNotification = {
  type: string;
  roomId: string;
  message: string;
  createdAt: string;
};

// 매칭 성사 등 실시간 알림 폴링. after 생략 시 TTL(3일) 내 전체, 지정 시 그 이후만 조회.
// 신규 알림이 없으면 백엔드가 resultCode "200-2"로 data: null을 내려준다.
export const apiGetNotifications = (after?: string) =>
  req<MatchNotification[] | null>(
    `/api/v1/notifications${after ? `?after=${encodeURIComponent(after)}` : ""}`,
  );

export function subscribeRoom(
    roomId: string,
    onMessage: (msg: ChatMsg) => void,
    onReconnect?: () => void,
    onError?: (errorMsg: string) => void,
    onRoomClosed?: () => void,
): Client {
  let isFirstConnect = true;
  let refreshFailCount = 0;
  const MAX_REFRESH_FAILURES = 3;

  // Authorization 헤더 없이 쿠키로 인증한다. 연결/재연결 시도마다 먼저 accessToken을 갱신한다.
  const client = new Client({
    webSocketFactory: () => new SockJS(`${OAUTH_SERVER_BASE}/ws`),
    reconnectDelay: 3000,
    beforeConnect: async () => {
      const refreshed = await refreshAccessToken();
      if (refreshed) {
        refreshFailCount = 0;
        return;
      }
      // 세션이 끊긴 상태 - 재발급이 계속 실패하는데 3초마다 재시도하면 API만 계속 두드리게 된다.
      refreshFailCount++;
      if (refreshFailCount >= MAX_REFRESH_FAILURES) {
        client.deactivate();
        clearTokens();
        if (typeof window !== "undefined") window.location.href = "/login";
      }
    },
    onConnect: () => {
      if(!isFirstConnect) {
        onReconnect?.();
      }
      isFirstConnect = false;

      client.subscribe(`/user/queue/rooms/${roomId}`, (frame) => {
        const raw = JSON.parse(frame.body);
        // 상대방의 채팅방 종료 알림은 일반 채팅 메시지와 같은 큐로 오지만 messageId가 없다.
        if (!raw.messageId) {
          onRoomClosed?.();
          return;
        }
        onMessage(raw);
      });
      client.subscribe('/user/queue/errors', (frame) => {
        const error = JSON.parse(frame.body);
        onError?.(error.code + ' : ' + error.message);
      });
    },
    onStompError: (frame) => {
      console.error('[STOMP] error frame:', frame.headers, frame.body);
      onError?.(frame.headers['message'] ?? '전송 오류가 발생했습니다.');
    },
    onWebSocketError: (event) => {
      console.error('[STOMP] websocket error:', event);
    },
    onWebSocketClose: (event) => {
      console.error('[STOMP] websocket closed:', event.code, event.reason);
    },
  });
  client.activate();
  return client;
}

/* ── Admin ──────────────────────────────────────────────────────── */
export type AdminMember = {
  memberId: string;
  email: string;
  industry: string;
  isSuspended: boolean;
  createdAt: string;
  role?: string;
};

export const apiGetDashboard = () =>
  req<{
    matchStatistics: {
      totalMembers: number;
      todayMatches: number;
      activeChatRooms: number;
      pendingMatches: number;
    };
    industryStatistics: Array<{ industry: string; count: number }>;
    recentMatchLogs: Array<{ matchedAt: string; industry: string; situation: string }>;
  }>("/api/v1/admin/dashboard");

// startDate/endDate는 "YYYY-MM-DD" 형식, 둘 다 포함(inclusive) 범위.
// 해당 기간에 가입자가 없는 산업군은 industryStatistics에서 아예 빠진다(0으로 채워지지 않음).
export const apiGetIndustrySignupStats = (startDate: string, endDate: string) =>
  req<{
    startDate: string;
    endDate: string;
    industryStatistics: Array<{ industry: string; count: number }>;
  }>(`/api/v1/admin/dashboard/industry-signups?startDate=${startDate}&endDate=${endDate}`);

export const apiGetAdminMembers = (page = 0, size = 10, isSuspended?: boolean) =>
  req<{
    content: AdminMember[];
    totalPages: number;
    totalElements: number;
    pageable: { pageNumber: number; pageSize: number };
  }>(
    `/api/v1/admin/members?page=${page}&size=${size}${isSuspended !== undefined ? `&isSuspended=${isSuspended}` : ""}`,
  );

export const apiGetAdminMember = (identifier: string) =>
  req<AdminMember>(`/api/v1/admin/members/${identifier}`);

export const apiSuspendMember = (memberId: string) =>
  req<AdminMember>(`/api/v1/admin/members/${memberId}/suspend`, { method: 'PATCH' });

/* ── Reports ────────────────────────────────────────────────────── */
export type ReportResult = {
  reportId: string;
  status: 'PENDING' | 'PROCESSED';
  createdAt: string;
};

export const apiSubmitReport = (roomId: string, reportedMessageId: string, reason: string) =>
  req<ReportResult>('/api/v1/reports', {
    method: 'POST',
    body: JSON.stringify({ roomId, reportedMessageId, reason }),
  });

export type AdminReport = {
  reportId: string;
  reporterEmail: string;
  reportedEmail: string;
  reason: string;
  status: 'PENDING' | 'PROCESSED';
  createdAt: string;
};

export type AdminReportDetail = {
  reportId: string;
  reporterEmail: string;
  reportedEmail: string;
  status: 'PENDING' | 'PROCESSED';
  reportedMessages: Array<{
    senderNickname: string;
    senderLabel: string;
    content: string;
    sentAt: string;
    isTarget: boolean;
  }>;
};

export const apiGetAdminReports = (page = 0, size = 10, status?: 'PENDING' | 'PROCESSED') =>
  req<{
    content: AdminReport[];
    totalPages: number;
    totalElements: number;
    pageable: { pageNumber: number; pageSize: number };
  }>(`/api/v1/admin/reports?page=${page}&size=${size}${status ? `&status=${status}` : ''}`);

export const apiGetAdminReport = (reportId: string) =>
  req<AdminReportDetail>(`/api/v1/admin/reports/${reportId}`);

export const apiToggleReportStatus = (reportId: string) =>
  req<{ reportId: string; status: 'PENDING' | 'PROCESSED' }>(`/api/v1/admin/reports/${reportId}/status`, { method: 'PATCH' });
