import { apiClient } from './client'
import type { ScoreRankingResponse } from '../types/score'

export async function getDashboardScores(
  watchlistOnly = true,
  limit = 10,
): Promise<ScoreRankingResponse[]> {
  const { data } = await apiClient.get<ScoreRankingResponse[]>('/api/dashboard/scores', {
    params: { watchlistOnly, limit },
  })
  return data
}
