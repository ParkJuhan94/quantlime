import type { StockInvestorTradingResponse } from '../types/stock'

export type InvestorGroupKey =
  'individualNetBuyVolume' | 'foreignerNetBuyVolume' | 'institutionNetBuyVolume' | 'otherCorporationNetBuyVolume'

// 최근 days거래일(확정된 날만)의 순매수 합계. 잠정 기록은 개인·기타법인이 null이라 섞으면 그 분류만 며칠이 빠진
// 합계가 나와 분류끼리 기간이 어긋난다 - 모든 분류를 같은 확정 거래일 구간으로 맞춘다. 확정 기록이 없으면 null.
export function sumNetBuyVolume(
  rows: StockInvestorTradingResponse[],
  key: InvestorGroupKey,
  days: number,
): number | null {
  const confirmed = rows.filter((row) => row.confirmed).slice(-days)
  if (confirmed.length === 0) return null
  return confirmed.reduce((sum, row) => sum + (row[key] ?? 0), 0)
}

const MAN = 10_000 // 만
const EOK = 100_000_000 // 억

// 주식 수를 만/억 단위로 압축하고 순매수(+)/순매도(-) 부호를 붙인다. null은 '-'.
export function formatShares(shares: number | null | undefined): string {
  if (shares == null) return '-'
  const sign = shares > 0 ? '+' : shares < 0 ? '-' : ''
  const abs = Math.abs(shares)
  if (abs >= EOK) return `${sign}${(abs / EOK).toFixed(1)}억주`
  if (abs >= MAN) return `${sign}${Math.round(abs / MAN).toLocaleString('ko-KR')}만주`
  return `${sign}${Math.round(abs).toLocaleString('ko-KR')}주`
}

// 외국인 보유율(소수 비율 0.5089)을 '50.89%'로. null은 '-'.
export function formatHoldingRate(rate: number | null | undefined): string {
  if (rate == null) return '-'
  return `${(rate * 100).toFixed(2)}%`
}
