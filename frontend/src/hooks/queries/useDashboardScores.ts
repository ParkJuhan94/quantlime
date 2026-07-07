import { useQuery } from '@tanstack/react-query'
import { getDashboardScores } from '../../api/dashboard'
import { queryKeys } from '../queryKeys'

export function useDashboardScoresQuery() {
  return useQuery({
    queryKey: queryKeys.dashboardScores,
    queryFn: getDashboardScores,
    staleTime: 60 * 1000,
  })
}
