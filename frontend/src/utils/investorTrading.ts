import type { StockInvestorTradingResponse } from '../types/stock'

// 표/칩의 한 열 - 한 분류의 순매수 값을 한 행에서 꺼내는 함수. 세부 분류는 행에 세부가 없으면 null을 돌려준다.
export interface InvestorColumn {
  id: string
  label: string
  pick: (row: StockInvestorTradingResponse) => number | null
  // 기관 세부 분류 여부 - 표에서 기관 옆에 들여써 구분한다.
  detail?: boolean
}

export const MAIN_COLUMNS: InvestorColumn[] = [
  { id: 'individual', label: '개인', pick: (row) => row.individualNetBuyVolume },
  { id: 'foreigner', label: '외국인', pick: (row) => row.foreignerNetBuyVolume },
  { id: 'institution', label: '기관', pick: (row) => row.institutionNetBuyVolume },
  { id: 'otherCorporation', label: '기타법인', pick: (row) => row.otherCorporationNetBuyVolume },
]

// 기관 합계를 이루는 세부 7개 분류(토스 breakdown 순서).
export const INSTITUTION_DETAIL_COLUMNS: InvestorColumn[] = [
  {
    id: 'financialInvestment',
    label: '금융투자',
    detail: true,
    pick: (row) => row.institutionBreakdown?.financialInvestmentNetBuyVolume ?? null,
  },
  {
    id: 'insurance',
    label: '보험',
    detail: true,
    pick: (row) => row.institutionBreakdown?.insuranceNetBuyVolume ?? null,
  },
  { id: 'trust', label: '투신', detail: true, pick: (row) => row.institutionBreakdown?.trustNetBuyVolume ?? null },
  {
    id: 'privateEquityFund',
    label: '사모',
    detail: true,
    pick: (row) => row.institutionBreakdown?.privateEquityFundNetBuyVolume ?? null,
  },
  { id: 'bank', label: '은행', detail: true, pick: (row) => row.institutionBreakdown?.bankNetBuyVolume ?? null },
  {
    id: 'otherFinancialInstitution',
    label: '기타금융',
    detail: true,
    pick: (row) => row.institutionBreakdown?.otherFinancialInstitutionNetBuyVolume ?? null,
  },
  {
    id: 'pensionFund',
    label: '연기금',
    detail: true,
    pick: (row) => row.institutionBreakdown?.pensionFundNetBuyVolume ?? null,
  },
]

// 누적 구간 선택지. 데이터가 모자란 신규 상장 종목에서 "120일 누적"이 실제로는 30일치인 걸 120일처럼 보이지 않게,
// 확정된 거래일 수 이하인 구간만 보여준다(최소 첫 구간은 항상 둔다).
export const SUMMARY_DAY_OPTIONS = [5, 20, 60, 120] as const

export function availableSummaryDays(rows: StockInvestorTradingResponse[]): number[] {
  const confirmedCount = rows.filter((row) => row.confirmed).length
  const options = SUMMARY_DAY_OPTIONS.filter((days) => days <= confirmedCount)
  return options.length > 0 ? options : [SUMMARY_DAY_OPTIONS[0]]
}

// 최근 days거래일(확정된 날만)의 순매수 합계. 서버는 거래일만 내려주므로(주말·휴장일 없음) days는 달력 일수가 아니라
// 거래일 수다. 잠정 기록은 개인·기타법인이 null이라 섞으면 그 분류만 며칠이 빠진 합계가 나와 분류끼리 기간이 어긋난다 -
// 모든 분류를 같은 확정 거래일 구간으로 맞춘다. 구간 안에 값이 없는 행이 하나라도 있으면(세부 저장 이전 행 등) 일부만 더한
// 값을 전체 합계처럼 보이지 않게 null(알 수 없음)로 돌려준다. 확정 기록이 없어도 null.
export function sumNetBuyVolume(
  rows: StockInvestorTradingResponse[],
  pick: (row: StockInvestorTradingResponse) => number | null,
  days: number,
): number | null {
  const confirmed = rows.filter((row) => row.confirmed).slice(-days)
  if (confirmed.length === 0) return null
  let sum = 0
  for (const row of confirmed) {
    const value = pick(row)
    if (value == null) return null
    sum += value
  }
  return sum
}

const MAN = 10_000 // 만
// 10만 주 미만은 만 단위로 반올림하면 "+1만주"처럼 값이 뭉개져서(실제 11,630주) 원래 숫자를 그대로 보여준다.
const COMPACT_FROM = 100_000
const EOK = 100_000_000 // 억

// 주식 수를 만/억 단위로 압축하고 순매수(+)/순매도(-) 부호를 붙인다. null은 '-'.
export function formatShares(shares: number | null | undefined): string {
  if (shares == null) return '-'
  const sign = shares > 0 ? '+' : shares < 0 ? '-' : ''
  const abs = Math.abs(shares)
  if (abs >= EOK) return `${sign}${(abs / EOK).toFixed(1)}억주`
  if (abs >= COMPACT_FROM) return `${sign}${Math.round(abs / MAN).toLocaleString('ko-KR')}만주`
  return `${sign}${Math.round(abs).toLocaleString('ko-KR')}주`
}

// 외국인 보유율(소수 비율 0.5089)을 '50.89%'로. null은 '-'.
export function formatHoldingRate(rate: number | null | undefined): string {
  if (rate == null) return '-'
  return `${(rate * 100).toFixed(2)}%`
}
