export interface IndexQuote {
  value: number
  changeAmount: number
  changeRate: number
  marketOpen: boolean
  // 미국 시장(나스닥/S&P500/SOXX)에서 정규장 마감 중 프리·애프터마켓이
  // 열려 있을 때만 채워진다. 국내 지수는 항상 null.
  overMarketValue: number | null
  overMarketChangeRate: number | null
  overMarketSessionType: 'PRE_MARKET' | 'AFTER_MARKET' | null
}

export interface MarketIndexResponse {
  usdKrwRate: number | null
  usdKrwChangeType: 'UP' | 'DOWN' | 'EQUAL' | null
  usdKrwChangeRate: number | null
  bitcoinPriceKrw: number | null
  usTreasuryYield10y: number | null
  usTreasuryYield10yChangeRate: number | null
  // FRED 등 공식 과거 시세 소스가 봇 차단에 막혀(MarketIndexCache 참고)
  // 백엔드가 폴링할 때마다 값을 누적한 이력이다 - 재시작 직후엔 짧고
  // 일봉이 아니라 몇 분 단위 스냅샷이다. 홈 카드 미니 차트에만 쓴다.
  usTreasuryYield10yHistory: number[]
  bitcoinChangeRate: number | null
  // 네이버 금융 비공식 API 조회가 실패하면 null - 프론트는 자리만 유지하고 값을 비워 보여준다.
  kospi: IndexQuote | null
  kosdaq: IndexQuote | null
  nasdaq: IndexQuote | null
  sp500: IndexQuote | null
  soxx: IndexQuote | null
}

export type WorldIndexCode = 'NASDAQ' | 'SP500' | 'SOXX'
export type ChartIndexCode = 'KOSPI' | 'KOSDAQ' | WorldIndexCode

export interface IndexChartPoint {
  tradeDate: string
  open: number
  high: number
  low: number
  close: number
}

export interface IndexMinuteChartPoint {
  time: string
  price: number
}

export interface MarketRankingResponse {
  stockCode: string
  stockName: string
  sector: string
  currentPrice: number
  changeRate: number
}
