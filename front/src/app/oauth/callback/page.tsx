'use client';

import { Suspense, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import {
  apiGetMe,
  apiGetActiveRoom,
  setAdmin,
  markSession,
  SUSPENDED_STORAGE_KEY,
} from '@/lib/api';

function OAuthCallbackInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [error, setError] = useState('');
  const hasFetched = useRef(false);

  useEffect(() => {
    const errorParam = searchParams.get('error');
    if (errorParam) {
      setError(`인증 실패: ${searchParams.get('error_description') || errorParam}`);
      return;
    }

    // 백엔드가 로그인 성공 시점에 바로 쿠키를 심어주므로, 여기서는 code 교환 없이
    // 쿠키 인증으로 내 정보(/me)를 조회해 role/온보딩 여부를 판단한다.
    if (hasFetched.current) return;
    hasFetched.current = true;

    (async () => {
      try {
        const me = await apiGetMe();
        markSession();

        if (me.role === 'ADMIN') {
          setAdmin();
          router.replace('/admin/stats');
          return;
        }

        if (!me.industry) {
          router.replace('/me?onboarding=true');
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
      } catch (err) {
        console.error('OAuth Callback Error:', err);
        setError('소셜 로그인에 실패했어요');
      }
    })();
  }, [searchParams, router]);

  if (error) {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 16, fontFamily: "Arial, 'Helvetica Neue', sans-serif" }}>
        <div style={{ fontSize: 14, color: '#ea4c4c' }}>{error}</div>
        <Link href="/login" style={{ color: '#3b7ff2', fontWeight: 600, textDecoration: 'none', fontSize: 14 }}>
          로그인으로 돌아가기
        </Link>
      </div>
    );
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: "Arial, 'Helvetica Neue', sans-serif" }}>
      <span style={{ color: '#9aa0a6', fontSize: 13 }}>로그인 처리 중...</span>
    </div>
  );
}

export default function OAuthCallbackPage() {
  return (
    <Suspense
      fallback={
        <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: "Arial, 'Helvetica Neue', sans-serif" }}>
          <span style={{ color: '#9aa0a6', fontSize: 13 }}>로그인 처리 중...</span>
        </div>
      }
    >
      <OAuthCallbackInner />
    </Suspense>
  );
}
