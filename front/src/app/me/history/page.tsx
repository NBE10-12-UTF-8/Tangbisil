'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { apiGetMatchHistory, isLoggedIn, INDUSTRY_NAMES, type MatchHistoryDto } from '@/lib/api';

const LOGO_CHARS = [
  { c: 'T', color: '#3b7ff2' }, { c: 'a', color: '#ea4c4c' }, { c: 'n', color: '#f5b400' },
  { c: 'g', color: '#3b7ff2' }, { c: 'b', color: '#34a06b' }, { c: 'i', color: '#ea4c4c' },
  { c: 's', color: '#f5b400' }, { c: 'i', color: '#3b7ff2' }, { c: 'l', color: '#34a06b' },
];
function TangbisilLogo({ size = 24 }: { size?: number }) {
  return (
    <span style={{ fontFamily: "var(--font-baloo2), 'Baloo 2', sans-serif", fontSize: size, fontWeight: 700, lineHeight: 1, letterSpacing: '-.8px', userSelect: 'none' }}>
      {LOGO_CHARS.map(({ c, color }, i) => <span key={i} style={{ color }}>{c}</span>)}
    </span>
  );
}

function fmtDate(iso: string) {
  const d = new Date(iso);
  return d.toLocaleString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}

const STATUS_LABEL: Record<string, string> = { ACTIVE: '진행 중', CLOSED: '종료' };
const STATUS_STYLE: Record<string, { background: string; color: string }> = {
  ACTIVE: { background: '#e6f4ea', color: '#137333' },
  CLOSED: { background: '#f1f3f4', color: '#5f6368' },
};
const PARTNER_STYLE = { background: '#f3e8ff', color: '#7e3ff2' };

export default function MatchHistoryPage() {
  const router = useRouter();
  const [history, setHistory] = useState<MatchHistoryDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!isLoggedIn()) { router.replace('/login'); return; }
    apiGetMatchHistory()
      .then(data => setHistory(data))
      .catch(() => setError('매칭 이력을 불러오지 못했어요'))
      .finally(() => setLoading(false));
  }, [router]);

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: '#fff', fontFamily: "Arial, 'Helvetica Neue', sans-serif" }}>
      <div style={{ height: 54, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 30px', borderBottom: '1px solid #ebebeb' }}>
        <button onClick={() => router.push('/')} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>
          <TangbisilLogo size={24} />
        </button>
        <span style={{ fontSize: 13, color: '#5f6368' }}>매칭 이력</span>
        <button onClick={() => router.back()} style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 13, color: '#5f6368' }}>← 돌아가기</button>
      </div>

      <div style={{ flex: 1, padding: '28px 20px', maxWidth: 700, margin: '0 auto', width: '100%', boxSizing: 'border-box' }}>
        {loading ? (
          <div style={{ textAlign: 'center', padding: '40px 0', color: '#9aa0a6', fontSize: 13 }}>로딩 중...</div>
        ) : error ? (
          <div style={{ textAlign: 'center', padding: '40px 0', color: '#ea4c4c', fontSize: 13 }}>{error}</div>
        ) : history.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '60px 0', color: '#9aa0a6', fontSize: 14 }}>매칭 이력이 없어요</div>
        ) : (
          <div style={{ border: '1px solid #ebebeb', borderRadius: 12, overflow: 'hidden' }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1.4fr 0.9fr 1.1fr 0.7fr 0.8fr', padding: '12px 20px', background: '#f8f9fa', fontSize: 12, color: '#9aa0a6', borderBottom: '1px solid #ebebeb' }}>
              <span>일시</span><span>산업군</span><span>상황</span><span>상대</span><span style={{ textAlign: 'right' }}>상태</span>
            </div>
            {[...history].reverse().map((item, i) => (
              <div key={i} style={{ display: 'grid', gridTemplateColumns: '1.4fr 0.9fr 1.1fr 0.7fr 0.8fr', alignItems: 'center', padding: '13px 20px', fontSize: 13, color: '#3c4043', borderBottom: i < history.length - 1 ? '1px solid #f1f1f1' : 'none' }}>
                <span style={{ color: '#5f6368', fontSize: 12 }}>{fmtDate(item.matchedAt)}</span>
                <span>{INDUSTRY_NAMES[item.industry] ?? item.industry}</span>
                <span style={{ color: '#5f6368' }}>{item.situation}</span>
                <span>
                  {item.isBot && (
                    <span style={{ display: 'inline-block', padding: '3px 9px', borderRadius: 9, fontSize: 11, fontWeight: 600, ...PARTNER_STYLE }}>
                      🤖 AI
                    </span>
                  )}
                </span>
                <span style={{ textAlign: 'right' }}>
                  <span style={{ display: 'inline-block', padding: '3px 9px', borderRadius: 9, fontSize: 11, fontWeight: 600, ...(STATUS_STYLE[item.status] ?? { background: '#f1f3f4', color: '#5f6368' }) }}>
                    {STATUS_LABEL[item.status] ?? item.status}
                  </span>
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
