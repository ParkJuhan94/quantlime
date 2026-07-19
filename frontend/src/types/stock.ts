export interface StockDetailResponse {
  id: number
  stockCode: string
  stockName: string
  marketType: string
  listingStatus: string
  sector: string
  logoUrl: string
}

export interface PageResponse<T> {
  content: T[]
  size: number
  hasNext: boolean
}

export interface CurrentPriceResponse {
  stockCode: string
  price: number | null
  changeRate: number | null
  currency: string | null
  timestamp: string | null
}

export interface DailyChartResponse {
  tradeDate: string
  open: number
  high: number
  low: number
  close: number
  volume: number
}

// 네이버 금융 비공식 API 특성상 개별 필드가 없을 수 있어 전부 nullable -
// 값이 없는 항목은 프론트에서 조용히 숨긴다.
export interface StockFundamentalsResponse {
  marketCap: number | null
  per: number | null
  forwardPer: number | null
  pbr: number | null
  psr: number | null
  debtRatio: number | null
}
