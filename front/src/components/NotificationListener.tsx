'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { apiGetNotifications, isLoggedIn, type MatchNotification } from '@/lib/api';

const AFTER_KEY = 'tangbisil_notification_after';
const POLL_MS = 5000;

// 앱 전역(RootLayout)에 한 번만 마운트되어, 어떤 페이지에 있든 매칭 성사 등
// 실시간 알림을 폴링한다. AppShell은 페이지마다 다시 마운트되므로 여기 두면 안 된다.
export function NotificationListener() {
  const router = useRouter();
  const [queue, setQueue] = useState<MatchNotification[]>([]);
  const afterRef = useRef<string | null>(null);

  useEffect(() => {
    afterRef.current = localStorage.getItem(AFTER_KEY);

    const tick = async () => {
      if (!isLoggedIn()) return;
      try {
        const notifications = await apiGetNotifications(afterRef.current ?? undefined);
        if (notifications && notifications.length > 0) {
          const latest = notifications.reduce((max, n) => (n.createdAt > max ? n.createdAt : max), notifications[0].createdAt);
          afterRef.current = latest;
          localStorage.setItem(AFTER_KEY, latest);
          setQueue(q => [...q, ...notifications]);
        } else if (!afterRef.current) {
          // 첫 폴링이었고 신규 알림도 없었다면, 과거 알림을 재생하지 않도록 지금 시각을 커서로 고정
          const now = new Date().toISOString();
          afterRef.current = now;
          localStorage.setItem(AFTER_KEY, now);
        }
      } catch {
        /* 다음 폴링에서 재시도 */
      }
    };

    tick();
    const id = setInterval(tick, POLL_MS);
    return () => clearInterval(id);
  }, []);

  if (queue.length === 0) return null;
  const current = queue[0];
  const dismiss = () => setQueue(q => q.slice(1));

  return (
    <div style={{ position: 'fixed', top: 24, left: '50%', transform: 'translateX(-50%)', display: 'flex', alignItems: 'center', gap: 12, background: '#202124', color: '#fff', borderRadius: 10, padding: '12px 16px', fontSize: 13.5, fontWeight: 500, boxShadow: '0 4px 16px rgba(0,0,0,.2)', zIndex: 300 }}>
      <span>🔔 {current.message}</span>
      {current.type === 'MATCH_SUCCESS' && current.roomId && (
        <button
          onClick={() => { dismiss(); router.push(`/chat/${current.roomId}`); }}
          style={{ background: '#3b7ff2', color: '#fff', border: 'none', borderRadius: 6, padding: '5px 10px', fontSize: 12.5, fontWeight: 600, cursor: 'pointer', flexShrink: 0 }}
        >
          대화하러 가기
        </button>
      )}
      <button onClick={dismiss} style={{ background: 'none', border: 'none', color: '#9aa0a6', cursor: 'pointer', fontSize: 14, flexShrink: 0, padding: 0 }}>✕</button>
    </div>
  );
}
