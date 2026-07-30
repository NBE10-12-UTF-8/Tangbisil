'use client';

import Link from 'next/link';
import { useState, useEffect, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { apiLogin, apiGetActiveRoom, setTokens, setAdmin, getRoleFromToken, OAUTH_SERVER_BASE, SUSPENDED_STORAGE_KEY, isValidEmail } from '@/lib/api';

const LOGO_CHARS = [
  { c: 'T', color: '#3b7ff2' }, { c: 'a', color: '#ea4c4c' }, { c: 'n', color: '#f5b400' },
  { c: 'g', color: '#3b7ff2' }, { c: 'b', color: '#34a06b' }, { c: 'i', color: '#ea4c4c' },
  { c: 's', color: '#f5b400' }, { c: 'i', color: '#3b7ff2' }, { c: 'l', color: '#34a06b' },
];
function TangbisilLogo({ size = 42 }: { size?: number }) {
  const ls = size >= 35 ? '-1.2px' : '-0.8px';
  return (
    <span style={{ fontFamily: "var(--font-baloo2), 'Baloo 2', sans-serif", fontSize: size, fontWeight: 700, lineHeight: 1, letterSpacing: ls, userSelect: 'none' }}>
      {LOGO_CHARS.map(({ c, color }, i) => <span key={i} style={{ color }}>{c}</span>)}
    </span>
  );
}

function LoginPageInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [pwFocused, setPwFocused] = useState(false);
  const [showPw, setShowPw] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showSignupToast, setShowSignupToast] = useState(() => searchParams.get('signup') === 'success');
  const [showResetToast, setShowResetToast] = useState(() => searchParams.get('reset') === 'success');

  useEffect(() => {
    if (!showSignupToast) return;
    const t = setTimeout(() => setShowSignupToast(false), 3000);
    return () => clearTimeout(t);
  }, [showSignupToast]);

  useEffect(() => {
    if (!showResetToast) return;
    const t = setTimeout(() => setShowResetToast(false), 3000);
    return () => clearTimeout(t);
  }, [showResetToast]);

  const handleLogin = async () => {
    if (!email || !password) return;
    if (!isValidEmail(email)) {
      setError('올바른 이메일 형식이 아니에요');
      return;
    }
    setError('');
    setLoading(true);
    try {
      const data = await apiLogin(email, password);
      setTokens(data.accessToken, data.refreshToken);
      if (getRoleFromToken(data.accessToken) === 'ADMIN') {
        setAdmin();
        router.replace('/admin/stats');
        return;
      }
      try {
        await apiGetActiveRoom();
      } catch (checkErr: unknown) {
        if ((checkErr as { status?: number })?.status === 403) {
          localStorage.setItem(SUSPENDED_STORAGE_KEY, '1');
          router.replace('/me');
          return;
        }
      }
      router.replace('/');
    } catch {
      setError('이메일 또는 비밀번호가 올바르지 않아요');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', background: '#fff', fontFamily: "Arial, 'Helvetica Neue', sans-serif", padding: '24px 16px', boxSizing: 'border-box' }}>
      {showSignupToast && (
        <div style={{ position: 'fixed', top: 24, left: '50%', transform: 'translateX(-50%)', display: 'flex', alignItems: 'center', gap: 8, background: '#202124', color: '#fff', borderRadius: 10, padding: '12px 18px', fontSize: 13.5, fontWeight: 500, boxShadow: '0 4px 16px rgba(0,0,0,.2)', zIndex: 200 }}>
          <span style={{ color: '#34a06b' }}>✓</span>
          회원가입이 완료됐어요. 로그인해주세요
        </div>
      )}
      {showResetToast && (
        <div style={{ position: 'fixed', top: 24, left: '50%', transform: 'translateX(-50%)', display: 'flex', alignItems: 'center', gap: 8, background: '#202124', color: '#fff', borderRadius: 10, padding: '12px 18px', fontSize: 13.5, fontWeight: 500, boxShadow: '0 4px 16px rgba(0,0,0,.2)', zIndex: 200 }}>
          <span style={{ color: '#34a06b' }}>✓</span>
          비밀번호가 변경됐어요. 새 비밀번호로 로그인해주세요
        </div>
      )}
      <Link href="/" style={{ textDecoration: 'none' }}><TangbisilLogo size={42} /></Link>
      <div style={{ fontSize: 14, color: '#5f6368', marginBottom: 26, marginTop: 8, textAlign: 'center' }}>
        검색하듯 로그인하고, 익명으로 동료와 연결되세요
      </div>

      <div style={{ width: '100%', maxWidth: 400, border: '1px solid #dadce0', borderRadius: 14, padding: '30px 24px 26px', boxSizing: 'border-box' }}>
        <div style={{ fontSize: 20, color: '#202124', fontWeight: 500, marginBottom: 22 }}>로그인</div>

        <div style={{ fontSize: 12, color: '#5f6368', marginBottom: 6 }}>이메일</div>
        <input
          type="email"
          value={email}
          onChange={e => setEmail(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleLogin()}
          placeholder="work@company.com"
          style={{ width: '100%', height: 46, border: '1px solid #dadce0', borderRadius: 8, padding: '0 14px', fontSize: 15, color: '#202124', marginBottom: 16, outline: 'none', boxSizing: 'border-box' }}
        />

        <div style={{ fontSize: 12, color: '#5f6368', marginBottom: 6 }}>비밀번호</div>
        <div style={{ width: '100%', height: 46, border: `1px solid ${error ? '#ea4c4c' : pwFocused ? '#3b7ff2' : '#dadce0'}`, borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 14px', marginBottom: 10, boxSizing: 'border-box' }}>
          <input
            type={showPw ? 'text' : 'password'}
            value={password}
            onChange={e => setPassword(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleLogin()}
            onFocus={() => setPwFocused(true)}
            onBlur={() => setPwFocused(false)}
            placeholder="••••••••"
            style={{ border: 'none', outline: 'none', fontSize: 16, color: '#202124', letterSpacing: 2, flex: 1, background: 'transparent' }}
          />
          <button onClick={() => setShowPw(v => !v)} style={{ fontSize: 12, color: '#3b7ff2', background: 'none', border: 'none', cursor: 'pointer', flexShrink: 0 }}>
            {showPw ? '숨기기' : '표시'}
          </button>
        </div>

        {error && (
          <div style={{ fontSize: 12, color: '#ea4c4c', marginBottom: 10 }}>{error}</div>
        )}

        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 22 }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: 7, fontSize: 12.5, color: '#5f6368', cursor: 'pointer' }}>
            <input type="checkbox" style={{ width: 15, height: 15, accentColor: '#3b7ff2' }} />
            로그인 유지
          </label>
          <Link href="/forgot-password" style={{ fontSize: 12.5, color: '#3b7ff2', textDecoration: 'none' }}>비밀번호 찾기</Link>
        </div>

        <button
          onClick={handleLogin}
          disabled={loading}
          style={{ width: '100%', height: 46, background: loading ? '#9aa0a6' : '#3b7ff2', color: '#fff', borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 15, fontWeight: 600, border: 'none', cursor: loading ? 'default' : 'pointer' }}
        >
          {loading ? '로그인 중...' : '로그인'}
        </button>

        <div style={{ textAlign: 'center', fontSize: 13, color: '#5f6368', marginTop: 18 }}>
          계정이 없으신가요?{' '}
          <Link href="/signup" style={{ color: '#3b7ff2', fontWeight: 600, textDecoration: 'none' }}>회원가입</Link>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, margin: '20px 0' }}>
          <div style={{ flex: 1, height: 1, background: '#ebebeb' }} />
          <span style={{ fontSize: 12, color: '#9aa0a6' }}>또는</span>
          <div style={{ flex: 1, height: 1, background: '#ebebeb' }} />
        </div>

        <a
          href={`${OAUTH_SERVER_BASE}/oauth2/authorization/google`}
          style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10, width: '100%', height: 46, border: '1px solid #dadce0', borderRadius: 8, fontSize: 14, fontWeight: 500, color: '#3c4043', textDecoration: 'none', boxSizing: 'border-box', marginBottom: 10 }}
        >
          <svg width="18" height="18" viewBox="0 0 24 24"><path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/><path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/><path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/><path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/></svg>
          Google로 계속하기
        </a>
        <a
          href={`${OAUTH_SERVER_BASE}/oauth2/authorization/kakao`}
          style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10, width: '100%', height: 46, background: '#FEE500', borderRadius: 8, fontSize: 14, fontWeight: 500, color: '#191919', textDecoration: 'none', boxSizing: 'border-box' }}
        >
          <svg width="18" height="18" viewBox="0 0 24 24"><path fill="#191919" d="M12 3C6.48 3 2 6.48 2 10.8c0 2.76 1.83 5.19 4.6 6.58-.2.75-.73 2.73-.84 3.15-.13.52.19.51.4.37.16-.11 2.6-1.77 3.66-2.49.7.1 1.42.15 2.18.15 5.52 0 10-3.48 10-7.76S17.52 3 12 3z"/></svg>
          카카오로 계속하기
        </a>
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <LoginPageInner />
    </Suspense>
  );
}
