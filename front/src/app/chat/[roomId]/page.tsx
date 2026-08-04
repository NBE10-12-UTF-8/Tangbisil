'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { useRouter, useParams } from 'next/navigation';
import {
  apiGetRoom, apiCloseRoom, apiGetMessages, apiGetMe, apiSubmitReport,
  apiGetHomeStats, isLoggedIn, INDUSTRY_NAMES, subscribeRoom, type ChatMsg,
} from '@/lib/api';
import { AppShell } from '@/components/AppShell';
import { TangbisilLogo } from '@/components/TangbisilLogo';

const SITUATION_KEY = 'tangbisil_situation';

function SearchIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" style={{ flexShrink: 0 }}>
      <circle cx="11" cy="11" r="7" stroke="#9aa0a6" strokeWidth="2" />
      <line x1="16.5" y1="16.5" x2="21" y2="21" stroke="#9aa0a6" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}

function MicIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" style={{ flexShrink: 0 }}>
      <rect x="9" y="3" width="6" height="11" rx="3" fill="#3b7ff2" />
      <path d="M5 11a7 7 0 0 0 14 0" stroke="#f5b400" strokeWidth="2" fill="none" strokeLinecap="round" />
      <line x1="12" y1="18" x2="12" y2="21" stroke="#34a06b" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}

function LockIcon() {
  return (
    <svg width="11" height="11" viewBox="0 0 24 24" fill="none">
      <rect x="5" y="11" width="14" height="9" rx="2" stroke="#3b7ff2" strokeWidth="2" />
      <path d="M8 11V8a4 4 0 0 1 8 0v3" stroke="#3b7ff2" strokeWidth="2" />
    </svg>
  );
}

function ClockIcon({ stroke }: { stroke: string }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" style={{ flexShrink: 0 }}>
      <circle cx="12" cy="12" r="9" stroke={stroke} strokeWidth="2" />
      <path d="M12 7v5l3.5 2" stroke={stroke} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

const REPORT_REASONS = [
  '욕설 / 혐오 발언',
  '스팸 / 광고',
  '성희롱 / 음란 발언',
  '개인정보 유출 시도',
  '기타',
];

export default function ChatPage() {
  const router = useRouter();
  const { roomId } = useParams<{ roomId: string }>();

  const [messages, setMessages]         = useState<ChatMsg[]>([]);
  const [input, setInput]               = useState('');
  const [chatTimeLeft, setChatTimeLeft] = useState(600);
  const [situation, setSituation]       = useState('');
  const [userIndustry, setUserIndustry] = useState('');
  const [partnerLeft, setPartnerLeft]   = useState(false);
  const [chatExpired, setChatExpired]   = useState(false);
  const [roomError, setRoomError]       = useState<'NOT_FOUND' | 'FORBIDDEN' | 'ALREADY_CLOSED' | null>(null);
  const [totalActiveUsers, setTotalActiveUsers] = useState(0);
  const [isBot, setIsBot]               = useState(false);
  const [opponentSituation, setOpponentSituation] = useState('');

  const [reportTarget, setReportTarget]           = useState<ChatMsg | null>(null);
  const [reportReason, setReportReason]           = useState('');
  const [reportSubmitting, setReportSubmitting]   = useState(false);
  const [reportDone, setReportDone]               = useState(false);

  const chatTimerRef        = useRef<ReturnType<typeof setInterval> | null>(null);
  const seenMsgIds          = useRef<Set<string>>(new Set());
  const lastMsgTimeRef      = useRef<string | null>(null);
  const inputRef            = useRef<HTMLInputElement>(null);
  const isLeavingRef        = useRef(false);
  const stompClientRef      = useRef<import('@stomp/stompjs').Client | null>(null);

  const chatClosed = chatExpired || partnerLeft;

  const stopTimers = useCallback(() => {
    if (chatTimerRef.current) { clearInterval(chatTimerRef.current); chatTimerRef.current = null; }
  }, []);

  const notifyPartnerLeft = useCallback(() => {
    stopTimers();
    setPartnerLeft(true);
  }, [stopTimers]);

  const endChat = useCallback(async () => {
    if (isLeavingRef.current) return;
    isLeavingRef.current = true;
    stopTimers();
    localStorage.removeItem(SITUATION_KEY);
    try { await apiCloseRoom(roomId); } catch { /* ignore */ }
    router.push('/');
  }, [roomId, router, stopTimers]);

  const send = useCallback(() => {
    if (chatClosed) return;
    const content = input.trim();
    if (!content) return;
    if (!stompClientRef.current?.connected) return;
    setInput('');
    stompClientRef.current.publish({
      destination: `/app/rooms/${roomId}/messages`,
      body: JSON.stringify({ content }),
    });
  }, [input, roomId, chatClosed]);

  useEffect(() => {
    if (!roomId) { router.push('/'); return; }

    if (!isLoggedIn()) { router.push('/login'); return; }

    setSituation(localStorage.getItem(SITUATION_KEY) ?? '');

    apiGetMe()
      .then(me => setUserIndustry(me.industry ? (INDUSTRY_NAMES[me.industry] ?? me.industry) : ''))
      .catch(() => {});

    apiGetMessages(roomId).then(({msgs: initial, closed}) => {
      if (closed) { notifyPartnerLeft(); return; }
      if (initial && initial.length > 0) {
        const fresh = initial.filter(m => !seenMsgIds.current.has(m.messageId));
        fresh.forEach(m => seenMsgIds.current.add(m.messageId));
        setMessages(prev => [...prev, ...fresh]);
        lastMsgTimeRef.current = initial[initial.length - 1].createdAt;
      }
    }).catch(() => {});

    // isMine은 서버가 계산해서 전달하므로 클라이언트 participantId 불필요
    const stompClient = subscribeRoom(roomId, (msg) => {
      if (!seenMsgIds.current.has(msg.messageId)) {
        seenMsgIds.current.add(msg.messageId);
        setMessages(prev => [...prev, msg]);
        lastMsgTimeRef.current = msg.createdAt;
      }
    }, (errorMsg) => {
      const code = errorMsg.split(' : ')[0];
      if (code === '409-1' || code === '403-1') notifyPartnerLeft();
    });
    stompClientRef.current = stompClient;

    apiGetRoom(roomId)
      .then(room => {
        setIsBot(room.isBot);
        setOpponentSituation(room.opponentSituation ?? '');
        if (room.status === 'CLOSED') { stopTimers(); setRoomError('ALREADY_CLOSED'); return; }
        const endTime = new Date(room.createdAt).getTime() + 10 * 60 * 1000;
        const remaining = Math.max(0, Math.floor((endTime - Date.now()) / 1000));
        if (remaining <= 0) { setChatExpired(true); stopTimers(); return; }
        setChatTimeLeft(remaining);
        chatTimerRef.current = setInterval(() => {
          const left = Math.max(0, Math.floor((endTime - Date.now()) / 1000));
          setChatTimeLeft(left);
          if (left <= 0) { setChatExpired(true); stopTimers(); }
        }, 1000);
      })
      .catch((err: unknown) => {
        const status = (err as { status?: number })?.status;
        if (status === 404) { stopTimers(); setRoomError('NOT_FOUND'); return; }
        if (status === 403) { stopTimers(); setRoomError('FORBIDDEN'); return; }
        // 방 조회 자체가 네트워크 문제 등으로 실패한 경우(상태를 알 수 없음)엔
        // 기존처럼 채팅을 계속 쓸 수 있다고 보고 클라이언트 타이머로 대체한다.
        setChatTimeLeft(600);
        chatTimerRef.current = setInterval(() => {
          setChatTimeLeft(prev => {
            const n = prev - 1;
            if (n <= 0) { setChatExpired(true); stopTimers(); return 0; }
            return n;
          });
        }, 1000);
      });

    return () => {
      stompClient.deactivate();
      stopTimers();
    };
  }, [roomId, notifyPartnerLeft]);

  useEffect(() => { inputRef.current?.focus(); }, []);

  useEffect(() => {
    apiGetHomeStats()
      .then(stats => setTotalActiveUsers(stats.totalActiveUsers))
      .catch((err) => {
        console.error('Failed to fetch home stats:', err);
      });
  }, []);

  const handleReport = async () => {
    if (!reportTarget || !reportReason) return;
    setReportSubmitting(true);
    try {
      await apiSubmitReport(roomId, reportTarget.messageId, reportReason);
      setReportDone(true);
    } catch { /* ignore */ } finally {
      setReportSubmitting(false);
    }
  };

  const closeReportModal = () => {
    setReportTarget(null);
    setReportReason('');
    setReportDone(false);
  };

  const fmtTimer = (s: number) =>
    `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;

  const partnerNickname = isBot ? 'AI' : (messages.find(m => !m.isMine)?.senderNickname ?? '익명의 상대');
  const displayMessages = [...messages].reverse();

  const s = {
    card: { width: '100%', maxWidth: 560, background: '#fff', borderRadius: 24, boxShadow: '0 1px 10px rgba(32,33,36,.18)', marginTop: 20, boxSizing: 'border-box' } as const,
    searchRow: { height: 46, display: 'flex', alignItems: 'center', gap: 13, padding: '0 16px' } as const,
    sep: { height: 1, background: '#e8eaed', margin: '0 14px' } as const,
    industryRow: { display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 12, padding: '6px 18px', borderBottom: '1px solid #e8eaed' } as const,
    hintRow: { display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 4, padding: '9px 18px' } as const,
    hintText: { fontSize: 11, color: '#bdc1c6' } as const,
  };

  if (roomError) {
    const info = {
      ALREADY_CLOSED: { icon: '👋', title: '종료된 채팅방입니다', desc: '이미 끝난 대화예요. 새로운 상황으로 다시 매칭해보세요.' },
      NOT_FOUND: { icon: '❓', title: '존재하지 않는 채팅방입니다', desc: '링크가 잘못됐거나 삭제된 채팅방일 수 있어요.' },
      FORBIDDEN: { icon: '🔒', title: '접근할 수 없는 채팅방입니다', desc: '이 채팅방에 참여했던 계정으로 로그인해주세요.' },
    }[roomError];
    return (
      <AppShell>
        <div style={{ marginBottom: 18 }}><TangbisilLogo size={52} /></div>
        <div style={{ ...s.card, padding: '40px 24px', textAlign: 'center' }}>
          <div style={{ fontSize: 32, marginBottom: 14 }}>{info.icon}</div>
          <div style={{ fontSize: 16, fontWeight: 700, color: '#202124', marginBottom: 6 }}>{info.title}</div>
          <div style={{ fontSize: 13, color: '#9aa0a6', marginBottom: 22 }}>{info.desc}</div>
          <button
            onClick={() => router.push('/')}
            style={{ padding: '10px 24px', background: '#3b7ff2', color: '#fff', border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: 'pointer' }}
          >
            홈으로 가기
          </button>
        </div>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <div style={{ marginBottom: 18 }}><TangbisilLogo size={52} /></div>
      <div style={s.card}>
        <div style={s.searchRow}>
          <SearchIcon />
          <input
            ref={inputRef}
            type="text"
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter' && !e.nativeEvent.isComposing) { e.preventDefault(); send(); } }}
            disabled={chatClosed}
            placeholder={chatClosed ? '대화가 종료되었습니다' : '메시지를 입력하고 Enter'}
            style={{ flex: 1, border: 'none', outline: 'none', fontSize: 16, color: chatClosed ? '#9aa0a6' : '#3c4043', background: 'transparent' }}
          />
          <div style={{ width: 1, height: 24, background: '#dfe1e5', flexShrink: 0 }} />
          <MicIcon />
        </div>

        <div style={s.sep} />

        <div style={s.industryRow}>
          <span style={{ fontSize: 11.5, color: '#9aa0a6', flexShrink: 0 }}>내 산업군</span>
          {userIndustry ? (
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, padding: '4px 11px', background: '#f3f8ff', color: '#3b7ff2', borderRadius: 14, fontSize: 12.5, fontWeight: 600, flexShrink: 0 }}>
              <LockIcon />{userIndustry}
            </span>
          ) : (
            <span style={{ fontSize: 11.5, color: '#bdc1c6' }}>로그인 후 확인</span>
          )}
          {situation && (
            <span style={{ display: 'inline-flex', alignItems: 'center', padding: '4px 11px', background: '#e8f0fe', border: '1px solid #3b7ff2', color: '#1a56c4', borderRadius: 14, fontSize: 12.5, fontWeight: 600, flexShrink: 0 }}>
              {situation}
            </span>
          )}
          <span style={{ marginLeft: 'auto', fontSize: 11.5, color: '#9aa0a6', flexShrink: 0 }}>지금 {totalActiveUsers.toLocaleString()}명 활동 중</span>
        </div>

        <div style={{ padding: '12px 18px 14px' }}>
          {isBot && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: '#f3e8ff', border: '1px solid #b083f2', borderRadius: 10, padding: '10px 14px', marginBottom: 12 }}>
              <span style={{ fontSize: 16 }}>🤖</span>
              <span style={{ fontSize: 13, color: '#5b2fa6', fontWeight: 500 }}>지금 실시간 상대가 없어 AI와 매칭되었습니다</span>
            </div>
          )}

          {chatExpired && (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, background: '#e8f0fe', border: '1px solid #3b7ff2', borderRadius: 10, padding: '10px 14px', marginBottom: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontSize: 16 }}>⏰</span>
                <span style={{ fontSize: 13, color: '#1a56c4', fontWeight: 500 }}>10분이 지나 대화가 자동 종료되었습니다</span>
              </div>
              <button onClick={endChat} style={{ flexShrink: 0, padding: '5px 14px', background: '#3b7ff2', color: '#fff', border: 'none', borderRadius: 8, fontSize: 12, fontWeight: 700, cursor: 'pointer' }}>확인</button>
            </div>
          )}

          {partnerLeft && !chatExpired && (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, background: '#fff8e1', border: '1px solid #f5b400', borderRadius: 10, padding: '10px 14px', marginBottom: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontSize: 16 }}>👋</span>
                <span style={{ fontSize: 13, color: '#5f3e00', fontWeight: 500 }}>상대방이 채팅을 종료했습니다</span>
              </div>
              <button onClick={endChat} style={{ flexShrink: 0, padding: '5px 14px', background: '#f5b400', color: '#fff', border: 'none', borderRadius: 8, fontSize: 12, fontWeight: 700, cursor: 'pointer' }}>확인</button>
            </div>
          )}

          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, marginBottom: 11 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ width: 8, height: 8, borderRadius: '50%', background: chatClosed ? '#9aa0a6' : '#34a06b', display: 'inline-block' }} />
              <span style={{ fontSize: 12, color: '#5f6368', display: 'flex', alignItems: 'center', gap: 6 }}>
                <b style={{ color: '#3c4043', fontWeight: 600 }}>{partnerNickname}</b>
                {isBot && (
                  <span style={{ display: 'inline-block', padding: '2px 7px', borderRadius: 8, fontSize: 10, fontWeight: 600, background: '#f3e8ff', color: '#7e3ff2' }}>
                    🤖 AI
                  </span>
                )}
                {opponentSituation && (
                  <span
                    title="상대방이 선택한 상황이에요"
                    style={{ display: 'inline-flex', alignItems: 'center', gap: 4, padding: '2px 8px', borderRadius: 8, fontSize: 10, fontWeight: 600, background: '#f1f3f4', color: '#5f6368' }}
                  >
                    <span style={{ opacity: 0.7, fontWeight: 500 }}>상대 상황</span>
                    {opponentSituation}
                  </span>
                )}
              </span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              {!chatClosed && (
                <span style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 12, color: chatTimeLeft <= 60 ? '#ea4c4c' : '#5f6368', fontWeight: 600 }}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none">
                    <circle cx="12" cy="12" r="9" stroke={chatTimeLeft <= 60 ? '#ea4c4c' : '#5f6368'} strokeWidth="2" />
                    <path d="M12 7v5l3 2" stroke={chatTimeLeft <= 60 ? '#ea4c4c' : '#5f6368'} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                  {fmtTimer(chatTimeLeft)} 남음
                </span>
              )}
              {!chatClosed && (
                <span onClick={endChat} style={{ border: '1px solid #f3c0bb', background: '#fef6f5', color: '#c5221f', borderRadius: 14, padding: '4px 13px', fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
                  채팅 종료
                </span>
              )}
            </div>
          </div>

          <div style={{ maxHeight: 220, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 1 }}>
            {messages.length === 0 && (
              <div style={{ padding: '20px 0', textAlign: 'center', fontSize: 13, color: '#bdc1c6' }}>대화를 시작해보세요</div>
            )}
            {displayMessages.map(msg => (
              <div
                key={msg.messageId}
                onClick={() => !msg.isMine && setReportTarget(msg)}
                style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '8px 16px', borderRadius: 8, background: msg.isMine ? 'transparent' : '#f8f9fa', cursor: msg.isMine ? 'default' : 'pointer' }}
                title={msg.isMine ? undefined : '클릭하여 신고'}
              >
                <ClockIcon stroke={msg.isMine ? '#3b7ff2' : '#9aa0a6'} />
                <span style={{ flex: 1, fontSize: 15, color: '#202124' }}>{msg.content}</span>
                {!msg.isMine && <span style={{ fontSize: 10, color: '#bdc1c6', flexShrink: 0 }}>⚑</span>}
              </div>
            ))}
          </div>
        </div>

        <div style={s.sep} />

        <div style={s.hintRow}>
          <span style={s.hintText}>최신 메시지가 위에 표시됩니다</span>
          <span style={s.hintText}>Enter 전송 · 상대 메시지 클릭 → 신고</span>
        </div>
      </div>

      {reportTarget && (
        <div
          onClick={closeReportModal}
          style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100, padding: 16, boxSizing: 'border-box' }}
        >
          <div
            onClick={e => e.stopPropagation()}
            style={{ background: '#fff', borderRadius: 16, padding: '26px 28px', width: '100%', maxWidth: 380, boxSizing: 'border-box', boxShadow: '0 4px 24px rgba(0,0,0,.18)' }}
          >
            {reportDone ? (
              <>
                <div style={{ fontSize: 15, fontWeight: 700, color: '#202124', marginBottom: 10 }}>신고가 접수되었습니다</div>
                <div style={{ fontSize: 13, color: '#5f6368', marginBottom: 20 }}>검토 후 조치가 이루어질 예정입니다.</div>
                <button onClick={closeReportModal} style={{ width: '100%', padding: '10px 0', background: '#3b7ff2', color: '#fff', border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>확인</button>
              </>
            ) : (
              <>
                <div style={{ fontSize: 15, fontWeight: 700, color: '#202124', marginBottom: 6 }}>메시지 신고</div>
                <div style={{ fontSize: 13, color: '#5f6368', background: '#f8f9fa', borderRadius: 8, padding: '10px 12px', marginBottom: 16, wordBreak: 'break-all' }}>
                  &ldquo;{reportTarget.content}&rdquo;
                </div>
                <div style={{ fontSize: 13, color: '#3c4043', fontWeight: 600, marginBottom: 10 }}>신고 사유</div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 20 }}>
                  {REPORT_REASONS.map(r => (
                    <label key={r} style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer', fontSize: 13, color: '#202124' }}>
                      <input
                        type="radio"
                        name="reason"
                        value={r}
                        checked={reportReason === r}
                        onChange={() => setReportReason(r)}
                        style={{ accentColor: '#3b7ff2' }}
                      />
                      {r}
                    </label>
                  ))}
                </div>
                <div style={{ display: 'flex', gap: 10 }}>
                  <button onClick={closeReportModal} style={{ flex: 1, padding: '10px 0', border: '1px solid #dadce0', borderRadius: 8, fontSize: 14, color: '#5f6368', background: '#fff', cursor: 'pointer' }}>취소</button>
                  <button
                    onClick={handleReport}
                    disabled={!reportReason || reportSubmitting}
                    style={{ flex: 1, padding: '10px 0', background: reportReason ? '#ea4c4c' : '#f1f3f4', color: reportReason ? '#fff' : '#9aa0a6', border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: reportReason ? 'pointer' : 'default' }}
                  >
                    {reportSubmitting ? '접수 중...' : '신고하기'}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </AppShell>
  );
}
