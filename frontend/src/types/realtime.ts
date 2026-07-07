// 백엔드 PriceBroadcastMessage와 동일한 형태. 거래량(volume)은
// 폴링 소스(Toss 현재가 API)가 제공하지 않아 의도적으로 없다.
export interface PriceBroadcastMessage {
  stockCode: string
  currentPrice: number | null
  changeRate: number | null
  timestamp: string
}
