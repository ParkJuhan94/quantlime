import { useQuery } from '@tanstack/react-query'
import { getMarketRanking } from '../../api/market'
import { queryKeys } from '../queryKeys'

// 백엔드 MarketRankingScheduler가 장중 5초 주기로 랭킹을 갱신하므로
// 프론트도 같은 주기로 폴링한다(별도 WebSocket 토픽 없이 REST 폴링만으로
// "준실시간" 구현 - PriceBroadcastScheduler 기반 개별 종목 시세와 동일한
// 설계 철학).
export function useMarketRankingQuery(sort: 'gainers' | 'losers', limit = 10, enabled = true) {
  return useQuery({
    queryKey: queryKeys.marketRanking(sort, limit),
    queryFn: () => getMarketRanking(sort, limit),
    enabled,
    refetchInterval: enabled ? 5_000 : false,
  })
}
