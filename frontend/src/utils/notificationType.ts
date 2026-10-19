// 백엔드 NotificationType(문자열) -> 알림 목록의 종류 라벨과 색. 색은 중요도 순으로 아껴 쓴다:
// 결제 실패(빨강)·구독 만료(노랑)는 조치가 필요한 알림, 랭킹/사분면은 정보성(파랑), 나머지는 중립.
// 앱에서 빨강·파랑은 주가 상승·하락 색이라(§7) 알림에서도 이 매핑 밖으로 늘리지 않는다.
export type NotificationTone = 'danger' | 'warn' | 'ok' | 'info' | 'dark' | 'neutral'

export interface NotificationTypeStyle {
  label: string
  tone: NotificationTone
}

export const NOTIFICATION_TONE_CLASSES: Record<NotificationTone, string> = {
  danger: 'bg-red-100 text-red-700',
  warn: 'bg-amber-100 text-amber-800',
  ok: 'bg-green-100 text-green-800',
  info: 'bg-blue-100 text-blue-700',
  dark: 'bg-gray-900 text-white',
  neutral: 'bg-gray-100 text-gray-700',
}

export const NOTIFICATION_TYPE_STYLES: Record<string, NotificationTypeStyle> = {
  ADMIN_NOTICE: { label: '공지', tone: 'dark' },
  PAYMENT_SUCCESS: { label: '결제 완료', tone: 'ok' },
  PAYMENT_FAILED: { label: '결제 실패', tone: 'danger' },
  SUBSCRIPTION_EXPIRED: { label: '구독 만료', tone: 'warn' },
  SCORE_RANKING_GLOBAL: { label: '랭킹', tone: 'info' },
  SCORE_RANKING_WATCHLIST: { label: '관심 랭킹', tone: 'info' },
  QUADRANT_CHANGE: { label: '사분면', tone: 'info' },
  SYSTEM: { label: '시스템', tone: 'neutral' },
}

// 백엔드에 타입이 새로 생겨도 화면이 깨지지 않도록 모르는 값은 중립 라벨로 보여준다.
const FALLBACK_STYLE: NotificationTypeStyle = { label: '알림', tone: 'neutral' }

export function getNotificationTypeStyle(type: string): NotificationTypeStyle {
  return NOTIFICATION_TYPE_STYLES[type] ?? FALLBACK_STYLE
}
