export interface BucketResponse {
  bucketNumber: number
  meanExcessReturn: number | null
  medianExcessReturn: number | null
  hitRate: number | null
  sampleSize: number
}

export interface HorizonBacktestResponse {
  horizonDays: number
  rankIc: number | null
  rankIcCiLow: number | null
  rankIcCiHigh: number | null
  sampleSize: number
  buckets: BucketResponse[]
}

export type BacktestAxisCode = 'TREND' | 'MEAN_REVERSION'

export interface AxisBacktestResponse {
  axis: BacktestAxisCode
  axisLabel: string
  scoreAutocorrelation: number | null
  gradeFlipRate: number | null
  horizons: HorizonBacktestResponse[]
}

export interface DailyScoreResponse {
  tradeDate: string
  closePrice: number
  trendScore: number | null
  meanReversionScore: number | null
  quadrant: string | null
  grade: string | null
}

export interface BacktestResponse {
  stockCode: string
  scoreVersion: string
  backtestDate: string | null
  axes: AxisBacktestResponse[]
  dailyScores: DailyScoreResponse[]
}
