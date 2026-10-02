import { apiClient } from './client'
import type {
  ChartIndexCode,
  IndexChartPoint,
  IndexMinuteChartPoint,
  InvestorTradingInterval,
  InvestorTradingResponse,
  MarketIndexResponse,
  HotSectorResponse,
  MarketRankingResponse,
} from '../types/market'

export async function getMarketIndices(): Promise<MarketIndexResponse> {
  const { data } = await apiClient.get<MarketIndexResponse>('/api/market/indices')
  return data
}

export async function getIndexChart(code: ChartIndexCode): Promise<IndexChartPoint[]> {
  const { data } = await apiClient.get<IndexChartPoint[]>(`/api/market/indices/${code}/chart`)
  return data
}

export async function getIndexMinuteChart(code: 'KOSPI' | 'KOSDAQ'): Promise<IndexMinuteChartPoint[]> {
  const { data } = await apiClient.get<IndexMinuteChartPoint[]>(`/api/market/indices/${code}/minute-chart`)
  return data
}

export async function getBitcoinChart(): Promise<IndexMinuteChartPoint[]> {
  const { data } = await apiClient.get<IndexMinuteChartPoint[]>('/api/market/indices/bitcoin/minute-chart')
  return data
}

export async function getExchangeRateChart(): Promise<IndexChartPoint[]> {
  const { data } = await apiClient.get<IndexChartPoint[]>('/api/market/indices/usdkrw/chart')
  return data
}

export async function getInvestorTrading(
  code: 'KOSPI' | 'KOSDAQ',
  interval: InvestorTradingInterval,
  count = 52,
): Promise<InvestorTradingResponse[]> {
  const { data } = await apiClient.get<InvestorTradingResponse[]>(`/api/market/indices/${code}/investor-trading`, {
    params: { interval, count },
  })
  return data
}

// 백엔드 RankingPeriod.code와 일치해야 한다(토스 랭킹 duration과 1:1 대응).
export type RankingPeriodCode = 'realtime' | '1d' | '1w' | '1mo' | '3mo' | '6mo' | '1y'

export async function getMarketRanking(
  scope: 'domestic' | 'overseas',
  sort: 'gainers' | 'losers' | 'amount',
  limit = 10,
  watchlistOnly = false,
  period: RankingPeriodCode = 'realtime',
): Promise<MarketRankingResponse[]> {
  const { data } = await apiClient.get<MarketRankingResponse[]>('/api/market/ranking', {
    params: { scope, sort, limit, watchlistOnly, period },
  })
  return data
}

export async function getHotSectors(limit = 5): Promise<HotSectorResponse[]> {
  const { data } = await apiClient.get<HotSectorResponse[]>('/api/market/sectors', { params: { limit } })
  return data
}
