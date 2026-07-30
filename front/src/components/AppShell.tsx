'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useState, useEffect } from 'react';
import { isLoggedIn as checkIsLoggedIn } from '@/lib/api';

export function AppShell({ children, topAlign = false }: { children: React.ReactNode; topAlign?: boolean }) {
  const pathname = usePathname();
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  useEffect(() => { setIsLoggedIn(checkIsLoggedIn()); }, [pathname]);

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: '#fff', fontFamily: "Arial, 'Helvetica Neue', sans-serif" }}>
      <div style={{ height: 50, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 18, padding: '0 22px' }}>
        <span style={{ fontSize: 13, color: '#202124', cursor: 'pointer' }}>메일</span>
        <span style={{ fontSize: 13, color: '#202124', cursor: 'pointer' }}>이미지</span>
        {isLoggedIn ? (
          <Link href="/me" style={{ textDecoration: 'none' }}>
            <div style={{ width: 30, height: 30, borderRadius: '50%', background: '#3b7ff2', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, fontWeight: 700, cursor: 'pointer' }}>나</div>
          </Link>
        ) : (
          <Link href="/login" style={{ textDecoration: 'none', padding: '7px 15px', background: '#3b7ff2', color: '#fff', borderRadius: 6, fontSize: 13, fontWeight: 600 }}>로그인</Link>
        )}
      </div>

      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: topAlign ? 'flex-start' : 'center', paddingTop: topAlign ? '22vh' : 0, paddingBottom: 40, paddingLeft: 16, paddingRight: 16, boxSizing: 'border-box', width: '100%' }}>
        {children}
      </div>

      <div style={{ flexShrink: 0, background: '#f2f2f2', borderTop: '1px solid #e4e4e4', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 24px' }}>
        <span style={{ fontSize: 13, color: '#70757a' }}>대한민국</span>
        <div style={{ display: 'flex', gap: 22 }}>
          {['정보', '약관', '설정'].map(t => (
            <span key={t} style={{ fontSize: 13, color: '#70757a', cursor: 'pointer' }}>{t}</span>
          ))}
        </div>
      </div>
    </div>
  );
}
