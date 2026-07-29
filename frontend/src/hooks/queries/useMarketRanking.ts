import { useQuery } from '@tanstack/react-query'
import { getMarketRanking } from '../../api/market'
import { queryKeys } from '../queryKeys'

// 백엔드가 랭킹을 짧은 TTL로 캐싱하므로(국내: 자체 계산 스윕, 해외:
// TossMarketRankingCache 10초 TTL) 프론트도 비슷한 주기로 폴링한다(별도
// WebSocket 토픽 없이 REST 폴링만으로 "준실시간" 구현 - 개별 종목 시세와
// 동일한 설계 철학).
export function useMarketRankingQuery(
  scope: 'domestic' | 'overseas',
  sort: 'gainers' | 'losers' | 'amount',
  limit = 10,
  enabled = true,
  watchlistOnly = false,
) {
  return useQuery({
    queryKey: queryKeys.marketRanking(scope, sort, limit, watchlistOnly),
    queryFn: () => getMarketRanking(scope, sort, limit, watchlistOnly),
    enabled,
    refetchInterval: enabled ? 5_000 : false,
  })
}
