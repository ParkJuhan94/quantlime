import { apiClient } from './client'
import type { ScoreRankingResponse } from '../types/score'

export async function getDashboardScores(): Promise<ScoreRankingResponse[]> {
  const { data } = await apiClient.get<ScoreRankingResponse[]>('/api/dashboard/scores')
  return data
}
