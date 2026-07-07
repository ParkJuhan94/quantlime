export interface StockDetailResponse {
  id: number
  stockCode: string
  stockName: string
  marketType: string
  listingStatus: string
  sector: string
}

export interface PageResponse<T> {
  content: T[]
  size: number
  hasNext: boolean
}

export interface CurrentPriceResponse {
  stockCode: string
  price: number | null
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
