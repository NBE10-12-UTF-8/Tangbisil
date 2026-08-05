'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { apiGetNotifications, isLoggedIn, type MatchNotification } from '@/lib/api';

const AFTER_KEY = 'tangbisil_notification_after';
const PROMPT_DISMISSED_KEY = 'tangbisil_notif_prompt_dismissed';
const POLL_MS = 5000;
const TOAST_DURATION_MS = 10000; // 알림 유지 시간 (10초)
const FADE_DURATION_MS = 500; // 페이드 아웃 애니메이션 시간 (0.5초)

function hasNotificationApi() {
  return typeof window !== 'undefined' && 'Notification' in window;
}

// 탭이 백그라운드/비활성일 때만 OS 알림을 띄운다
function notifyOs(n: MatchNotification, onClickNavigate: () => void) {
  if (!hasNotificationApi() || Notification.permission !== 'granted') return;
  if (document.visibilityState === 'visible' && document.hasFocus()) return;

  const os = new Notification('탕비실', { body: n.message, icon: '/favicon.ico', tag: n.roomId });
  os.onclick = () => {
    window.focus();
    onClickNavigate();
    os.close();
  };
}

export function NotificationListener() {
  const router = useRouter();
  const [queue, setQueue] = useState<MatchNotification[]>([]);
  const [showPermissionPrompt, setShowPermissionPrompt] = useState(false);
  // 페이드 아웃 상태 관리: 현재 알림이 사라지는 중인지 여부
  const [isFadingOut, setIsFadingOut] = useState(false);
  const afterRef = useRef<string | null>(null);

  useEffect(() => {
    afterRef.current = localStorage.getItem(AFTER_KEY);

    if (
      hasNotificationApi() &&
      Notification.permission === 'default' &&
      !localStorage.getItem(PROMPT_DISMISSED_KEY) &&
      isLoggedIn()
    ) {
      setShowPermissionPrompt(true);
    }

    const tick = async () => {
      if (!isLoggedIn()) return;
      try {
        const notifications = await apiGetNotifications(afterRef.current ?? undefined);
        if (notifications && notifications.length > 0) {
          const latest = notifications.reduce((max, n) => (n.createdAt > max ? n.createdAt : max), notifications[0].createdAt);
          afterRef.current = latest;
          localStorage.setItem(AFTER_KEY, latest);
          setQueue(q => [...q, ...notifications]);
          notifications.forEach(n => {
            if (n.type === 'MATCH_SUCCESS' && n.roomId) {
              notifyOs(n, () => router.push(`/chat/${n.roomId}`));
            }
          });
        } else if (!afterRef.current) {
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
  }, [router]);

  const current = queue[0];

  // 즉시 삭제용 함수 (버튼 클릭 시)
  const dismiss = () => {
    setIsFadingOut(false); // 페이드 상태 초기화
    setQueue(q => q.slice(1));
  };

  // '스르륵' 사라지게 하는 함수
  const startFadeOut = () => {
    setIsFadingOut(true); // 페이드 아웃 애니메이션 시작
    // 애니메이션 시간(0.5초) 후에 실제로 큐에서 삭제
    setTimeout(() => {
      dismiss();
    }, FADE_DURATION_MS);
  };

  // 현재 노출 중인 알림(current)이 바뀔 때마다 10초 타이머 작동
  useEffect(() => {
    if (!current) return;

    // 새로운 알림이 뜨면 페이드 상태 초기화
    setIsFadingOut(false);

    // 10초 뒤에 페이드 아웃 시작
    const timer = setTimeout(() => {
      startFadeOut();
    }, TOAST_DURATION_MS);

    return () => clearTimeout(timer);
  }, [current]);

  const requestPermission = () => {
    setShowPermissionPrompt(false);
    Notification.requestPermission();
  };
  const dismissPrompt = () => {
    setShowPermissionPrompt(false);
    localStorage.setItem(PROMPT_DISMISSED_KEY, '1');
  };

  // CSS 애니메이션 정의 (전역 <style> 태그 사용)
  const animationStyles = `
    @keyframes tb-fade-in {
      from { opacity: 0; transform: translate(-50%, -20px); }
      to { opacity: 1; transform: translate(-50%, 0); }
    }
    @keyframes tb-fade-out {
      from { opacity: 1; transform: translate(-50%, 0); }
      to { opacity: 0; transform: translate(-50%, -20px); }
    }
    .tb-toast {
      animation: tb-fade-in 0.4s ease-out forwards;
    }
    .tb-toast.fade-out {
      animation: tb-fade-out ${FADE_DURATION_MS}ms ease-in forwards;
    }
  `;

  return (
    <>
      <style>{animationStyles}</style>

      {showPermissionPrompt && (
        <div style={{ position: 'fixed', top: 24, left: '50%', transform: 'translateX(-50%)', display: 'flex', alignItems: 'center', gap: 12, background: '#202124', color: '#fff', borderRadius: 10, padding: '12px 16px', fontSize: 13.5, fontWeight: 500, boxShadow: '0 4px 16px rgba(0,0,0,.2)', zIndex: 300, transition: 'all 0.3s' }}>
          <span>🔔 다른 탭을 보고 있어도 매칭 성사를 알려드릴까요?</span>
          <button
            onClick={requestPermission}
            style={{ background: '#3b7ff2', color: '#fff', border: 'none', borderRadius: 6, padding: '5px 10px', fontSize: 12.5, fontWeight: 600, cursor: 'pointer', flexShrink: 0 }}
          >
            알림 받기
          </button>
          <button onClick={dismissPrompt} style={{ background: 'none', border: 'none', color: '#9aa0a6', cursor: 'pointer', fontSize: 14, flexShrink: 0, padding: 0 }}>✕</button>
        </div>
      )}

      {current && (
        <div
          // 기본 클래스와 페이드 아웃 클래스를 조건부로 적용
          className={`tb-toast ${isFadingOut ? 'fade-out' : ''}`}
          style={{
            position: 'fixed',
            top: showPermissionPrompt ? 76 : 24,
            left: '50%',
            transform: 'translateX(-50%)',
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            background: '#202124',
            color: '#fff',
            borderRadius: 10,
            padding: '12px 16px',
            fontSize: 13.5,
            fontWeight: 500,
            boxShadow: '0 4px 16px rgba(0,0,0,.2)',
            zIndex: 300,
            pointerEvents: isFadingOut ? 'none' : 'auto', // 사라지는 중에는 클릭 방지
          }}
        >
          <span>🔔 {current.message}</span>
          {current.type === 'MATCH_SUCCESS' && current.roomId && (
            <button
              onClick={() => { dismiss(); router.push(`/chat/${current.roomId}`); }}
              style={{ background: '#3b7ff2', color: '#fff', border: 'none', borderRadius: 6, padding: '5px 10px', fontSize: 12.5, fontWeight: 600, cursor: 'pointer', flexShrink: 0 }}
            >
              대화하러 가기
            </button>
          )}
          {/* X 버튼 클릭 시에는 즉시 삭제 */}
          <button onClick={dismiss} style={{ background: 'none', border: 'none', color: '#9aa0a6', cursor: 'pointer', fontSize: 14, flexShrink: 0, padding: 0 }}>✕</button>
        </div>
      )}
    </>
  );
}