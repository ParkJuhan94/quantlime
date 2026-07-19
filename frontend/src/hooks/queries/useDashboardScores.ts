import { useQuery } from '@tanstack/react-query'
import { getDashboardScores } from '../../api/dashboard'
import { queryKeys } from '../queryKeys'

export function useDashboardScoresQuery(watchlistOnly = true, limit = 10) {
  return useQuery({
    queryKey: queryKeys.dashboardScores(watchlistOnly, limit),
    queryFn: () => getDashboardScores(watchlistOnly, limit),
    staleTime: 60 * 1000,
  })
}
