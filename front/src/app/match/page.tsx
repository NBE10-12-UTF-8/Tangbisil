'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { apiGetMatch, apiCancelMatch, apiGetMe, apiGetHomeStats, isLoggedIn, INDUSTRY_NAMES } from '@/lib/api';
import { AppShell } from '@/components/AppShell';
import { TangbisilLogo } from '@/components/TangbisilLogo';

const MATCH_KEY     = 'tangbisil_match';
const SITUATION_KEY = 'tangbisil_situation';
const TIMEOUT_KEY   = 'tangbisil_match_timeout';

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

function XIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" style={{ flexShrink: 0 }}>
      <line x1="6" y1="6" x2="18" y2="18" stroke="#70757a" strokeWidth="2" strokeLinecap="round" />
      <line x1="18" y1="6" x2="6" y2="18" stroke="#70757a" strokeWidth="2" strokeLinecap="round" />
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

export default function MatchPage() {
  const router = useRouter();

  const [elapsed, setElapsed]           = useState(0);
  const [situation, setSituation]       = useState('');
  const [userIndustry, setUserIndustry] = useState('');
  const [totalActiveUsers, setTotalActiveUsers] = useState(0);
  const matchIdRef      = useRef<string | null>(null);
  const elapsedTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const matchPollRef    = useRef<ReturnType<typeof setInterval> | null>(null);

  const cancelMatch = useCallback(async () => {
    if (matchPollRef.current)    { clearInterval(matchPollRef.current);    matchPollRef.current    = null; }
    if (elapsedTimerRef.current) { clearInterval(elapsedTimerRef.current); elapsedTimerRef.current = null; }
    localStorage.removeItem(MATCH_KEY);
    if (matchIdRef.current) {
      try { await apiCancelMatch(matchIdRef.current); } catch { /* ignore */ }
    }
    router.push('/');
  }, [router]);

  useEffect(() => {
    const raw = localStorage.getItem(MATCH_KEY);
    if (!raw) { router.push('/'); return; }
    let saved: { id: string; situation: string };
    try { saved = JSON.parse(raw); } catch { localStorage.removeItem(MATCH_KEY); router.push('/'); return; }

    matchIdRef.current = saved.id;
    setSituation(saved.situation);

    if (isLoggedIn()) {
      apiGetMe()
        .then(me => setUserIndustry(me.industry ? (INDUSTRY_NAMES[me.industry] ?? me.industry) : ''))
        .catch(() => {});
    }

    elapsedTimerRef.current = setInterval(() => setElapsed(p => p + 1), 1000);

    matchPollRef.current = setInterval(async () => {
      try {
        const data = await apiGetMatch(saved.id);
        if (data.status === 'MATCHED' && data.chatRoomId) {
          clearInterval(matchPollRef.current!);    matchPollRef.current    = null;
          clearInterval(elapsedTimerRef.current!); elapsedTimerRef.current = null;
          localStorage.setItem(SITUATION_KEY, saved.situation);
          localStorage.removeItem(MATCH_KEY);
          router.push(`/chat/${data.chatRoomId}`);
        }
      } catch (err) {
        if ((err as { status?: number })?.status === 404) {
          clearInterval(matchPollRef.current!);    matchPollRef.current    = null;
          clearInterval(elapsedTimerRef.current!); elapsedTimerRef.current = null;
          localStorage.removeItem(MATCH_KEY);
          localStorage.setItem(TIMEOUT_KEY, '1');
          router.push('/');
        }
      }
    }, 2000);

    return () => {
      if (matchPollRef.current)    clearInterval(matchPollRef.current);
      if (elapsedTimerRef.current) clearInterval(elapsedTimerRef.current);
    };
  }, [router]);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') cancelMatch(); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [cancelMatch]);

  useEffect(() => {
    apiGetHomeStats()
      .then(stats => setTotalActiveUsers(stats.totalActiveUsers))
      .catch((err) => {
        console.error('Failed to fetch home stats:', err);
      });
  }, []);

  const s = {
    card: { width: '100%', maxWidth: 560, background: '#fff', borderRadius: 24, boxShadow: '0 1px 10px rgba(32,33,36,.18)', marginTop: 20, boxSizing: 'border-box' } as const,
    searchRow: { height: 46, display: 'flex', alignItems: 'center', gap: 13, padding: '0 16px' } as const,
    sep: { height: 1, background: '#e8eaed', margin: '0 14px' } as const,
    industryRow: { display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 12, padding: '6px 18px', borderBottom: '1px solid #e8eaed' } as const,
    hintRow: { display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 4, padding: '9px 18px' } as const,
    hintText: { fontSize: 11, color: '#bdc1c6' } as const,
  };

  return (
    <AppShell>
      <div style={{ marginBottom: 22 }}><TangbisilLogo size={58} /></div>
      <div style={s.card}>
        <div style={s.searchRow}>
          <SearchIcon />
          <span style={{ flex: 1, display: 'flex', alignItems: 'center', gap: 9, fontSize: 16, color: '#3b7ff2' }}>
            <span style={{ display: 'flex', gap: 3, alignItems: 'center' }}>
              {[0, 0.2, 0.4].map((d, i) => (
                <span key={i} style={{ width: 6, height: 6, borderRadius: '50%', background: '#3b7ff2', display: 'inline-block', animation: `blink 1.1s infinite ${d}s` }} />
              ))}
            </span>
            매칭 중... {elapsed}s
          </span>
          <button onClick={cancelMatch} style={{ border: 'none', background: 'none', cursor: 'pointer', padding: 2 }}><XIcon /></button>
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

        <div style={{ padding: '34px 18px 30px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14 }}>
          <span style={{ width: 40, height: 40, border: '3px solid #e8eaed', borderTopColor: '#3b7ff2', borderRadius: '50%', animation: 'spin .9s linear infinite', display: 'inline-block' }} />
          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: 15, color: '#202124', fontWeight: 600 }}>같은 업계의 익명의 동료를 찾고 있어요</div>
            <div style={{ fontSize: 12.5, color: '#9aa0a6', marginTop: 5 }}>산업군 + 상황이 모두 맞는 상대를 우선 연결합니다</div>
          </div>
          <span onClick={cancelMatch} style={{ marginTop: 6, padding: '9px 22px', border: '1px solid #dadce0', color: '#5f6368', borderRadius: 20, fontSize: 13.5, fontWeight: 500, cursor: 'pointer' }}>
            매칭 취소
          </span>
        </div>

        <div style={s.sep} />

        <div style={s.hintRow}>
          <span style={s.hintText}>상대가 없으면 대기 상태가 유지돼요</span>
          <span style={s.hintText}>ESC 취소</span>
        </div>
      </div>
    </AppShell>
  );
}
