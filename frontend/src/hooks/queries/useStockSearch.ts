import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { searchStocks } from '../../api/stocks'
import { queryKeys } from '../queryKeys'

export function useStockSearch(q: string, page = 0) {
  return useQuery({
    queryKey: queryKeys.stockSearch(q, page),
    queryFn: () => searchStocks(q, page),
    enabled: q.trim().length > 0,
    staleTime: 5 * 60 * 1000,
    placeholderData: keepPreviousData,
  })
}
