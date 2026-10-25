import { describe, expect, it } from 'vitest'
import type { DailyChartResponse, MinuteCandle } from '../types/stock'
import {
  aggregateCandles,
  aggregateMinuteCandles,
  mergeDailyPages,
  minuteUnitOf,
  nextDailyPageEnd,
  normalizeMinuteCandles,
} from './candleAggregation'

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

function minute(time: number, open: number, high: number, low: number, close: number, volume: number): MinuteCandle {
  return { time, open, high, low, close, volume }
}

// 2026-10-23 09:00 KST = 00:00 UTC
const T0 = Date.UTC(2026, 9, 23, 0, 0, 0) / 1000

describe('aggregateMinuteCandles', () => {
  const ONE_MINUTE: MinuteCandle[] = [
    minute(T0, 100, 102, 99, 101, 10),
    minute(T0 + 60, 101, 105, 100, 104, 20),
    minute(T0 + 120, 104, 106, 103, 103, 30),
    minute(T0 + 180, 103, 104, 98, 99, 40),
    minute(T0 + 240, 99, 100, 97, 98, 50),
  ]

  it('1분이면 원본을 그대로 돌려준다', () => {
    expect(aggregateMinuteCandles(ONE_MINUTE, 1)).toBe(ONE_MINUTE)
  })

  it('3분봉은 N분 경계로 묶는다: 시가=첫 봉, 종가=마지막 봉, 고저=극값, 거래량=합', () => {
    const result = aggregateMinuteCandles(ONE_MINUTE, 3)

    expect(result).toEqual([minute(T0, 100, 106, 99, 103, 60), minute(T0 + 180, 103, 104, 97, 98, 90)])
  })

  it('버킷 시각은 묶인 구간의 시작 시각이다 (09:07의 5분봉은 09:05)', () => {
    const result = aggregateMinuteCandles([minute(T0 + 7 * 60, 1, 2, 0, 1, 5)], 5)

    expect(result[0].time).toBe(T0 + 5 * 60)
  })

  it('60분봉은 정시 경계로 묶이고 시간대가 달라도 어긋나지 않는다', () => {
    // 미국 정규장 시작 09:30 EDT = 13:30 UTC → 13:00 버킷, 14:00 UTC → 14:00 버킷
    const open = Date.UTC(2026, 9, 23, 13, 30, 0) / 1000
    const result = aggregateMinuteCandles(
      [minute(open, 1, 2, 1, 2, 1), minute(open + 29 * 60, 2, 3, 2, 3, 1), minute(open + 30 * 60, 3, 4, 3, 4, 1)],
      60,
    )

    expect(result.map((c) => c.time)).toEqual([
      Date.UTC(2026, 9, 23, 13, 0, 0) / 1000,
      Date.UTC(2026, 9, 23, 14, 0, 0) / 1000,
    ])
    expect(result[0].volume).toBe(2)
  })

  it('원본 배열을 변경하지 않는다', () => {
    const copy = ONE_MINUTE.map((c) => ({ ...c }))
    aggregateMinuteCandles(ONE_MINUTE, 3)

    expect(ONE_MINUTE).toEqual(copy)
  })

  it('빈 배열은 그대로다', () => {
    expect(aggregateMinuteCandles([], 5)).toEqual([])
  })
})

describe('normalizeMinuteCandles', () => {
  it('겹치는 시각은 뒤에 온 값으로 중복 제거하고 오름차순으로 정렬한다', () => {
    const result = normalizeMinuteCandles([
      minute(120, 1, 1, 1, 1, 1),
      minute(60, 2, 2, 2, 2, 2),
      minute(120, 3, 3, 3, 3, 3),
    ])

    expect(result.map((c) => [c.time, c.open])).toEqual([
      [60, 2],
      [120, 3],
    ])
  })
})

describe('minuteUnitOf', () => {
  it('minute:N 형식에서 단위를 꺼내고 그 외에는 null이다', () => {
    expect(minuteUnitOf('minute:15')).toBe(15)
    expect(minuteUnitOf('minute:7')).toBeNull()
    expect(minuteUnitOf('daily')).toBeNull()
  })
})

describe('mergeDailyPages', () => {
  it('최근 → 과거 순 페이지를 오름차순 하나로 합치고 겹치는 날짜는 한 번만 남긴다', () => {
    const recent = [bar('2026-10-02', 1, 1, 1, 1, 1), bar('2026-10-01', 1, 1, 1, 2, 1)]
    const older = [bar('2026-10-01', 1, 1, 1, 9, 1), bar('2026-09-30', 1, 1, 1, 3, 1)]

    const merged = mergeDailyPages([recent, older])

    expect(merged.map((b) => b.tradeDate)).toEqual(['2026-09-30', '2026-10-01', '2026-10-02'])
  })
})

describe('nextDailyPageEnd', () => {
  it('가장 오래된 날짜의 전날을 돌려준다 (월 경계 포함)', () => {
    const page = [bar('2026-10-02', 1, 1, 1, 1, 1), bar('2026-10-01', 1, 1, 1, 1, 1)]

    expect(nextDailyPageEnd(page)).toBe('2026-09-30')
  })

  it('빈 페이지면 더 이어받을 구간이 없다', () => {
    expect(nextDailyPageEnd([])).toBeUndefined()
  })
})
