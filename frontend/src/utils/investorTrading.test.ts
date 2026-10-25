import { describe, expect, it } from 'vitest'
import type { StockInvestorTradingResponse } from '../types/stock'
import { availableSummaryDays, formatHoldingRate, formatShares, sumNetBuyVolume } from './investorTrading'

function row(
  tradeDate: string,
  individual: number | null,
  foreigner: number,
  institution: number,
  confirmed = true,
): StockInvestorTradingResponse {
  return {
    tradeDate,
    individualNetBuyVolume: individual,
    foreignerNetBuyVolume: foreigner,
    institutionNetBuyVolume: institution,
    otherCorporationNetBuyVolume: individual == null ? null : 0,
    foreignerHoldingRate: null,
    confirmed,
  }
}

// 오름차순: 10-20 ~ 10-23(확정), 10-26(잠정)
const ROWS = [
  row('2026-10-20', -100, 10, 90),
  row('2026-10-21', -200, 20, 180),
  row('2026-10-22', -300, -30, 330),
  row('2026-10-23', 50, 40, -90),
  row('2026-10-26', null, 5, 7, false),
]

describe('sumNetBuyVolume', () => {
  it('최근 확정 거래일 N일의 순매수를 합친다', () => {
    expect(sumNetBuyVolume(ROWS, 'foreignerNetBuyVolume', 2)).toBe(10) // -30 + 40
    expect(sumNetBuyVolume(ROWS, 'individualNetBuyVolume', 3)).toBe(-450) // -200 -300 +50
  })

  it('잠정 기록(오늘)은 합계에서 제외해 분류끼리 같은 기간으로 맞춘다', () => {
    expect(sumNetBuyVolume(ROWS, 'foreignerNetBuyVolume', 1)).toBe(40) // 잠정 10-26의 5는 제외
  })

  it('데이터보다 큰 N은 있는 만큼만 합친다', () => {
    expect(sumNetBuyVolume(ROWS, 'institutionNetBuyVolume', 20)).toBe(510)
  })

  it('확정 기록이 하나도 없으면 null이다', () => {
    expect(sumNetBuyVolume([row('2026-10-26', null, 5, 7, false)], 'foreignerNetBuyVolume', 5)).toBeNull()
    expect(sumNetBuyVolume([], 'foreignerNetBuyVolume', 5)).toBeNull()
  })
})

describe('formatShares', () => {
  it('만/억 단위로 압축하고 부호를 붙인다', () => {
    expect(formatShares(123_456_789)).toBe('+1.2억주')
    expect(formatShares(-2_345_000)).toBe('-235만주')
    expect(formatShares(9_500)).toBe('+9,500주')
    expect(formatShares(11_630)).toBe('+11,630주') // 10만 미만은 만 단위로 뭉개지 않는다
    expect(formatShares(100_000)).toBe('+10만주')
    expect(formatShares(0)).toBe('0주')
  })

  it('null은 대시다', () => {
    expect(formatShares(null)).toBe('-')
  })
})

describe('formatHoldingRate', () => {
  it('소수 비율을 퍼센트로 바꾼다', () => {
    expect(formatHoldingRate(0.5089)).toBe('50.89%')
    expect(formatHoldingRate(null)).toBe('-')
  })
})

describe('availableSummaryDays', () => {
  const confirmedRows = (n: number) =>
    Array.from({ length: n }, (_, i) => row(`2026-01-${String(i + 1).padStart(2, '0')}`, 1, 1, 1))

  it('확정된 거래일 수 이하의 구간만 고른다', () => {
    expect(availableSummaryDays(confirmedRows(120))).toEqual([5, 20, 60, 120])
    expect(availableSummaryDays(confirmedRows(30))).toEqual([5, 20])
  })

  it('잠정 기록은 세지 않는다', () => {
    expect(availableSummaryDays([...confirmedRows(5), row('2026-02-01', null, 1, 1, false)])).toEqual([5])
  })

  it('데이터가 아주 적어도 첫 구간은 남긴다', () => {
    expect(availableSummaryDays(confirmedRows(2))).toEqual([5])
    expect(availableSummaryDays([])).toEqual([5])
  })
})
