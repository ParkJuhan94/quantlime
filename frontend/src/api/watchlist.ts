import { apiClient } from './client'
import type { WatchlistResponse } from '../types/watchlist'

export async function getWatchlist(): Promise<WatchlistResponse[]> {
  const { data } = await apiClient.get<WatchlistResponse[]>('/api/watchlist')
  return data
}

export async function addWatchlist(stockCode: string): Promise<WatchlistResponse> {
  const { data } = await apiClient.post<WatchlistResponse>(`/api/watchlist/${stockCode}`)
  return data
}

export async function removeWatchlist(stockCode: string): Promise<void> {
  await apiClient.delete(`/api/watchlist/${stockCode}`)
}
