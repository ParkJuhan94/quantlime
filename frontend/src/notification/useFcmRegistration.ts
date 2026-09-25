import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/useAuth'
import { registerFcmToken } from '../api/notification'
import { listenForForegroundMessages, requestFcmToken, showPushNotification } from './firebaseClient'
import { queryKeys } from '../hooks/queryKeys'

/**
 * 로그인 상태가 될 때마다(최초 로그인, 새로고침 후 세션 복원 등) 알림
 * 권한을 요청하고 발급받은 토큰을 서버에 등록한다. Firebase 설정이 아직
 * 없거나(VITE_FIREBASE_* 미설정) 권한을 거부하면 스킵된다 - 인앱 알림함은
 * 푸시 토큰 등록 여부와 무관하게 동작한다.
 *
 * 탭이 포그라운드일 때 도착하는 푸시는 서비스워커의 onBackgroundMessage가
 * 아니라 여기(onMessage)로 오므로, 같은 모양의 알림을 직접 띄운다.
 */
export function useFcmRegistration() {
  const { isAuthenticated } = useAuth()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  // 알림 클릭 시 서비스워커가 이 탭을 포커스한 뒤 보내는 이동 요청
  // (firebase-messaging-sw.js notificationclick 참고).
  useEffect(() => {
    if (!('serviceWorker' in navigator)) {
      return
    }
    function handleMessage(event: MessageEvent) {
      if (event.data?.type === 'NOTIFICATION_NAVIGATE' && typeof event.data.linkUrl === 'string') {
        navigate(event.data.linkUrl)
      }
    }
    navigator.serviceWorker.addEventListener('message', handleMessage)
    return () => navigator.serviceWorker.removeEventListener('message', handleMessage)
  }, [navigate])

  useEffect(() => {
    if (!isAuthenticated) {
      return
    }
    let cancelled = false
    let unsubscribeForeground: (() => void) | undefined

    void (async () => {
      try {
        const token = await requestFcmToken()
        if (!token || cancelled) {
          return
        }
        await registerFcmToken({ token, deviceInfo: navigator.userAgent })
        if (cancelled) {
          return
        }
        unsubscribeForeground = await listenForForegroundMessages((payload) => {
          void showPushNotification(payload.data)
          // 배지 카운트를 60초 폴링까지 기다리지 않고 바로 갱신한다.
          void queryClient.invalidateQueries({ queryKey: queryKeys.notificationsAll })
        })
        // 리스너 등록을 기다리는 사이 언마운트됐으면 바로 해제한다.
        if (cancelled) {
          unsubscribeForeground()
        }
      } catch (error) {
        // 권한 거부/미지원 브라우저는 위에서 null로 걸러지므로 여기로 오는
        // 건 예상 밖 실패뿐이다 - 조용히 삼키면 원인 추적이 불가능했다.
        console.warn('[FCM] 푸시 알림 등록 실패:', error)
      }
    })()

    return () => {
      cancelled = true
      unsubscribeForeground?.()
    }
  }, [isAuthenticated, queryClient])
}
