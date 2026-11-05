import { describe, expect, it } from 'vitest'
import { isLivePriceFresh, LIVE_PRICE_MAX_AGE_MS } from './livePrice'

const NOW = Date.parse('2026-11-05T20:04:17.502+09:00')

function message(timestamp: string) {
  return { stockCode: 'AAPL', currentPrice: 351.82, changeRate: 1.2, timestamp }
}

describe('isLivePriceFresh', () => {
  it('제한 시간 안의 푸시는 유효하다', () => {
    expect(isLivePriceFresh(message('2026-11-05T20:04:00.000+09:00'), NOW)).toBe(true)
  })

  it('타임존 표기가 달라도 같은 시각으로 비교한다', () => {
    expect(isLivePriceFresh(message('2026-11-05T11:04:17.000Z'), NOW)).toBe(true)
  })

  it('제한 시간이 지나면 버린다', () => {
    const old = new Date(NOW - LIVE_PRICE_MAX_AGE_MS - 1).toISOString()
    expect(isLivePriceFresh(message(old), NOW)).toBe(false)
  })

  it('메시지가 없거나 시각을 읽을 수 없으면 버린다', () => {
    expect(isLivePriceFresh(undefined, NOW)).toBe(false)
    expect(isLivePriceFresh(message(''), NOW)).toBe(false)
  })
})
