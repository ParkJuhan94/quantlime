import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { addWatchlist, getWatchlist, removeWatchlist } from '../../api/watchlist'
import { queryKeys } from '../queryKeys'
import type { WatchlistResponse } from '../../types/watchlist'

export function useWatchlistQuery() {
  return useQuery({
    queryKey: queryKeys.watchlist,
    queryFn: getWatchlist,
  })
}

export function useAddWatchlist() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (stockCode: string) => addWatchlist(stockCode),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.watchlist })
      void queryClient.invalidateQueries({ queryKey: queryKeys.dashboardScores })
    },
  })
}

export function useRemoveWatchlist() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (stockCode: string) => removeWatchlist(stockCode),
    onMutate: async (stockCode: string) => {
      await queryClient.cancelQueries({ queryKey: queryKeys.watchlist })
      const previous = queryClient.getQueryData<WatchlistResponse[]>(queryKeys.watchlist)
      queryClient.setQueryData<WatchlistResponse[]>(queryKeys.watchlist, (old) =>
        old?.filter((item) => item.stockCode !== stockCode),
      )
      return { previous }
    },
    onError: (_error, _stockCode, context) => {
      if (context?.previous) {
        queryClient.setQueryData(queryKeys.watchlist, context.previous)
      }
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.watchlist })
    },
  })
}
