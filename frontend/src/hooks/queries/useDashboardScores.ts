import { useQuery } from '@tanstack/react-query'
import { getDashboardScores } from '../../api/dashboard'
import { queryKeys } from '../queryKeys'

export function useDashboardScoresQuery(
  watchlistOnly = true,
  limit = 10,
  scope: 'all' | 'domestic' | 'overseas' = 'all',
) {
  return useQuery({
    queryKey: queryKeys.dashboardScores(watchlistOnly, limit, scope),
    queryFn: () => getDashboardScores(watchlistOnly, limit, scope),
    staleTime: 60 * 1000,
  })
}
