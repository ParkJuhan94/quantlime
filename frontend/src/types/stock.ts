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

export interface OrderbookLevel {
  price: number | null
  volume: number | null
}

// asks는 낮은 가격순, bids는 높은 가격순(토스 응답 그대로).
export interface OrderbookResponse {
  timestamp: string | null
  currency: 'KRW' | 'USD'
  asks: OrderbookLevel[]
  bids: OrderbookLevel[]
}

export interface TradeResponse {
  price: number | null
  volume: number | null
  timestamp: string
  currency: 'KRW' | 'USD'
}

// 가격제한이 없는 시장(미국 등)은 상/하한가가 null.
export interface PriceLimitResponse {
  upperLimitPrice: number | null
  lowerLimitPrice: number | null
  currency: 'KRW' | 'USD'
}

export interface StockWarningResponse {
  warningType: string
  label: string
  exchange: string | null
  startDate: string | null
  endDate: string | null
}
