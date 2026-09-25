// 프론트 앱 본체가 쓰는 firebase npm 패키지 버전(frontend/package.json)과
// 맞춰둔다 - SW와 페이지가 같은 SDK의 내부 메시지 포맷(포그라운드 릴레이용
// postMessage)을 주고받으므로, 메이저 버전이 어긋나면 호환이 보장되지 않는다.
importScripts('https://www.gstatic.com/firebasejs/12.19.0/firebase-app-compat.js')
importScripts('https://www.gstatic.com/firebasejs/12.19.0/firebase-messaging-compat.js')

// Firebase 콘솔 > 프로젝트 설정 > 일반 > "내 앱"의 웹 설정값.
// 서비스워커는 정적 파일 그대로 서빙되어 Vite의 import.meta.env(VITE_*)를
// 읽지 못한다 - 공식 Firebase 가이드도 이 파일엔 값을 직접 적어 넣는
// 방식을 쓴다(apiKey 등 Firebase 웹 설정값은 시크릿이 아니다). 프론트 앱
// 본체(src/notification/firebaseClient.ts)의 VITE_FIREBASE_* 값과 반드시
// 같은 Firebase 프로젝트로 맞출 것.
firebase.initializeApp({
  apiKey: 'AIzaSyCR22SzvRVNZ676FeDwThw_VoYbxd0Slws',
  projectId: 'quantlime',
  messagingSenderId: '551648031389',
  appId: '1:551648031389:web:c4fb3151f76b77b5055a74',
})

const messaging = firebase.messaging()

// 새 버전의 서비스워커를 즉시 활성화한다 - 기본 동작은 기존 탭을 전부
// 닫아야 새 SW가 활성화돼, 이 파일을 수정할 때마다 "안 바뀐 것 같은"
// 혼란을 준다.
self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (event) => event.waitUntil(clients.claim()))

// 백그라운드(보이는 탭이 없을 때) 수신 - 포그라운드일 땐 Firebase가 이
// 핸들러 대신 페이지의 onMessage로 넘기고, 페이지가 같은 모양으로
// registration.showNotification을 호출한다(firebaseClient.ts
// showPushNotification - 옵션을 바꾸면 양쪽을 같이 맞출 것). 백엔드는
// data-only로 보내므로(FcmPushService.sendChunk 주석 참고) 전부 data에서 읽는다.
messaging.onBackgroundMessage((payload) => {
  const title = payload.data?.title ?? '퀀트라임'
  self.registration.showNotification(title, {
    body: payload.data?.body ?? '',
    icon: '/favicon.svg',
    data: { linkUrl: payload.data?.linkUrl },
    // 클릭 전까지 유지 - macOS에선 크롬의 "Alerts" 알림 항목(시스템 설정에
    // 같은 이름 "Google Chrome"으로 하나 더 보임)이 허용돼 있어야 뜬다.
    requireInteraction: true,
  })
})

// 알림 클릭 시 linkUrl로 이동한다(핸들러가 없으면 알림만 닫히고 아무 데도
// 안 감). 열려있는 퀀트라임 탭이 있으면 그 탭을 먼저 포커스하고, 이동은
// 페이지에 메시지로 맡겨 React Router로 처리하게 한다(useFcmRegistration의
// NOTIFICATION_NAVIGATE 리스너). 처음엔 WindowClient.navigate() 후 focus()
// 순서였는데 탭이 앞으로만 오고 이동은 안 됐다 - navigate()는 이 SW가
// 제어중인 탭에만 동작하고(강력 새로고침 등으로 열린 탭은 거부), focus()는
// 크롬이 알림 클릭 직후 잠깐만 허용해서 뒤로 미루면 안 된다.
self.addEventListener('notificationclick', (event) => {
  const linkUrl = event.notification.data?.linkUrl
  event.notification.close()
  if (!linkUrl) {
    return
  }
  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windowClients) => {
      const appClient = windowClients.find((client) => client.url.startsWith(self.location.origin))
      if (!appClient) {
        return clients.openWindow(new URL(linkUrl, self.location.origin).href)
      }
      appClient.postMessage({ type: 'NOTIFICATION_NAVIGATE', linkUrl })
      return appClient.focus()
    }),
  )
})
