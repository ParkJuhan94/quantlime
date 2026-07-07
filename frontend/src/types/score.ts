export interface ScoreResponse {
  stockCode: string
  scoreDate: string
  trendScore: number | null
  meanReversionScore: number | null
  compositeScore: number | null
  grade: string | null
  divergenceFlag: boolean | null
  divergenceMessage: string | null
  comment: string
  insufficientData: boolean
}

export interface ScoreRankingResponse {
  stockCode: string
  stockName: string
  scoreDate: string
  trendScore: number | null
  meanReversionScore: number | null
  compositeScore: number | null
  grade: string | null
  insufficientData: boolean
}
