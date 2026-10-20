import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createNotice, deleteNotice, getNotice, getNotices, updateNotice } from '../../api/notice'
import type { UpdateNoticeRequest } from '../../types/notice'
import { queryKeys } from '../queryKeys'

export function useNoticesInfiniteQuery() {
  return useInfiniteQuery({
    queryKey: queryKeys.noticesList,
    queryFn: ({ pageParam }) => getNotices(pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage, allPages) => (lastPage.hasNext ? allPages.length : undefined),
  })
}

export function useNoticeQuery(noticeId: number) {
  return useQuery({
    queryKey: queryKeys.notice(noticeId),
    queryFn: () => getNotice(noticeId),
    enabled: Number.isFinite(noticeId),
    // 삭제된 공지(404)를 재시도해 봐야 소용없다
    retry: false,
  })
}

function useInvalidateNotices() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: queryKeys.noticesAll })
}

export function useCreateNotice() {
  const invalidate = useInvalidateNotices()
  return useMutation({ mutationFn: createNotice, onSuccess: () => void invalidate() })
}

export function useUpdateNotice() {
  const invalidate = useInvalidateNotices()
  return useMutation({
    mutationFn: ({ noticeId, request }: { noticeId: number; request: UpdateNoticeRequest }) =>
      updateNotice(noticeId, request),
    onSuccess: () => void invalidate(),
  })
}

export function useDeleteNotice() {
  const invalidate = useInvalidateNotices()
  return useMutation({ mutationFn: deleteNotice, onSuccess: () => void invalidate() })
}
