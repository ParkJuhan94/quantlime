import { initializeApp } from 'firebase/app'
import { getMessaging, getToken, isSupported, onMessage, type Messaging, type MessagePayload } from 'firebase/messaging'
import { env } from '../config/env'

const firebaseConfig = {
  apiKey: env.firebase.apiKey,
  projectId: env.firebase.projectId,
  messagingSenderId: env.firebase.messagingSenderId,
  appId: env.firebase.appId,
}

let messagingPromise: Promise<Messaging | null> | null = null

// 브라우저가 FCM(서비스워커+Push API)을 지원하지 않으면(구형 Safari 등)
// getMessaging()이 그대로 던지므로 isSupported()로 먼저 확인한다.
async function resolveMessaging(): Promise<Messaging | null> {
  if (!env.firebase.apiKey || !(await isSupported())) {
    return null
  }
  const app = initializeApp(firebaseConfig)
  return getMessaging(app)
}

function getMessagingInstance(): Promise<Messaging | null> {
  messagingPromise ??= resolveMessaging()
  return messagingPromise
}

let swRegistrationPromise: Promise<ServiceWorkerRegistration | null> | null = null

// getToken()에 맡기면 Firebase가 "/firebase-cloud-messaging-push-scope"
// 스코프로 SW를 알아서 등록하지만, 포그라운드 알림도 그 SW의
// showNotification으로 띄우기 위해(showPushNotification) 등록을 직접
// 소유한다.
async function resolveServiceWorkerRegistration(): Promise<ServiceWorkerRegistration | null> {
  if (!('serviceWorker' in navigator)) {
    return null
  }
  const registration = await navigator.serviceWorker.register('/firebase-messaging-sw.js')
  await waitUntilActive(registration)
  return registration
}

// register() 직후엔 워커가 아직 installing 상태일 수 있고, 이때
// getToken()(내부적으로 pushManager.subscribe)을 부르면 "no active Service
// Worker" AbortError가 난다(사이트 데이터를 지우고 처음 등록할 때 재현됨).
function waitUntilActive(registration: ServiceWorkerRegistration): Promise<void> {
  if (registration.active) {
    return Promise.resolve()
  }
  const worker = registration.installing ?? registration.waiting
  if (!worker) {
    return Promise.resolve()
  }
  return new Promise((resolve) => {
    worker.addEventListener('statechange', function handleStateChange() {
      if (worker.state === 'activated') {
        worker.removeEventListener('statechange', handleStateChange)
        resolve()
      }
    })
  })
}

function getServiceWorkerRegistration(): Promise<ServiceWorkerRegistration | null> {
  swRegistrationPromise ??= resolveServiceWorkerRegistration()
  return swRegistrationPromise
}

/**
 * 알림 권한을 요청하고(이미 허용/거부된 상태면 재요청 없이 즉시 반환)
 * FCM 토큰을 발급받는다. 미지원 브라우저/설정 누락/권한 거부 시 null.
 * 이미 권한이 허용된 상태에서 다시 호출하면(로그아웃 시 토큰 재조회 등)
 * 동일 토큰을 그대로 반환한다.
 */
export async function requestFcmToken(): Promise<string | null> {
  const messaging = await getMessagingInstance()
  if (!messaging || !env.firebase.vapidKey) {
    return null
  }
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    return null
  }
  const swRegistration = await getServiceWorkerRegistration()
  return getToken(messaging, {
    vapidKey: env.firebase.vapidKey,
    serviceWorkerRegistration: swRegistration ?? undefined,
  })
}

/**
 * 탭이 포그라운드(보이는 상태)일 때 도착하는 푸시를 구독한다 - 이 경우
 * 서비스워커의 onBackgroundMessage는 호출되지 않고 여기로만 전달된다.
 * 반환된 함수를 호출하면 구독을 해제한다.
 */
export async function listenForForegroundMessages(
  onReceive: (payload: MessagePayload) => void,
): Promise<() => void> {
  const messaging = await getMessagingInstance()
  if (!messaging) {
    return () => {}
  }
  return onMessage(messaging, onReceive)
}

/**
 * 포그라운드 푸시를 백그라운드와 같은 모양의 OS 알림으로 띄운다. 페이지의
 * new Notification()은 macOS 크롬에서 해당 탭이 포커스된 상태면 뜨지 않아
 * (2026-09-24 실제 확인) SW의 showNotification을 쓴다 - 클릭 처리도 SW의
 * notificationclick 하나로 통일된다. 옵션은 firebase-messaging-sw.js의
 * onBackgroundMessage와 맞출 것.
 */
export async function showPushNotification(data: Record<string, string> | undefined): Promise<void> {
  const registration = await getServiceWorkerRegistration()
  if (!registration || Notification.permission !== 'granted') {
    return
  }
  await registration.showNotification(data?.title ?? '퀀트라임', {
    body: data?.body ?? '',
    icon: '/favicon.svg',
    data: { linkUrl: data?.linkUrl },
    requireInteraction: true,
  })
}
