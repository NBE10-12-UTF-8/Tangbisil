'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  apiSignup, apiSendEmailVerification, apiConfirmEmailVerification,
  INDUSTRY_CODES, isValidEmail,
} from '@/lib/api';

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
function TangbisilLogo({ size = 34 }: { size?: number }) {
  const ls = size >= 35 ? '-1.2px' : '-0.8px';
  return (
    <span style={{ fontFamily: "var(--font-baloo2), 'Baloo 2', sans-serif", fontSize: size, fontWeight: 700, lineHeight: 1, letterSpacing: ls, userSelect: 'none' }}>
      {LOGO_CHARS.map(({ c, color }, i) => <span key={i} style={{ color }}>{c}</span>)}
    </span>
  );
}

export default function SignupPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [selected, setSelected] = useState<string | null>(null);
  const [agreedToTerms, setAgreedToTerms] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const [emailVerified, setEmailVerified] = useState(false);
  const [verifySent, setVerifySent] = useState(false);
  const [verifyCode, setVerifyCode] = useState('');
  const [sendingCode, setSendingCode] = useState(false);
  const [confirmingCode, setConfirmingCode] = useState(false);
  const [verifyError, setVerifyError] = useState('');

  const emailOk = isValidEmail(email);
  const pwOk = password.length >= 4;
  const canSubmit = !!(emailOk && emailVerified && pwOk && password === confirm && selected && agreedToTerms && !loading);

  const handleEmailChange = (value: string) => {
    setEmail(value);
    setEmailVerified(false);
    setVerifySent(false);
    setVerifyCode('');
    setVerifyError('');
  };

  const sendVerificationCode = async () => {
    if (!emailOk || sendingCode) return;
    setVerifyError('');
    setSendingCode(true);
    try {
      await apiSendEmailVerification(email);
      setVerifySent(true);
    } catch (e: unknown) {
      setVerifyError((e as Error)?.message ?? '인증 코드 발송에 실패했어요');
    } finally {
      setSendingCode(false);
    }
  };

  const confirmVerificationCode = async () => {
    if (!verifyCode || confirmingCode) return;
    setVerifyError('');
    setConfirmingCode(true);
    try {
      await apiConfirmEmailVerification(email, verifyCode);
      setEmailVerified(true);
    } catch (e: unknown) {
      setVerifyError((e as Error)?.message ?? '인증 코드 확인에 실패했어요');
    } finally {
      setConfirmingCode(false);
    }
  };

  const handleSignup = async () => {
    if (!canSubmit) return;
    setError('');
    setLoading(true);
    try {
      await apiSignup(email, password, INDUSTRY_CODES[selected!] ?? selected!, agreedToTerms);
      router.replace('/login?signup=success');
    } catch (e: unknown) {
      setError((e as Error)?.message ?? '회원가입에 실패했어요');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', background: '#fff', fontFamily: "Arial, 'Helvetica Neue', sans-serif", padding: '34px 16px 30px', boxSizing: 'border-box', overflowY: 'auto' }}>
      <Link href="/" style={{ textDecoration: 'none' }}><TangbisilLogo size={34} /></Link>
      <div style={{ fontSize: 20, color: '#202124', fontWeight: 500, marginBottom: 4, marginTop: 6 }}>계정 만들기</div>
      <div style={{ fontSize: 13, color: '#5f6368', marginBottom: 24, textAlign: 'center' }}>실명·회사명·연락처는 받지 않아요. 익명으로 시작합니다</div>

      <div style={{ width: '100%', maxWidth: 560 }}>
        {/* Email */}
        <div style={{ marginBottom: 14 }}>
          <div style={{ fontSize: 12, color: '#5f6368', marginBottom: 6 }}>이메일</div>
          <div style={{ display: 'flex', gap: 8 }}>
            <input
              type="email"
              value={email}
              onChange={e => handleEmailChange(e.target.value)}
              placeholder="work@company.com"
              disabled={emailVerified}
              style={{ flex: 1, height: 46, border: '1px solid #dadce0', borderRadius: 8, padding: '0 14px', fontSize: 15, color: '#202124', outline: 'none', boxSizing: 'border-box', background: emailVerified ? '#f8f9fa' : '#fff' }}
            />
            <button
              type="button"
              onClick={sendVerificationCode}
              disabled={!emailOk || sendingCode || emailVerified}
              style={{ flexShrink: 0, padding: '0 16px', height: 46, borderRadius: 8, fontSize: 13, fontWeight: 600, border: 'none', cursor: emailOk && !emailVerified ? 'pointer' : 'default', background: emailVerified ? '#e6f4ea' : emailOk ? '#3b7ff2' : '#f1f3f4', color: emailVerified ? '#137333' : emailOk ? '#fff' : '#9aa0a6' }}
            >
              {emailVerified ? '인증 완료' : sendingCode ? '발송 중...' : verifySent ? '재발송' : '인증코드 받기'}
            </button>
          </div>
          {email && !emailVerified && (
            <div style={{ fontSize: 11.5, color: emailOk ? '#34a06b' : '#9aa0a6', marginTop: 6 }}>
              {emailOk ? '✓ 사용 가능한 이메일 형식이에요' : '올바른 이메일 형식으로 입력해주세요 (예: work@company.com)'}
            </div>
          )}

          {verifySent && !emailVerified && (
            <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
              <input
                type="text"
                inputMode="numeric"
                maxLength={6}
                value={verifyCode}
                onChange={e => setVerifyCode(e.target.value.replace(/\D/g, ''))}
                onKeyDown={e => e.key === 'Enter' && confirmVerificationCode()}
                placeholder="6자리 인증코드"
                style={{ flex: 1, height: 42, border: '1px solid #dadce0', borderRadius: 8, padding: '0 14px', fontSize: 15, color: '#202124', outline: 'none', boxSizing: 'border-box', letterSpacing: 2 }}
              />
              <button
                type="button"
                onClick={confirmVerificationCode}
                disabled={verifyCode.length !== 6 || confirmingCode}
                style={{ flexShrink: 0, padding: '0 16px', height: 42, borderRadius: 8, fontSize: 13, fontWeight: 600, border: '1px solid #3b7ff2', cursor: verifyCode.length === 6 ? 'pointer' : 'default', background: '#fff', color: '#3b7ff2', opacity: verifyCode.length === 6 ? 1 : 0.5 }}
              >
                {confirmingCode ? '확인 중...' : '확인'}
              </button>
            </div>
          )}
          {emailVerified && (
            <div style={{ fontSize: 11.5, color: '#34a06b', marginTop: 6 }}>✓ 이메일 인증이 완료됐어요</div>
          )}
          {verifyError && (
            <div style={{ fontSize: 11.5, color: '#ea4c4c', marginTop: 6 }}>{verifyError}</div>
          )}
        </div>

        {/* Password row */}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 14, marginBottom: 6 }}>
          <div style={{ flex: '1 1 200px' }}>
            <div style={{ fontSize: 12, color: '#5f6368', marginBottom: 6 }}>비밀번호</div>
            <input
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              placeholder="••••••••"
              style={{ width: '100%', height: 46, border: '1px solid #dadce0', borderRadius: 8, padding: '0 14px', fontSize: 15, outline: 'none', boxSizing: 'border-box' }}
            />
          </div>
          <div style={{ flex: '1 1 200px' }}>
            <div style={{ fontSize: 12, color: '#5f6368', marginBottom: 6 }}>비밀번호 확인</div>
            <input
              type="password"
              value={confirm}
              onChange={e => setConfirm(e.target.value)}
              placeholder="••••••••"
              style={{ width: '100%', height: 46, border: `1px solid ${confirm && confirm !== password ? '#ea4c4c' : '#dadce0'}`, borderRadius: 8, padding: '0 14px', fontSize: 15, outline: 'none', boxSizing: 'border-box' }}
            />
          </div>
        </div>
        <div style={{ fontSize: 11.5, color: pwOk ? '#34a06b' : '#9aa0a6', marginBottom: 22 }}>
          {pwOk ? '✓ 4자 이상 · 사용 가능한 비밀번호예요' : '비밀번호는 4자 이상으로 입력해주세요'}
        </div>

        {/* Industry */}
        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 6, marginBottom: 11 }}>
          <span style={{ fontSize: 13, color: '#3c4043', fontWeight: 600 }}>산업군</span>
          <span style={{ fontSize: 11, color: '#ea4c4c' }}>필수</span>
          <span style={{ fontSize: 11.5, color: '#9aa0a6' }}>같은 업계 사람과 우선 매칭돼요</span>
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 9, marginBottom: 14 }}>
          {INDUSTRIES.map((ind) => {
            const sel = selected === ind.name;
            return (
              <span
                key={ind.name}
                onClick={() => setSelected(ind.name)}
                style={{ display: 'inline-flex', alignItems: 'center', gap: 8, padding: '8px 14px', background: sel ? '#e8f0fe' : '#fff', border: `1.5px solid ${sel ? '#3b7ff2' : '#dadce0'}`, borderRadius: 10, fontSize: 14, color: sel ? '#1a56c4' : '#3c4043', fontWeight: 500, cursor: 'pointer' }}
              >
                <span style={{ width: 11, height: 11, borderRadius: 3, background: ind.color, flexShrink: 0 }} />
                {ind.name}
              </span>
            );
          })}
        </div>

        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 7, marginBottom: 28, fontSize: 11.5, color: '#9aa0a6' }}>
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" style={{ flexShrink: 0 }}>
            <circle cx="12" cy="12" r="9" stroke="#9aa0a6" strokeWidth="2" />
            <path d="M12 8v5" stroke="#9aa0a6" strokeWidth="2" strokeLinecap="round" />
            <circle cx="12" cy="16.5" r="1" fill="#9aa0a6" />
          </svg>
          현재 상황은 가입 후 매칭할 때 골라요
        </div>

        <label style={{ display: 'flex', alignItems: 'flex-start', gap: 9, marginBottom: 18, fontSize: 12.5, color: '#3c4043', cursor: 'pointer' }}>
          <input
            type="checkbox"
            checked={agreedToTerms}
            onChange={e => setAgreedToTerms(e.target.checked)}
            style={{ width: 16, height: 16, marginTop: 1, accentColor: '#3b7ff2', flexShrink: 0, cursor: 'pointer' }}
          />
          <span>
            <span style={{ color: '#ea4c4c' }}>[필수] </span>
            약관 및 개인정보 최소 수집(이메일·비밀번호·산업군)에 동의합니다
          </span>
        </label>

        {error && <div style={{ fontSize: 12, color: '#ea4c4c', marginBottom: 12 }}>{error}</div>}

        <button
          onClick={handleSignup}
          disabled={!canSubmit}
          style={{ width: '100%', height: 48, background: canSubmit ? '#3b7ff2' : '#9aa0a6', color: '#fff', borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 15, fontWeight: 600, border: 'none', cursor: canSubmit ? 'pointer' : 'default' }}
        >
          {loading ? '가입 중...' : '가입하고 매칭 시작'}
        </button>
        <div style={{ textAlign: 'center', fontSize: 13, color: '#5f6368', marginTop: 12 }}>
          이미 계정이 있으신가요?{' '}
          <Link href="/login" style={{ color: '#3b7ff2', fontWeight: 600, textDecoration: 'none' }}>로그인</Link>
        </div>
      </div>
    </div>
  );
}
