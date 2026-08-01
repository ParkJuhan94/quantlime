export interface ScoreResponse {
  stockCode: string
  scoreDate: string
  trendScore: number | null
  meanReversionScore: number | null
  compositeScore: number | null
  grade: string | null
  divergenceFlag: boolean | null
  divergenceMessage: string | null
  insufficientData: boolean
}

export interface ScoreRankingResponse {
  stockCode: string
  stockName: string
  sector: string
  scoreDate: string
  trendScore: number | null
  meanReversionScore: number | null
  compositeScore: number | null
  grade: string | null
  insufficientData: boolean
  // 로컬 stock 테이블에 있는 종목만 채워짐(MarketRankingResponse와 동일한
  // 이유) - null이면 buildStockLogoUrl로 폴백.
  logoUrl: string | null
  overseas: boolean
}
