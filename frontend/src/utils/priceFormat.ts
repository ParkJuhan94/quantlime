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
