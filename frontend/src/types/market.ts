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
  // Toss 조회가 실패하면 null - 프론트는 자리만 유지하고 값을 비워 보여준다.
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

export type InvestorTradingInterval = 'weekly' | 'monthly'

// 순매수(매수-매도, KRW) - 개인/외국인/기관계+세부7종/기타법인. 백엔드가
// 이미 순매수로 계산해서 내려준다(InvestorTradingMapper 참고).
export interface InvestorTradingResponse {
  baseDate: string
  individualNetBuyAmount: number
  foreignerNetBuyAmount: number
  institutionNetBuyAmount: number
  financialInvestmentNetBuyAmount: number
  insuranceNetBuyAmount: number
  trustNetBuyAmount: number
  privateEquityFundNetBuyAmount: number
  bankNetBuyAmount: number
  otherFinancialInstitutionNetBuyAmount: number
  pensionFundNetBuyAmount: number
  otherCorporationNetBuyAmount: number
}

export interface MarketRankingResponse {
  stockCode: string
  stockName: string
  // 해외 상위 종목이 로컬 stock 테이블(백테스트 유니버스)에 없으면
  // null - 이 경우 stockName엔 심볼 원문이 대신 채워진다(백엔드
  // TossMarketRankingCache 참고).
  sector: string | null
  currentPrice: number
  changeRate: number
  // 2026-07-29 Toss 랭킹 API 연동으로 추가 - 국내 관심종목만 보기(자체
  // 계산 경로)에서는 항상 null.
  currency: 'KRW' | 'USD' | null
  tradingVolume: number | null
  tradingAmount: number | null
  // 로컬 stock 테이블에 있는 종목만 채워짐(나스닥은 .O 접미사가 붙어야
  // 해서 종목코드만으로 프론트가 직접 조립할 수 없다 - 백엔드
  // StockMapper.toLogoUrl 참고) - null이면 buildStockLogoUrl로 폴백.
  logoUrl: string | null
  // 2026-08-01 추가 - 로컬 stock 테이블에 이 종목이 있는지 여부. 국내는
  // 항상 true(백엔드가 이미 없는 심볼을 걸러냄), 해외만 false가 나올 수
  // 있다. false면 상세페이지 진입/관심종목 등록 둘 다 404가 나므로
  // 프론트에서 막는다(logoUrl === null과는 의미가 다름 - 로고는 종목이
  // 있어도 없을 수 있음).
  detailAvailable: boolean
}
