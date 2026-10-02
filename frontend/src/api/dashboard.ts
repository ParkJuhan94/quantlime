import { apiClient } from './client'
import type { ScoreRankingResponse } from '../types/score'
import type { RankingPeriodCode } from './market'

export async function getDashboardScores(
  watchlistOnly = true,
  limit = 10,
  scope: 'all' | 'domestic' | 'overseas' = 'all',
  period: RankingPeriodCode = 'realtime',
): Promise<ScoreRankingResponse[]> {
  const { data } = await apiClient.get<ScoreRankingResponse[]>('/api/dashboard/scores', {
    params: { watchlistOnly, limit, scope, period },
  })
  return data
}
