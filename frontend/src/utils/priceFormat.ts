// 백엔드 StockMapper가 MarketType을 한글 라벨로 내려주므로(예: "나스닥",
// "뉴욕증권거래소") 종목 메타데이터(marketType)만 있고 CurrentPriceResponse.currency가
// 없는 화면(관심종목 사이드패널 등)에서 통화를 판별할 때 쓴다.
export function currencyForMarketType(marketType: string | null | undefined): 'KRW' | 'USD' {
  return marketType === '나스닥' || marketType === '뉴욕증권거래소' ? 'USD' : 'KRW'
}

// currency를 생략하면 기존 국내(KRW) 동작 그대로 - 2026-07-29 해외
// 실시간가 지원으로 USD 분기 추가.
export function formatPrice(price: number | null | undefined, currency: 'KRW' | 'USD' = 'KRW'): string {
  if (price == null) return '-'
  if (currency === 'USD') {
    // 미국 주식은 소수점 2자리 - 국내처럼 반올림하면 저가주에서 의미가 사라진다.
    return `$${price.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
  }
  // 국내 주식은 원 단위 정수로만 거래되므로 소수점은 항상 버린다.
  return `${Math.round(price).toLocaleString('ko-KR', { maximumFractionDigits: 0 })}원`
}

const EOK = 100_000_000 // 억
const JO = 1_000_000_000_000 // 조

// 큰 단위 원화 금액(투자자별 순매수 등)을 억/조 단위로 압축 표시한다.
// 부호를 붙여 순매수(+)/순매도(-)를 구분한다(음수는 Math.abs 후 직접 -를 붙임).
export function formatKrwAmount(amount: number | null | undefined): string {
  if (amount == null) return '-'
  const sign = amount > 0 ? '+' : amount < 0 ? '-' : ''
  const abs = Math.abs(amount)
  if (abs >= JO) return `${sign}${(abs / JO).toFixed(1)}조`
  if (abs >= EOK) return `${sign}${Math.round(abs / EOK).toLocaleString('ko-KR')}억`
  return `${sign}${Math.round(abs).toLocaleString('ko-KR')}원`
}

// 랭킹 테이블 "거래대금" 전용(2026-08-01 요청) - formatPrice와 달리 부호를
// 안 붙인다(거래대금은 항상 0 이상). 국내/해외 모두 억/조 단위로 압축하되
// 단위 글자만 원/달러로 바꾼다 - K/M/B/T 영문 접미사보다 "17억달러"처럼
// 한글 단위가 감이 더 잘 온다는 피드백으로 영문 접미사에서 전환
// (1억=100,000,000은 통화와 무관한 숫자 단위라 원화 로직을 그대로 재사용).
export function formatTradingAmount(amount: number | null | undefined, currency: 'KRW' | 'USD' = 'KRW'): string {
  if (amount == null) return '-'
  const abs = Math.abs(amount)
  const unit = currency === 'USD' ? '달러' : '원'
  const locale = currency === 'USD' ? 'en-US' : 'ko-KR'
  if (abs >= JO) return `${(abs / JO).toFixed(1)}조${unit}`
  if (abs >= EOK) return `${Math.round(abs / EOK).toLocaleString(locale)}억${unit}`
  return `${Math.round(abs).toLocaleString(locale)}${unit}`
}

export function formatChangeRate(rate: number | null | undefined): string {
  if (rate == null) return '-'
  const sign = rate > 0 ? '+' : ''
  return `${sign}${rate.toFixed(2)}%`
}

// 국내 주식 관례: 상승은 빨간색, 하락은 파란색(미국식과 반대).
export function changeRateColorClass(rate: number | null | undefined): string {
  if (rate == null) return 'text-gray-400'
  if (rate > 0) return 'text-red-600'
  if (rate < 0) return 'text-blue-600'
  return 'text-gray-600'
}
