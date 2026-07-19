import { useQuery } from '@tanstack/react-query'
import { getPopularStocks } from '../../api/stocks'
import { queryKeys } from '../queryKeys'

export function usePopularStocksQuery(limit = 5) {
  return useQuery({
    queryKey: queryKeys.popularStocks(limit),
    queryFn: () => getPopularStocks(limit),
    staleTime: 5 * 60 * 1000,
  })
}
