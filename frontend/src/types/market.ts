export interface MarketIndexResponse {
  usdKrwRate: number | null
  usdKrwChangeType: 'UP' | 'DOWN' | 'EQUAL' | null
  bitcoinPriceKrw: number | null
  bitcoinChangeRate: number | null
}

export interface MarketRankingResponse {
  stockCode: string
  stockName: string
  sector: string
  currentPrice: number
  changeRate: number
}
