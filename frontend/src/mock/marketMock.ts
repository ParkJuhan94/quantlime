// 홈 리디자인용 예시(mock) 시장 데이터 - 시가총액/거래대금 실시간 전종목 랭킹과
// 코스피 등 지수 위젯은 토스 API만으로 불가능해 아직 백엔드가 없다
// (docs/ROADMAP.md #1, #2a~c 참고, Phase 7 기획 중). 종목 코드/이름/섹터는
// 실제 DB에 있는 진짜 종목이고(그래서 클릭 시 실제 상세 페이지·실시간
// 시세·관심종목 등록이 전부 정상 동작한다), 등락률/거래대금/시가총액만
// 화면 데모용으로 지어낸 값이다 - UI에도 "예시 데이터" 캡션을 반드시 노출할 것.

import { buildStockLogoUrl } from '../utils/stockLogo'

export interface MockRankingStock {
  stockCode: string
  stockName: string
  sector: string
  basePrice: number
  baseChangeRate: number
  amount: string
  amountValue: number
  volumeValue: number
  marketCap: string
  logoUrl: string
}

const RAW_RANKING_STOCKS: Omit<MockRankingStock, 'logoUrl'>[] = [
  {
    stockCode: '005930',
    stockName: '삼성전자',
    sector: '전기전자',
    basePrice: 128000,
    baseChangeRate: -5.64,
    amount: '1조 2,400억원',
    amountValue: 1240000000000,
    volumeValue: 9680000,
    marketCap: '5.6조원',
  },
  {
    stockCode: '000660',
    stockName: 'SK하이닉스',
    sector: '전기전자',
    basePrice: 512000,
    baseChangeRate: 7.65,
    amount: '9,870억원',
    amountValue: 987000000000,
    volumeValue: 1920000,
    marketCap: '3.1조원',
  },
  {
    stockCode: '035420',
    stockName: 'NAVER',
    sector: '서비스업',
    basePrice: 218500,
    baseChangeRate: 2.13,
    amount: '4,120억원',
    amountValue: 412000000000,
    volumeValue: 1880000,
    marketCap: '4,120억원',
  },
  {
    stockCode: '035720',
    stockName: '카카오',
    sector: '서비스업',
    basePrice: 48200,
    baseChangeRate: 4.67,
    amount: '3,340억원',
    amountValue: 334000000000,
    volumeValue: 6910000,
    marketCap: '8,340억원',
  },
  {
    stockCode: '373220',
    stockName: 'LG에너지솔루션',
    sector: '전기전자',
    basePrice: 412000,
    baseChangeRate: -3.14,
    amount: '2,980억원',
    amountValue: 298000000000,
    volumeValue: 720000,
    marketCap: '9,800억원',
  },
  {
    stockCode: '005380',
    stockName: '현대차',
    sector: '운수장비',
    basePrice: 231500,
    baseChangeRate: 2.44,
    amount: '2,650억원',
    amountValue: 265000000000,
    volumeValue: 1140000,
    marketCap: '1.2조원',
  },
  {
    stockCode: '068270',
    stockName: '셀트리온',
    sector: '의약품',
    basePrice: 187300,
    baseChangeRate: -1.2,
    amount: '2,120억원',
    amountValue: 212000000000,
    volumeValue: 980000,
    marketCap: '1.8조원',
  },
  {
    stockCode: '207940',
    stockName: '삼성바이오로직스',
    sector: '의약품',
    basePrice: 891000,
    baseChangeRate: 0.43,
    amount: '1,760억원',
    amountValue: 176000000000,
    volumeValue: 210000,
    marketCap: '9.2조원',
  },
]

export const MOCK_RANKING_STOCKS: MockRankingStock[] = RAW_RANKING_STOCKS.map((stock) => ({
  ...stock,
  logoUrl: buildStockLogoUrl(stock.stockCode),
}))

export interface MockMarketIndex {
  label: string
  value: string
  changeLabel: string
  up: boolean
  points: string
}

// 달러 환율·비트코인은 이제 실데이터(MarketIndexRow가 useMarketIndicesQuery로
// 별도 조회)라 여기서 뺐다. 코스피·코스닥은 토스에 지수 심볼이 없고,
// 나스닥·S&P500·필라델피아반도체는 해외지수 API가 아직 없어(docs/ROADMAP.md
// #1) 예시 데이터로 남아 있다.
export const MOCK_MARKET_INDICES: MockMarketIndex[] = [
  { label: '코스피', value: '2,753.94', changeLabel: '+2.52%', up: true, points: '0,16 8,12 16,14 24,6 32,8 40,4' },
  { label: '코스닥', value: '861.05', changeLabel: '+0.94%', up: true, points: '0,14 8,16 16,10 24,12 32,6 40,8' },
  { label: '나스닥', value: '19,281.60', changeLabel: '+0.28%', up: true, points: '0,18 8,14 16,16 24,8 32,10 40,4' },
  { label: 'S&P 500', value: '6,090.39', changeLabel: '+0.42%', up: true, points: '0,6 8,10 16,8 24,16 32,14 40,20' },
  {
    label: '필라델피아 반도체',
    value: '5,102.16',
    changeLabel: '-0.71%',
    up: false,
    points: '0,18 8,14 16,10 24,12 32,8 40,6',
  },
]

export const MOCK_TRENDING_SECTORS = [
  { name: '반도체', changeLabel: '+4.13%' },
  { name: '2차전지', changeLabel: '+3.82%' },
  { name: '바이오', changeLabel: '+3.49%' },
]
