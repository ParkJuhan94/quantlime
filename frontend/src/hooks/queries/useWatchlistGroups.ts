import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createWatchlistGroup,
  deleteWatchlistGroup,
  getWatchlistGroups,
  renameWatchlistGroup,
  reorderWatchlistGroups,
} from '../../api/watchlist'
import { queryKeys } from '../queryKeys'

export function useWatchlistGroupsQuery(enabled = true) {
  return useQuery({
    queryKey: queryKeys.watchlistGroups,
    queryFn: getWatchlistGroups,
    enabled,
  })
}

export function useCreateWatchlistGroup() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (name: string) => createWatchlistGroup(name),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.watchlistGroups })
    },
  })
}

export function useRenameWatchlistGroup() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ groupId, name }: { groupId: number; name: string }) => renameWatchlistGroup(groupId, name),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.watchlistGroups })
    },
  })
}

export function useDeleteWatchlistGroup() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (groupId: number) => deleteWatchlistGroup(groupId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.watchlistGroups })
      // 그룹이 삭제되면 소속 종목이 미분류로 이동하므로 관심 목록도 갱신한다.
      void queryClient.invalidateQueries({ queryKey: queryKeys.watchlist })
    },
  })
}

export function useReorderWatchlistGroups() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (groupIds: number[]) => reorderWatchlistGroups(groupIds),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.watchlistGroups })
    },
  })
}
