import { apiClient } from './client'
import type { MarketIndexResponse, MarketRankingResponse } from '../types/market'

export async function getMarketIndices(): Promise<MarketIndexResponse> {
  const { data } = await apiClient.get<MarketIndexResponse>('/api/market/indices')
  return data
}

export async function getMarketRanking(
  sort: 'gainers' | 'losers',
  limit = 10,
): Promise<MarketRankingResponse[]> {
  const { data } = await apiClient.get<MarketRankingResponse[]>('/api/market/ranking', {
    params: { sort, limit },
  })
  return data
}
