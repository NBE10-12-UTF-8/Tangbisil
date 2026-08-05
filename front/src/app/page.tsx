'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import {
  apiCreateMatch, apiGetMatch, apiGetActiveRoom,
  apiGetMe, apiGetHomeStats, apiGetTrendKeywords, isLoggedIn, INDUSTRY_NAMES,
  type TrendKeyword,
} from '@/lib/api';
import { AppShell } from '@/components/AppShell';
import { TangbisilLogo } from '@/components/TangbisilLogo';

const MATCH_KEY   = 'tangbisil_match';
const TIMEOUT_KEY = 'tangbisil_match_timeout';

const TOPICS = [
  { label: '야근 중' },
  { label: '회의 폭탄' },
  { label: '사내 연애 폭로' },
  { label: '상사 억까' },
  { label: '사내 정치 피로' },
  { label: '이직 마려움' },
  { label: '연봉 협상 앞둠' },
  { label: '몰래 루팡중' },
  { label: '기타' },
];

const TREND_ICON: Record<'up' | 'down' | 'flat', string> = { up: '▲', down: '▼', flat: '—' };
const TREND_COLOR: Record<'up' | 'down' | 'flat', string> = { up: '#ea4c4c', down: '#3b7ff2', flat: '#9aa0a6' };

function SearchIcon({ onClick }: { onClick?: () => void }) {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none"
      onClick={onClick}
      style={{ flexShrink: 0, cursor: onClick ? 'pointer' : 'default' }}
    >
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

export default function HomePage() {
  const router = useRouter();
  const [preClick, setPreClick]           = useState(true);
  const [selectedTopic, setSelectedTopic] = useState<string | null>(null);
  const [userIndustry, setUserIndustry]   = useState('');
  const [matchError, setMatchError]       = useState('');
  const [showTimeout, setShowTimeout]     = useState(false);
  const [totalActiveUsers, setTotalActiveUsers] = useState(0);
  const [situationCounts, setSituationCounts]   = useState<Record<string, number>>({});
  const [hotKeywords, setHotKeywords]           = useState<TrendKeyword[]>([]);

  useEffect(() => {
    apiGetHomeStats()
      .then(stats => {
        setTotalActiveUsers(stats.totalActiveUsers);
        setSituationCounts(
          Object.fromEntries(stats.situationStats.map(s => [s.situation, s.count])),
        );
      })
      .catch((err) => {
        console.error('Failed to fetch home stats:', err);
      });

    apiGetTrendKeywords()
      .then(setHotKeywords)
      .catch((err) => {
        console.error('Failed to fetch trend keywords:', err);
      });
  }, []);

  useEffect(() => {
    const timeoutFlag = localStorage.getItem(TIMEOUT_KEY);
    if (timeoutFlag) {
      localStorage.removeItem(TIMEOUT_KEY);
      setShowTimeout(true);
    }

    if (!isLoggedIn()) return;

    apiGetMe()
      .then(me => setUserIndustry(me.industry ? (INDUSTRY_NAMES[me.industry] ?? me.industry) : ''))
      .catch(() => {});

    apiGetActiveRoom()
      .then(room => {
        if (room) { router.push(`/chat/${room.roomId}`); return; }

        const raw = localStorage.getItem(MATCH_KEY);
        if (!raw) return;
        let saved: { id: string; situation: string };
        try { saved = JSON.parse(raw); } catch { localStorage.removeItem(MATCH_KEY); return; }

        apiGetMatch(saved.id)
          .then(data => {
            // 여기 도달했다는 건 바로 위 apiGetActiveRoom()이 이미 "활성 채팅방 없음"이라고
            // 답했다는 뜻이다. 그런데도 MATCHED 상태라면, 매칭 자체는 성사됐지만 그 채팅방은
            // 이미 종료된 것 - MATCHED는 채팅방이 나중에 종료돼도 영구히 남는 상태라서다.
            // 예전엔 여기서 그 종료된 방으로 리다이렉트했는데, tangbisil_match를 안 지워서
            // 홈에 올 때마다 같은 종료된 방으로 계속 돌아가는 무한 루프가 됐다.
            if (data.status === 'PENDING') {
              router.push('/match');
            } else {
              localStorage.removeItem(MATCH_KEY);
            }
          })
          .catch(() => localStorage.removeItem(MATCH_KEY));
      })
      .catch((err: unknown) => {
        if ((err as { status?: number })?.status === 403) {
          localStorage.setItem('tangbisil_suspended', '1');
          router.replace('/me');
        }
      });
  }, []);

  const startMatch = useCallback(async () => {
    if (!isLoggedIn()) { setMatchError('LOGIN_REQUIRED'); return; }
    if (!selectedTopic) { setMatchError('상황 칩을 선택해주세요'); return; }
    setMatchError('');
    try {
      const data = await apiCreateMatch(selectedTopic);
      localStorage.setItem(MATCH_KEY, JSON.stringify({ id: data.matchRequestId, situation: selectedTopic, requestedAt: data.requestedAt }));
      router.push('/match');
    } catch (err: unknown) {
      const status = (err as { status?: number })?.status;
      if (status === 401) { router.push('/login'); return; }
      setMatchError((err as Error)?.message || '매칭을 시작하지 못했어요. 다시 시도해주세요');
    }
  }, [selectedTopic, router]);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key !== 'Enter' || e.isComposing) return;
      if (preClick) { setPreClick(false); return; }
      startMatch();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [preClick, startMatch]);

  // 실시간 매칭 대기 인원(situationCounts)에서 실제로 count > 0인 상황만 🔥 HOT 후보로 삼아
  // 최대 3개까지 앞쪽에 노출한다. count가 0인 상황은 순위와 무관하게 절대 HOT으로 표시하지 않는다.
  const rankedTopics = useMemo(() => {
    const active = TOPICS
      .filter(t => (situationCounts[t.label] ?? 0) > 0)
      .sort((a, b) => situationCounts[b.label] - situationCounts[a.label]);
    const hot = active.slice(0, 3);
    const hotLabels = hot.map(t => t.label);
    const rest = TOPICS.filter(t => !hotLabels.includes(t.label));
    return [...hot, ...rest].map(t => ({
      ...t,
      hotRank: hotLabels.indexOf(t.label) + 1,
    }));
  }, [situationCounts]);

  const s = {
    card: { width: '100%', maxWidth: 560, background: '#fff', borderRadius: 24, boxShadow: '0 1px 10px rgba(32,33,36,.18)', marginTop: 20, boxSizing: 'border-box' } as const,
    searchRow: { height: 46, display: 'flex', alignItems: 'center', gap: 13, padding: '0 16px' } as const,
    sep: { height: 1, background: '#e8eaed', margin: '0 14px' } as const,
    industryRow: { display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 12, padding: '6px 18px', borderBottom: '1px solid #e8eaed' } as const,
    hintRow: { display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 4, padding: '9px 18px' } as const,
    hintText: { fontSize: 11, color: '#bdc1c6' } as const,
  };

  return (
    <AppShell topAlign>
      <TangbisilLogo size={58} />

      {showTimeout && (
        <div style={{ width: '100%', maxWidth: 560, boxSizing: 'border-box', marginTop: 14, display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 12, background: '#fef7e0', border: '1px solid #f5b400', borderRadius: 12, padding: '10px 16px' }}>
          <span style={{ fontSize: 13, color: '#5f3e00' }}>매칭 시간이 초과됐어요. 다시 시도해보세요.</span>
          <button onClick={() => setShowTimeout(false)} style={{ border: 'none', background: 'none', cursor: 'pointer', padding: 2, flexShrink: 0 }}><XIcon /></button>
        </div>
      )}

      <div style={s.card}>
        <div style={s.searchRow}>
          <SearchIcon onClick={preClick ? () => setPreClick(false) : startMatch} />
          {preClick ? (
            <span
              onClick={() => setPreClick(false)}
              style={{ flex: 1, fontSize: 16, color: '#9aa0a6', cursor: 'text' }}
            >
              지금 겪고 있는 상황으로 익명 매칭하기
            </span>
          ) : (
            <span style={{ flex: 1, fontSize: 16, color: selectedTopic ? '#3c4043' : '#9aa0a6' }}>
              {selectedTopic ?? '아래에서 상황을 골라주세요'}
            </span>
          )}
          {!preClick && selectedTopic && (
            <button onClick={() => setSelectedTopic(null)} style={{ border: 'none', background: 'none', cursor: 'pointer', padding: 2 }}>
              <XIcon />
            </button>
          )}
          <div style={{ width: 1, height: 24, background: '#dfe1e5', flexShrink: 0 }} />
          <MicIcon />
        </div>

        <div style={s.sep} />

        {!preClick && (
          <>
            <div style={s.industryRow}>
              <span style={{ fontSize: 11.5, color: '#9aa0a6', flexShrink: 0 }}>내 산업군</span>
              {userIndustry ? (
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, padding: '4px 11px', background: '#f3f8ff', color: '#3b7ff2', borderRadius: 14, fontSize: 12.5, fontWeight: 600, flexShrink: 0 }}>
                  <LockIcon />{userIndustry}
                </span>
              ) : (
                <span style={{ fontSize: 11.5, color: '#bdc1c6' }}>로그인 후 확인</span>
              )}
              {userIndustry && (
                <span style={{ fontSize: 11, color: '#bdc1c6' }}>가입 시 선택한 업계로 고정</span>
              )}
              <span style={{ marginLeft: 'auto', fontSize: 11.5, color: '#9aa0a6', flexShrink: 0 }}>지금 {totalActiveUsers.toLocaleString()}명 활동 중</span>
            </div>

            <div style={{ padding: '12px 18px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 10 }}>
                <span style={{ width: 7, height: 7, borderRadius: '50%', background: '#ea4c4c', display: 'inline-block' }} />
                <span style={{ fontSize: 11.5, color: '#5f6368' }}>지금 가장 많이 얘기되는 상황 — 골라서 매칭하세요</span>
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                {rankedTopics.map(t => {
                  const active = selectedTopic === t.label;
                  const isHot = t.hotRank > 0;
                  const border = active ? '#3b7ff2' : isHot ? '#ef9a9a' : '#e8eaed';
                  const color = active ? '#3b7ff2' : isHot ? '#c1503f' : '#3c4043';
                  const background = active ? '#e8f0fe' : isHot ? '#fff' : '#f8f9fa';
                  return (
                    <span
                      key={t.label}
                      onClick={() => { setMatchError(''); setSelectedTopic(active ? null : t.label); }}
                      style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '6px 12px', background, border: `1px solid ${border}`, borderRadius: 16, fontSize: 13, color, fontWeight: active || isHot ? 600 : 400, cursor: 'pointer' }}
                    >
                      {isHot && <span style={{ fontSize: 11 }}>🔥</span>}
                      {t.label}
                      <span style={{ color: active ? '#7ea8f0' : isHot ? '#e29a91' : '#80868b', fontSize: 11.5 }}>{(situationCounts[t.label] ?? 0).toLocaleString()}</span>
                    </span>
                  );
                })}
              </div>
              <button
                onClick={startMatch}
                disabled={!selectedTopic}
                style={{ marginTop: 14, width: '100%', height: 36, background: selectedTopic ? '#3b7ff2' : '#f1f3f4', color: selectedTopic ? '#fff' : '#9aa0a6', border: 'none', borderRadius: 18, fontSize: 14, fontWeight: 600, cursor: selectedTopic ? 'pointer' : 'default' }}
              >
                매칭시작하기
              </button>
              <div style={{ marginTop: 6, textAlign: 'center', fontSize: 11, color: '#bdc1c6' }}>같은 업계의 동료와 매칭돼요.</div>

              {matchError === 'LOGIN_REQUIRED' ? (
                <div style={{ marginTop: 10, fontSize: 12, color: '#5f6368' }}>
                  매칭하려면 로그인이 필요해요.{' '}
                  <Link href="/login" style={{ color: '#3b7ff2', fontWeight: 600, textDecoration: 'none' }}>로그인하기 →</Link>
                </div>
              ) : matchError ? (
                <div style={{ marginTop: 10, fontSize: 12, color: '#ea4c4c' }}>{matchError}</div>
              ) : null}

              <div style={{ height: 1, background: '#e8eaed', margin: '18px 0 16px' }} />
              <div style={{ fontSize: 12.5, color: '#202124', fontWeight: 700, marginBottom: 11 }}>실시간 HOT 키워드</div>
              {hotKeywords.length > 0 ? (
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gridAutoFlow: 'column', gridTemplateRows: 'repeat(5, auto)', columnGap: 20 }}>
                  {hotKeywords.map(k => (
                    <div key={k.rank} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '6px 0' }}>
                      <span style={{ width: 16, fontSize: 13, color: k.rank <= 3 ? '#1a56c4' : '#9aa0a6', fontWeight: 700 }}>{k.rank}</span>
                      <span style={{ flex: 1, fontSize: 13.5, color: '#202124' }}>{k.label}</span>
                      <span style={{ fontSize: 10, color: TREND_COLOR[k.trend] }}>{TREND_ICON[k.trend]}</span>
                    </div>
                  ))}
                </div>
              ) : (
                <div style={{ fontSize: 12.5, color: '#9aa0a6', textAlign: 'center', padding: '14px 0' }}>데이터 집계중입니다</div>
              )}
            </div>

            <div style={s.sep} />
          </>
        )}

        <div style={s.hintRow}>
          {preClick ? (
            <span style={{ ...s.hintText, display: 'flex', alignItems: 'center', gap: 5 }}>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
                <path d="M12 5v6l4 2" stroke="#bdc1c6" strokeWidth="2" strokeLinecap="round" />
                <circle cx="12" cy="12" r="9" stroke="#bdc1c6" strokeWidth="2" />
              </svg>
              검색창을 누르면 상황 선택이 펼쳐집니다 →
            </span>
          ) : (
            <>
              <span style={s.hintText}>상황을 고르면 같은 업계의 익명의 동료와 매칭돼요</span>
              <span style={s.hintText}>매칭하기 버튼 또는 Enter로 매칭</span>
            </>
          )}
        </div>
      </div>
    </AppShell>
  );
}
