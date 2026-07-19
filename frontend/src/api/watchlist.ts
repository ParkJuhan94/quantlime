import { apiClient } from './client'
import type { WatchlistGroupResponse, WatchlistResponse } from '../types/watchlist'

export async function getWatchlist(): Promise<WatchlistResponse[]> {
  const { data } = await apiClient.get<WatchlistResponse[]>('/api/watchlist')
  return data
}

// 등록과 동시에 그룹을 지정한다("미분류" 폐지 - 백엔드가 groupId를 필수로 검증한다).
export async function addWatchlist(stockCode: string, groupId: number): Promise<WatchlistResponse> {
  const { data } = await apiClient.post<WatchlistResponse>(`/api/watchlist/${stockCode}`, { groupId })
  return data
}

export async function removeWatchlist(stockCode: string): Promise<void> {
  await apiClient.delete(`/api/watchlist/${stockCode}`)
}

export async function moveWatchlistGroup(stockCode: string, groupId: number): Promise<void> {
  await apiClient.patch(`/api/watchlist/${stockCode}/group`, { groupId })
}

export async function reorderWatchlist(watchlistIds: number[]): Promise<void> {
  await apiClient.put('/api/watchlist/reorder', { watchlistIds })
}

export async function getWatchlistGroups(): Promise<WatchlistGroupResponse[]> {
  const { data } = await apiClient.get<WatchlistGroupResponse[]>('/api/watchlist/groups')
  return data
}

export async function createWatchlistGroup(name: string): Promise<WatchlistGroupResponse> {
  const { data } = await apiClient.post<WatchlistGroupResponse>('/api/watchlist/groups', { name })
  return data
}

export async function renameWatchlistGroup(groupId: number, name: string): Promise<WatchlistGroupResponse> {
  const { data } = await apiClient.patch<WatchlistGroupResponse>(`/api/watchlist/groups/${groupId}`, { name })
  return data
}

export async function deleteWatchlistGroup(groupId: number): Promise<void> {
  await apiClient.delete(`/api/watchlist/groups/${groupId}`)
}

export async function reorderWatchlistGroups(groupIds: number[]): Promise<void> {
  await apiClient.put('/api/watchlist/groups/reorder', { groupIds })
}
