import { useQuery } from '@tanstack/react-query'
import { getDashboardScores } from '../../api/dashboard'
import { queryKeys } from '../queryKeys'

// enabled=false(스코어 탭이 아니거나 비구독)면 요청 자체를 보내지 않는다
// (useStockScoreQuery와 동일한 이유 - PremiumGate 참고).
export function useDashboardScoresQuery(
  watchlistOnly = true,
  limit = 10,
  scope: 'all' | 'domestic' | 'overseas' = 'all',
  enabled = true,
) {
  return useQuery({
    queryKey: queryKeys.dashboardScores(watchlistOnly, limit, scope),
    queryFn: () => getDashboardScores(watchlistOnly, limit, scope),
    staleTime: 60 * 1000,
    enabled,
  })
}
