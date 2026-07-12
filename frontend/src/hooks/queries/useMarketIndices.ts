import { useQuery } from '@tanstack/react-query'
import { getMarketIndices } from '../../api/market'
import { queryKeys } from '../queryKeys'

// 토스 환율은 1분 단위로만 갱신되고 서버도 20초 캐싱을 두므로, 프론트는
// 60초 주기로만 재조회한다(백엔드 MarketIndexCache와 이중 방어).
export function useMarketIndicesQuery() {
  return useQuery({
    queryKey: queryKeys.marketIndices,
    queryFn: getMarketIndices,
    staleTime: 60_000,
    refetchInterval: 60_000,
  })
}
