import { describe, expect, it } from 'vitest'
import {
  changeRateFromPrevious,
  formatChartPrice,
  formatChartVolume,
  formatKstDate,
  formatKstDateTime,
  formatKstMonthDay,
  formatKstTime,
  formatTradeDate,
  kstDateString,
  priceFormatOptions,
  pricePrecisionFor,
} from './chartFormat'

describe('pricePrecisionFor', () => {
  it('원화는 정수, 달러는 소수 2자리다', () => {
    expect(pricePrecisionFor('KRW')).toBe(0)
    expect(pricePrecisionFor('USD')).toBe(2)
  })
})

describe('priceFormatOptions', () => {
  it('자리수에 맞는 최소 호가 단위를 만든다', () => {
    expect(priceFormatOptions(0)).toEqual({ type: 'price', precision: 0, minMove: 1 })
    expect(priceFormatOptions(2)).toEqual({ type: 'price', precision: 2, minMove: 0.01 })
  })
})

describe('formatChartPrice', () => {
  it('천 단위 구분과 고정 소수 자리로 표시한다', () => {
    expect(formatChartPrice(71200, 0)).toBe('71,200')
    expect(formatChartPrice(2534.5, 2)).toBe('2,534.50')
  })
})

describe('formatChartVolume', () => {
  it('큰 거래량은 정수로, 작은 코인 거래량은 소수로 표시한다', () => {
    expect(formatChartVolume(1234567.4)).toBe('1,234,567')
    expect(formatChartVolume(1.23456)).toBe('1.2346')
  })
})

describe('changeRateFromPrevious', () => {
  it('전봉 종가 대비 등락률을 계산한다', () => {
    expect(changeRateFromPrevious(110, 100)).toBeCloseTo(10)
    expect(changeRateFromPrevious(90, 100)).toBeCloseTo(-10)
  })

  it('전봉이 없거나 0이면 null이다', () => {
    expect(changeRateFromPrevious(100, null)).toBeNull()
    expect(changeRateFromPrevious(100, 0)).toBeNull()
  })
})

describe('KST 라벨', () => {
  // 2026-10-23 15:30 KST = 06:30 UTC
  const t = Date.UTC(2026, 9, 23, 6, 30, 0) / 1000

  it('epoch 초를 한국 시각으로 표시한다', () => {
    expect(formatKstTime(t)).toBe('15:30')
    expect(formatKstMonthDay(t)).toBe('10/23')
    expect(formatKstDate(t)).toBe('2026.10.23')
    expect(formatKstDateTime(t)).toBe('2026.10.23 15:30')
  })

  it('UTC로는 전날이어도 KST 날짜로 넘어간다 (업비트 일봉 00:00 UTC = 09:00 KST)', () => {
    const upbitDayStart = Date.UTC(2026, 9, 24, 15, 0, 0) / 1000 // 2026-10-25 00:00 KST

    expect(kstDateString(upbitDayStart)).toBe('2026-10-25')
  })

  it('거래일 문자열을 점 구분으로 바꾼다', () => {
    expect(formatTradeDate('2026-10-23')).toBe('2026.10.23')
  })
})
