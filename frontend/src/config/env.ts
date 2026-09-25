// import.meta.env를 위한 타입 있는 래퍼. Vite는 VITE_ 접두사가 붙은
// 값만 클라이언트 번들에 노출한다(그 외 .env 값은 서버 전용).
export const env = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  tossPaymentsClientKey: import.meta.env.VITE_TOSS_PAYMENTS_CLIENT_KEY ?? '',
  // 알림(Web Push) - Firebase 콘솔 > 프로젝트 설정 > 일반의 웹 앱 설정값.
  // 미설정 시 useFcmRegistration/firebaseClient가 조용히 건너뛴다.
  firebase: {
    apiKey: import.meta.env.VITE_FIREBASE_API_KEY ?? '',
    projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID ?? '',
    messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID ?? '',
    appId: import.meta.env.VITE_FIREBASE_APP_ID ?? '',
    vapidKey: import.meta.env.VITE_FIREBASE_VAPID_KEY ?? '',
  },
}
