import { useQuery } from '@tanstack/react-query'
import { getMarketRanking, type RankingPeriodCode } from '../../api/market'
import { queryKeys } from '../queryKeys'

// 백엔드가 랭킹을 짧은 TTL로 캐싱하므로(국내: 자체 계산 스윕, 해외:
// TossMarketRankingCache 10초 TTL) 프론트도 비슷한 주기로 폴링한다. 순위는
// 이 폴링으로만 바뀌고, 상위 행의 현재가만 RankingTable이 소켓 푸시로 덮어쓴다.
export function useMarketRankingQuery(
  scope: 'domestic' | 'overseas',
  sort: 'gainers' | 'losers' | 'amount',
  limit = 10,
  enabled = true,
  watchlistOnly = false,
  period: RankingPeriodCode = 'realtime',
) {
  return useQuery({
    queryKey: queryKeys.marketRanking(scope, sort, limit, watchlistOnly, period),
    queryFn: () => getMarketRanking(scope, sort, limit, watchlistOnly, period),
    enabled,
    refetchInterval: enabled ? 5_000 : false,
  })
}
