import { apiClient } from './client'
import type { ScoreRankingResponse } from '../types/score'

export async function getDashboardScores(
  watchlistOnly = true,
  limit = 10,
  scope: 'all' | 'domestic' | 'overseas' = 'all',
): Promise<ScoreRankingResponse[]> {
  const { data } = await apiClient.get<ScoreRankingResponse[]>('/api/dashboard/scores', {
    params: { watchlistOnly, limit, scope },
  })
  return data
}
