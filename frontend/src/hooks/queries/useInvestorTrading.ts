import { useQuery } from '@tanstack/react-query'
import { getInvestorTrading } from '../../api/market'
import { queryKeys } from '../queryKeys'
import type { InvestorTradingInterval } from '../../types/market'

// 주간/월간 집계라 하루에 한 번(MarketDataRefreshService 트리거) 갱신되는
// 데이터 - 다른 실시간성 지수 위젯과 달리 폴링할 이유가 없어 staleTime만
// 길게 잡고 refetchInterval은 두지 않는다.
export function useInvestorTradingQuery(
  code: 'KOSPI' | 'KOSDAQ',
  interval: InvestorTradingInterval,
  enabled = true,
) {
  return useQuery({
    queryKey: queryKeys.investorTrading(code, interval),
    queryFn: () => getInvestorTrading(code, interval),
    staleTime: 5 * 60_000,
    enabled,
  })
}
