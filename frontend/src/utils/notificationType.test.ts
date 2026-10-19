import { describe, expect, it } from 'vitest'
import { getNotificationTypeStyle, NOTIFICATION_TONE_CLASSES, NOTIFICATION_TYPE_STYLES } from './notificationType'

// 백엔드 NotificationType 8종과 이 매핑이 어긋나면 새 타입 알림이 라벨 없이 나온다 - 개수와 색 정의를 고정한다
describe('notificationType', () => {
  it('백엔드 NotificationType 8종을 모두 정의한다', () => {
    expect(Object.keys(NOTIFICATION_TYPE_STYLES).sort()).toEqual(
      [
        'ADMIN_NOTICE',
        'PAYMENT_FAILED',
        'PAYMENT_SUCCESS',
        'QUADRANT_CHANGE',
        'SCORE_RANKING_GLOBAL',
        'SCORE_RANKING_WATCHLIST',
        'SUBSCRIPTION_EXPIRED',
        'SYSTEM',
      ].sort(),
    )
  })

  it('모든 타입의 톤이 색 클래스에 정의돼 있다', () => {
    for (const [type, style] of Object.entries(NOTIFICATION_TYPE_STYLES)) {
      expect(NOTIFICATION_TONE_CLASSES[style.tone], `${type} 톤`).toBeTruthy()
    }
  })

  it('랭킹·관심 랭킹·사분면은 파랑, 결제 실패는 빨강, 구독 만료는 노랑이다', () => {
    expect(getNotificationTypeStyle('SCORE_RANKING_GLOBAL').tone).toBe('info')
    expect(getNotificationTypeStyle('SCORE_RANKING_WATCHLIST').tone).toBe('info')
    expect(getNotificationTypeStyle('QUADRANT_CHANGE').tone).toBe('info')
    expect(getNotificationTypeStyle('PAYMENT_FAILED').tone).toBe('danger')
    expect(getNotificationTypeStyle('SUBSCRIPTION_EXPIRED').tone).toBe('warn')
  })

  it('모르는 타입은 중립 "알림" 라벨로 대체한다', () => {
    expect(getNotificationTypeStyle('SOMETHING_NEW')).toEqual({ label: '알림', tone: 'neutral' })
  })
})
