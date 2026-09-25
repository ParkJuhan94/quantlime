export interface ScoreResponse {
  stockCode: string
  scoreDate: string
  trendScore: number | null
  meanReversionScore: number | null
  compositeScore: number | null
  // v3.0부터 도입 - 같은 날짜·같은 모집단(국내/해외) 대비 상대 순위
  // (0~100, 높을수록 상위). 등급(grade)은 이 값이 아니라 compositeScore
  // (절대점수) 기준으로 매겨진다(2026-09-16 재검토 - 등급을 백분위 기준
  // 으로 매기면 시장 전체가 나쁜 날에도 상위 10%가 기계적으로 STRONG_BUY가
  // 되는 문제가 있었음) - 화면에는 compositeScore/grade와 이 값을
  // "원점수"/"상위 N%"로 둘 다 동등하게 병기한다.
  trendPercentile: number | null
  meanReversionPercentile: number | null
  compositePercentile: number | null
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
  trendPercentile: number | null
  meanReversionPercentile: number | null
  compositePercentile: number | null
  grade: string | null
  insufficientData: boolean
  // 로컬 stock 테이블에 있는 종목만 채워짐(MarketRankingResponse와 동일한
  // 이유) - null이면 buildStockLogoUrl로 폴백.
  logoUrl: string | null
  overseas: boolean
  // 최근 20거래일 일평균 거래대금(원/달러) - 유동성 필터를 통과한 종목만
  // 랭킹에 노출되므로, 사용자가 왜 이 종목이 보이는지 납득할 수 있게 함께 보여준다.
  avgTradingValue: number | null
}
