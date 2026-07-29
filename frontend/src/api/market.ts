import { apiClient } from './client'
import type {
  ChartIndexCode,
  IndexChartPoint,
  IndexMinuteChartPoint,
  MarketIndexResponse,
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

export async function getMarketRanking(
  scope: 'domestic' | 'overseas',
  sort: 'gainers' | 'losers' | 'amount',
  limit = 10,
  watchlistOnly = false,
): Promise<MarketRankingResponse[]> {
  const { data } = await apiClient.get<MarketRankingResponse[]>('/api/market/ranking', {
    params: { scope, sort, limit, watchlistOnly },
  })
  return data
}
