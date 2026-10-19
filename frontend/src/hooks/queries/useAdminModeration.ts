import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  deleteComment,
  deletePost,
  getHiddenComments,
  getHiddenPosts,
  restoreComment,
  restorePost,
} from '../../api/admin'
import { queryKeys } from '../queryKeys'

export function useHiddenPostsQuery(enabled = true) {
  return useInfiniteQuery({
    queryKey: queryKeys.adminHiddenPosts,
    queryFn: ({ pageParam }) => getHiddenPosts(pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage, allPages) => (lastPage.hasNext ? allPages.length : undefined),
    enabled,
  })
}

export function useHiddenCommentsQuery(enabled = true) {
  return useInfiniteQuery({
    queryKey: queryKeys.adminHiddenComments,
    queryFn: ({ pageParam }) => getHiddenComments(pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage, allPages) => (lastPage.hasNext ? allPages.length : undefined),
    enabled,
  })
}

// 복구·삭제는 글/댓글 목록 어디에 영향을 줄지 모르게 서로 얽혀 있어(글 삭제 시 그 댓글도 삭제) 두 목록을 함께 무효화한다.
function useModerationMutation(mutationFn: (id: number) => Promise<void>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminModerationAll })
    },
  })
}

export const useRestorePost = () => useModerationMutation(restorePost)
export const useRestoreComment = () => useModerationMutation(restoreComment)
export const useDeletePost = () => useModerationMutation(deletePost)
export const useDeleteComment = () => useModerationMutation(deleteComment)
