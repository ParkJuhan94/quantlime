import { describe, expect, it } from 'vitest'
import type { DailyChartResponse } from '../types/stock'
import { aggregateCandles } from './candleAggregation'

function bar(
  tradeDate: string,
  open: number,
  high: number,
  low: number,
  close: number,
  volume: number,
): DailyChartResponse {
  return { tradeDate, open, high, low, close, volume }
}

// 2026-09-21(월) ~ 09-25(금), 다음 주 09-28(월) ~ 10-01(목)
const DAILY: DailyChartResponse[] = [
  bar('2026-09-21', 100, 110, 95, 105, 10),
  bar('2026-09-22', 105, 120, 100, 115, 20),
  bar('2026-09-23', 115, 118, 90, 92, 30),
  bar('2026-09-24', 92, 99, 88, 97, 40),
  bar('2026-09-25', 97, 101, 96, 100, 50),
  bar('2026-09-28', 100, 130, 99, 125, 60),
  bar('2026-10-01', 125, 126, 120, 121, 70),
]

describe('aggregateCandles', () => {
  it('일봉이면 원본을 그대로 돌려준다', () => {
    expect(aggregateCandles(DAILY, 'daily')).toBe(DAILY)
  })

  it('빈 배열은 어떤 주기여도 그대로다', () => {
    expect(aggregateCandles([], 'weekly')).toEqual([])
    expect(aggregateCandles([], 'monthly')).toEqual([])
  })

  it('주봉은 월요일 시작 주 단위로 묶는다: 시가=첫날, 종가=마지막날, 고저=구간 극값, 거래량=합', () => {
    const weekly = aggregateCandles(DAILY, 'weekly')

    expect(weekly).toHaveLength(2)
    expect(weekly[0]).toEqual({
      tradeDate: '2026-09-25',
      open: 100,
      high: 120,
      low: 88,
      close: 100,
      volume: 150,
    })
    expect(weekly[1]).toEqual({
      tradeDate: '2026-10-01',
      open: 100,
      high: 130,
      low: 99,
      close: 121,
      volume: 130,
    })
  })

  it('월봉은 같은 달끼리 묶고 달이 바뀌면 새 봉이다', () => {
    const monthly = aggregateCandles(DAILY, 'monthly')

    expect(monthly).toHaveLength(2)
    expect(monthly[0].tradeDate).toBe('2026-09-28')
    expect(monthly[0].open).toBe(100)
    expect(monthly[0].close).toBe(125)
    expect(monthly[0].volume).toBe(210)
    expect(monthly[1]).toEqual(bar('2026-10-01', 125, 126, 120, 121, 70))
  })

  it('일요일이 주의 끝으로 들어가지 않는다: 월요일 시작 규칙이라 일요일은 직전 월요일 주에 속한다', () => {
    const weekly = aggregateCandles(
      [bar('2026-09-26', 1, 1, 1, 1, 1), bar('2026-09-27', 2, 2, 2, 2, 2), bar('2026-09-28', 3, 3, 3, 3, 3)],
      'weekly',
    )

    expect(weekly).toHaveLength(2)
    expect(weekly[0].tradeDate).toBe('2026-09-27')
    expect(weekly[1].tradeDate).toBe('2026-09-28')
  })

  it('원본 배열을 변경하지 않는다', () => {
    const copy = JSON.parse(JSON.stringify(DAILY))

    aggregateCandles(DAILY, 'weekly')
    aggregateCandles(DAILY, 'monthly')

    expect(DAILY).toEqual(copy)
  })
})
