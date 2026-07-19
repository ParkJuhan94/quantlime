import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addWatchlist,
  getWatchlist,
  moveWatchlistGroup,
  removeWatchlist,
  reorderWatchlist,
} from '../../api/watchlist'
import { queryKeys } from '../queryKeys'
import type { WatchlistResponse } from '../../types/watchlist'

export function useWatchlistQuery(enabled = true) {
  return useQuery({
    queryKey: queryKeys.watchlist,
    queryFn: getWatchlist,
    enabled,
  })
}

export function useAddWatchlist() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ stockCode, groupId }: { stockCode: string; groupId: number }) =>
      addWatchlist(stockCode, groupId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.watchlist })
      void queryClient.invalidateQueries({ queryKey: queryKeys.dashboardScoresAll })
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

export function useMoveWatchlistGroup() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ stockCode, groupId }: { stockCode: string; groupId: number }) =>
      moveWatchlistGroup(stockCode, groupId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.watchlist })
    },
  })
}

export function useReorderWatchlist() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (watchlistIds: number[]) => reorderWatchlist(watchlistIds),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.watchlist })
    },
  })
}
