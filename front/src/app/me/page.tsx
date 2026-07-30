'use client';

import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useState, useEffect, Suspense } from 'react';
import { apiGetMe, apiUpdateMe, apiLogout, apiDeleteMe, clearTokens, INDUSTRY_NAMES, INDUSTRY_CODES, SUSPENDED_STORAGE_KEY } from '@/lib/api';

const CONTACT_URL = 'https://www.google.com/search?q=%EB%A9%94%EB%A1%B1&oq=%EB%A9%94%EB%A1%B1&gs_lcrp=EgZjaHJvbWUyBggAEEUYOTIGCAEQRRg90gEINDM5NGowajeoAgCwAgA&sourceid=chrome&ie=UTF-8';

const INDUSTRIES = [
  { name: 'IT/개발',       color: '#3b7ff2' },
  { name: '서비스업',      color: '#34a06b' },
  { name: '금융업',        color: '#f5b400' },
  { name: '의료서비스',    color: '#ea4c4c' },
  { name: '유통',          color: '#3b7ff2' },
  { name: '미디어/디자인', color: '#ea4c4c' },
  { name: '사무업',        color: '#34a06b' },
];

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

function MyPageInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const isOnboarding = searchParams.get('onboarding') === 'true';
  const [email, setEmail]         = useState('');
  const [current, setCurrent]     = useState('IT/개발');
  const [saved, setSaved]         = useState(false);
  const [saving, setSaving]       = useState(false);
  const [loading, setLoading]     = useState(true);
  const [suspended, setSuspended] = useState(false);

  useEffect(() => {
    if (localStorage.getItem(SUSPENDED_STORAGE_KEY)) {
      localStorage.removeItem(SUSPENDED_STORAGE_KEY);
      setSuspended(true);
      setLoading(false);
      return;
    }
    apiGetMe()
      .then(data => {
        setEmail(data.email);
        setCurrent(data.industry ? (INDUSTRY_NAMES[data.industry] ?? data.industry) : '');
      })
      .catch(() => router.replace('/login'))
      .finally(() => setLoading(false));
  }, [router]);

  const handleSave = async () => {
    setSaving(true);
    try {
      await apiUpdateMe(INDUSTRY_CODES[current] ?? current);
      if (isOnboarding) {
        router.replace('/');
        return;
      }
      setSaved(true);
    } finally {
      setSaving(false);
    }
  };

  const handleLogout = async () => {
    try { await apiLogout(); } catch {}
    clearTokens();
    router.replace('/login');
  };

  const handleDelete = async () => {
    if (!window.confirm('탈퇴 시 계정과 매칭 기록이 즉시 삭제됩니다. 계속하시겠어요?')) return;
    try { await apiDeleteMe(); } catch {}
    clearTokens();
    router.replace('/login');
  };

  if (loading) return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: "Arial, 'Helvetica Neue', sans-serif" }}>
      <span style={{ color: '#9aa0a6', fontSize: 13 }}>로딩 중...</span>
    </div>
  );

  if (suspended) return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', background: '#fff', fontFamily: "Arial, 'Helvetica Neue', sans-serif", gap: 16 }}>
      <div style={{ width: 56, height: 56, borderRadius: '50%', background: '#fce8e6', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <svg width="26" height="26" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="9" stroke="#c5221f" strokeWidth="2" />
          <line x1="12" y1="8" x2="12" y2="13" stroke="#c5221f" strokeWidth="2" strokeLinecap="round" />
          <circle cx="12" cy="16" r="1" fill="#c5221f" />
        </svg>
      </div>
      <div style={{ fontSize: 20, fontWeight: 700, color: '#202124' }}>계정이 정지됐습니다</div>
      <div style={{ fontSize: 14, color: '#5f6368', textAlign: 'center', lineHeight: 1.6 }}>
        관리자에 의해 계정이 정지되었어요.<br />
        문의를 통해 사유를 확인하고 해제를 요청하세요.
      </div>
      <div style={{ display: 'flex', gap: 10, marginTop: 4 }}>
        <a
          href={CONTACT_URL}
          target="_blank"
          rel="noopener noreferrer"
          style={{ padding: '10px 22px', background: '#3b7ff2', color: '#fff', borderRadius: 8, fontSize: 14, fontWeight: 600, textDecoration: 'none' }}
        >
          문의하기
        </a>
        <button
          onClick={handleLogout}
          style={{ padding: '10px 22px', border: '1px solid #dadce0', borderRadius: 8, fontSize: 14, color: '#5f6368', background: '#fff', cursor: 'pointer' }}
        >
          로그아웃
        </button>
      </div>
    </div>
  );

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: '#fff', fontFamily: "Arial, 'Helvetica Neue', sans-serif" }}>
      <div style={{ height: 54, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 30px', borderBottom: '1px solid #ebebeb' }}>
        <button onClick={() => router.push('/')} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>
          <TangbisilLogo size={24} />
        </button>
        <span style={{ fontSize: 13, color: '#5f6368' }}>내 계정</span>
      </div>

      <div style={{ flex: 1, padding: '34px 20px', maxWidth: 700, margin: '0 auto', width: '100%', boxSizing: 'border-box' }}>
        {isOnboarding && (
          <div style={{ marginBottom: 22, padding: '14px 18px', background: '#e8f0fe', border: '1px solid #c5d8fd', borderRadius: 12, display: 'flex', alignItems: 'flex-start', gap: 12 }}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" style={{ flexShrink: 0, marginTop: 1 }}>
              <circle cx="12" cy="12" r="9" stroke="#3b7ff2" strokeWidth="2" />
              <path d="M12 8v4M12 16h.01" stroke="#3b7ff2" strokeWidth="2" strokeLinecap="round" />
            </svg>
            <div>
              <div style={{ fontSize: 14, fontWeight: 700, color: '#1a56c4', marginBottom: 3 }}>소셜 로그인 완료! 마지막 단계예요</div>
              <div style={{ fontSize: 13, color: '#3b7ff2', lineHeight: 1.5 }}>
                아래에서 <strong>산업군을 선택하고 저장</strong>하면 익명 매칭을 바로 시작할 수 있어요.
              </div>
            </div>
          </div>
        )}

        <div style={{ display: 'flex', alignItems: 'center', gap: 18, marginBottom: 30 }}>
          <div style={{ width: 64, height: 64, borderRadius: '50%', background: '#e8f0fe', color: '#3b7ff2', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 24, fontWeight: 700 }}>동</div>
          <div>
            <div style={{ fontSize: 20, color: '#202124', fontWeight: 600 }}>익명의 동료</div>
            <div style={{ fontSize: 13, color: '#9aa0a6', marginTop: 3 }}>채팅 상대에게는 늘 '익명의 동료'로만 표시됩니다</div>
          </div>
        </div>

        <div style={{ border: '1px solid #ebebeb', borderRadius: 12, overflow: 'hidden', marginBottom: 22 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 20px', borderBottom: '1px solid #f1f1f1' }}>
            <span style={{ fontSize: 13, color: '#5f6368' }}>이메일</span>
            <span style={{ fontSize: 14, color: '#202124' }}>{email}</span>
          </div>
          <Link
            href="/me/history"
            style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 20px', textDecoration: 'none', color: 'inherit' }}
          >
            <span style={{ fontSize: 13, color: '#5f6368' }}>매칭 이력</span>
            <span style={{ fontSize: 13, color: '#3b7ff2', fontWeight: 600 }}>보기 →</span>
          </Link>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 11 }}>
          <span style={{ fontSize: 13, color: '#3c4043', fontWeight: 600 }}>산업군 변경</span>
          <span style={{ fontSize: 11.5, color: '#9aa0a6' }}>현재 · {current}</span>
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 9, marginBottom: 16 }}>
          {INDUSTRIES.map((ind) => {
            const sel = current === ind.name;
            return (
              <span
                key={ind.name}
                onClick={() => { setCurrent(ind.name); setSaved(false); }}
                style={{ display: 'inline-flex', alignItems: 'center', gap: 8, padding: '8px 14px', background: sel ? '#e8f0fe' : '#fff', border: `1.5px solid ${sel ? '#3b7ff2' : '#dadce0'}`, borderRadius: 10, fontSize: 14, color: sel ? '#1a56c4' : '#3c4043', fontWeight: 500, cursor: 'pointer' }}
              >
                <span style={{ width: 11, height: 11, borderRadius: 3, background: ind.color, flexShrink: 0 }} />
                {ind.name}
              </span>
            );
          })}
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 30 }}>
          <button
            onClick={handleSave}
            disabled={saving}
            style={{ padding: '9px 22px', background: saved ? '#34a06b' : '#3b7ff2', color: '#fff', borderRadius: 8, fontSize: 14, fontWeight: 600, border: 'none', cursor: 'pointer', transition: 'background .2s' }}
          >
            {saving ? '저장 중...' : saved ? '저장됨 ✓' : '변경 저장'}
          </button>
        </div>

        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 12 }}>
          <button
            onClick={handleLogout}
            style={{ padding: '11px 20px', border: '1px solid #dadce0', borderRadius: 8, fontSize: 14, color: '#3c4043', background: '#fff', cursor: 'pointer' }}
          >
            로그아웃
          </button>
          <button
            onClick={handleDelete}
            style={{ padding: '11px 20px', border: '1px solid #f3c0bb', background: '#fef6f5', borderRadius: 8, fontSize: 14, color: '#c5221f', fontWeight: 500, cursor: 'pointer' }}
          >
            회원 탈퇴
          </button>
          <span style={{ fontSize: 11.5, color: '#9aa0a6' }}>탈퇴 시 계정과 매칭 기록이 즉시 삭제됩니다</span>
        </div>
      </div>
    </div>
  );
}

export default function MyPage() {
  return (
    <Suspense
      fallback={
        <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: "Arial, 'Helvetica Neue', sans-serif" }}>
          <span style={{ color: '#9aa0a6', fontSize: 13 }}>로딩 중...</span>
        </div>
      }
    >
      <MyPageInner />
    </Suspense>
  );
}
