'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { apiSendPasswordReset, apiConfirmPasswordReset, isValidEmail } from '@/lib/api';

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

export default function ForgotPasswordPage() {
  const router = useRouter();
  const [step, setStep] = useState<'email' | 'reset'>('email');

  const [email, setEmail] = useState('');
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState('');
  const [sendNotice, setSendNotice] = useState('');

  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [resetting, setResetting] = useState(false);
  const [resetError, setResetError] = useState('');

  const emailOk = isValidEmail(email);
  const pwOk = newPassword.length >= 4;
  const canReset = code.length === 6 && pwOk && newPassword === confirm && !resetting;

  const sendCode = async () => {
    if (!emailOk || sending) return;
    setSendError('');
    setSending(true);
    try {
      await apiSendPasswordReset(email);
      setSendNotice('인증코드를 이메일로 보냈어요. 받은 코드를 아래에 입력해주세요');
      setStep('reset');
    } catch (e: unknown) {
      setSendError((e as Error)?.message ?? '재설정 코드 발송에 실패했어요');
    } finally {
      setSending(false);
    }
  };

  const resendCode = async () => {
    if (sending) return;
    setSendError('');
    setSending(true);
    try {
      await apiSendPasswordReset(email);
      setSendNotice('인증코드를 다시 보냈어요');
    } catch (e: unknown) {
      setSendError((e as Error)?.message ?? '재발송에 실패했어요');
    } finally {
      setSending(false);
    }
  };

  const handleReset = async () => {
    if (!canReset) return;
    setResetError('');
    setResetting(true);
    try {
      await apiConfirmPasswordReset(email, code, newPassword);
      router.replace('/login?reset=success');
    } catch (e: unknown) {
      setResetError((e as Error)?.message ?? '비밀번호 재설정에 실패했어요');
    } finally {
      setResetting(false);
    }
  };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', background: '#fff', fontFamily: "Arial, 'Helvetica Neue', sans-serif", padding: '24px 16px', boxSizing: 'border-box' }}>
      <Link href="/" style={{ textDecoration: 'none' }}><TangbisilLogo size={42} /></Link>
      <div style={{ fontSize: 14, color: '#5f6368', marginBottom: 26, marginTop: 8, textAlign: 'center' }}>
        비밀번호를 재설정하세요
      </div>

      <div style={{ width: '100%', maxWidth: 400, border: '1px solid #dadce0', borderRadius: 14, padding: '30px 24px 26px', boxSizing: 'border-box' }}>
        <div style={{ fontSize: 20, color: '#202124', fontWeight: 500, marginBottom: 22 }}>비밀번호 찾기</div>

        <div style={{ fontSize: 12, color: '#5f6368', marginBottom: 6 }}>이메일</div>
        <input
          type="email"
          value={email}
          onChange={e => setEmail(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && step === 'email' && sendCode()}
          placeholder="work@company.com"
          disabled={step === 'reset'}
          style={{ width: '100%', height: 46, border: '1px solid #dadce0', borderRadius: 8, padding: '0 14px', fontSize: 15, color: '#202124', marginBottom: 16, outline: 'none', boxSizing: 'border-box', background: step === 'reset' ? '#f8f9fa' : '#fff' }}
        />

        {step === 'email' && (
          <>
            {sendError && <div style={{ fontSize: 12, color: '#ea4c4c', marginBottom: 12 }}>{sendError}</div>}
            <button
              onClick={sendCode}
              disabled={!emailOk || sending}
              style={{ width: '100%', height: 46, background: emailOk && !sending ? '#3b7ff2' : '#9aa0a6', color: '#fff', borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 15, fontWeight: 600, border: 'none', cursor: emailOk && !sending ? 'pointer' : 'default' }}
            >
              {sending ? '발송 중...' : '재설정 코드 받기'}
            </button>
          </>
        )}

        {step === 'reset' && (
          <>
            {sendNotice && <div style={{ fontSize: 12, color: '#34a06b', marginBottom: 16 }}>{sendNotice}</div>}

            <div style={{ fontSize: 12, color: '#5f6368', marginBottom: 6 }}>인증코드</div>
            <input
              type="text"
              inputMode="numeric"
              maxLength={6}
              value={code}
              onChange={e => setCode(e.target.value.replace(/\D/g, ''))}
              placeholder="6자리 인증코드"
              style={{ width: '100%', height: 46, border: '1px solid #dadce0', borderRadius: 8, padding: '0 14px', fontSize: 16, color: '#202124', marginBottom: 16, outline: 'none', boxSizing: 'border-box', letterSpacing: 2 }}
            />

            <div style={{ fontSize: 12, color: '#5f6368', marginBottom: 6 }}>새 비밀번호</div>
            <input
              type="password"
              value={newPassword}
              onChange={e => setNewPassword(e.target.value)}
              placeholder="••••••••"
              style={{ width: '100%', height: 46, border: '1px solid #dadce0', borderRadius: 8, padding: '0 14px', fontSize: 16, color: '#202124', marginBottom: 12, outline: 'none', boxSizing: 'border-box', letterSpacing: 2 }}
            />

            <div style={{ fontSize: 12, color: '#5f6368', marginBottom: 6 }}>새 비밀번호 확인</div>
            <input
              type="password"
              value={confirm}
              onChange={e => setConfirm(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleReset()}
              placeholder="••••••••"
              style={{ width: '100%', height: 46, border: `1px solid ${confirm && confirm !== newPassword ? '#ea4c4c' : '#dadce0'}`, borderRadius: 8, padding: '0 14px', fontSize: 16, color: '#202124', marginBottom: 8, outline: 'none', boxSizing: 'border-box', letterSpacing: 2 }}
            />
            <div style={{ fontSize: 11.5, color: pwOk ? '#34a06b' : '#9aa0a6', marginBottom: 16 }}>
              {pwOk ? '✓ 4자 이상 · 사용 가능한 비밀번호예요' : '비밀번호는 4자 이상으로 입력해주세요'}
            </div>

            {resetError && <div style={{ fontSize: 12, color: '#ea4c4c', marginBottom: 12 }}>{resetError}</div>}

            <button
              onClick={handleReset}
              disabled={!canReset}
              style={{ width: '100%', height: 46, background: canReset ? '#3b7ff2' : '#9aa0a6', color: '#fff', borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 15, fontWeight: 600, border: 'none', cursor: canReset ? 'pointer' : 'default', marginBottom: 12 }}
            >
              {resetting ? '변경 중...' : '비밀번호 재설정'}
            </button>
            <div style={{ textAlign: 'center', fontSize: 12.5, color: '#3b7ff2', cursor: sending ? 'default' : 'pointer' }} onClick={resendCode}>
              {sending ? '발송 중...' : '코드 다시 받기'}
            </div>
          </>
        )}

        <div style={{ textAlign: 'center', fontSize: 13, color: '#5f6368', marginTop: 18 }}>
          <Link href="/login" style={{ color: '#3b7ff2', fontWeight: 600, textDecoration: 'none' }}>로그인으로 돌아가기</Link>
        </div>
      </div>
    </div>
  );
}
