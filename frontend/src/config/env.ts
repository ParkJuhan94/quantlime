// import.meta.env를 위한 타입 있는 래퍼. Vite는 VITE_ 접두사가 붙은
// 값만 클라이언트 번들에 노출한다(그 외 .env 값은 서버 전용).
export const env = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  tossPaymentsClientKey: import.meta.env.VITE_TOSS_PAYMENTS_CLIENT_KEY ?? '',
}
