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

/* ── Token / admin storage ──────────────────────────────────────── */
export const getToken = (): string | null =>
  typeof window !== "undefined" ? localStorage.getItem("accessToken") : null;

export const setTokens = (accessToken: string, refreshToken: string) => {
  localStorage.setItem("accessToken", accessToken);
  localStorage.setItem("refreshToken", refreshToken);
};

export const clearTokens = () => {
  localStorage.removeItem("accessToken");
  localStorage.removeItem("refreshToken");
  localStorage.removeItem("isAdmin");
  localStorage.removeItem("hasSession");
};

export const setAdmin = () => localStorage.setItem("isAdmin", "1");
export const isAdmin = () =>
  typeof window !== "undefined" && localStorage.getItem("isAdmin") === "1";

// OAuth 로그인은 accessToken을 localStorage에 저장하지 않고 쿠키로만 인증하므로,
// getToken()만으로는 로그인 여부를 판단할 수 없다. OAuth 콜백에서 /me 조회에 성공하면
// 이 플래그를 세워 로그인 상태를 표시한다.
export const markSession = () => localStorage.setItem("hasSession", "1");
export const isLoggedIn = () =>
  typeof window !== "undefined" &&
  (!!getToken() || localStorage.getItem("hasSession") === "1");

// 정지된 계정으로 로그인 시 /me 페이지에 정지 안내를 띄우기 위한 1회성 플래그 키
export const SUSPENDED_STORAGE_KEY = "tangbisil_suspended";

// JWT의 role 클레임을 읽어 관리자 여부를 판별 (백엔드는 별도의 /me role 필드를 내려주지 않음)
export const getRoleFromToken = (token: string): string | null => {
  try {
    const base64 = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const json = decodeURIComponent(
      atob(base64)
        .split("")
        .map((c) => "%" + c.charCodeAt(0).toString(16).padStart(2, "0"))
        .join(""),
    );
    return (JSON.parse(json).role as string) ?? null;
  } catch {
    return null;
  }
};


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
let refreshPromise: Promise<string | null> | null = null;

function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = fetch(`${BASE}/api/v1/members/refresh`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
    })
      .then(async (res) => {
        if (!res.ok) return null;
        const text = await res.text();
        const body = text ? safeJsonParse(text) : null;
        const token = body?.data?.accessToken as string | undefined;
        if (!token) return null;
        localStorage.setItem("accessToken", token);
        return token;
      })
      .catch(() => null)
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
  const token = getToken();
  const res = await fetch(`${BASE}${path}`, {
    ...options,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options?.headers ?? {}),
    },
  });

  // accessToken 만료로 401을 받으면, 로그인 흐름 자체의 요청이 아닌 한
  // refreshToken으로 한 번만 자동 재발급받아 원요청을 재시도한다.
  // OAuth 로그인 사용자는 localStorage에 토큰이 없고 쿠키만으로 인증되므로
  // token 존재 여부와 무관하게 재발급을 시도해야 한다.
  // 재시도한 요청마저 401이면(리프레시 토큰/쿠키 만료 등) 세션이 완전히 끝난 것이므로
  // 여기서도 반드시 로컬 세션 정보를 정리하고 로그인 페이지로 보내야 한다.
  if (res.status === 401 && !NO_REFRESH_RETRY_PATHS.includes(path)) {
    if (!_isRetry) {
      const newToken = await refreshAccessToken();
      if (newToken) {
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
export const apiLogin = (email: string, password: string) =>
  req<{
    grantType: string;
    accessToken: string;
    refreshToken: string;
    accessTokenExpiresIn: number;
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
  req<{ grantType: string; accessToken: string; refreshToken: string; accessTokenExpiresIn: number }>(
    "/api/v1/members/refresh",
    { method: "POST" },
  );

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

// after: LocalDateTime ISO 문자열 (마지막 수신 메시지의 createdAt). 없으면 전체 조회.
// 백엔드가 종료된 방에 대해 HTTP 200 + resultCode "200-3" 을 반환하므로 closed 플래그로 구분.
export async function apiGetMessages(
  roomId: string,
  after?: string,
): Promise<{ msgs: ChatMsg[] | null; closed: boolean }> {
  const token = getToken();
  const url = `/api/v1/rooms/${roomId}/messages${after ? `?after=${encodeURIComponent(after)}` : ""}`;
  const res = await fetch(url, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });
  if (res.status === 204) return { msgs: null, closed: false };
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
): Client {
  const token = getToken();
  let isFirstConnect = true;

  const client = new Client({
    webSocketFactory: () => new SockJS(`${OAUTH_SERVER_BASE}/ws`),
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
    reconnectDelay: 3000,
    onConnect: () => {
      if(!isFirstConnect) {
        onReconnect?.();
      }
      isFirstConnect = false;

      client.subscribe(`/user/queue/rooms/${roomId}`, (frame) => {
        const raw = JSON.parse(frame.body);
        onMessage(raw);
      });
      client.subscribe('/user/queue/errors', (frame) => {
        const error = JSON.parse(frame.body);
        onError?.(error.code + ' : ' + error.message);
      });
    },
    onStompError: (frame) => {
      onError?.(frame.headers['message'] ?? '전송 오류가 발생했습니다.');
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
