import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createFeedComment,
  createFeedPost,
  deleteFeedPost,
  getFeedComments,
  getFeedPosts,
  likeFeedPost,
  unlikeFeedPost,
  updateFeedPost,
} from '../../api/feed'
import { queryKeys } from '../queryKeys'
import type { FeedPostResponse } from '../../types/feed'
import type { PageResponse } from '../../types/stock'

export function useFeedPostsQuery(category?: string) {
  return useQuery({
    queryKey: queryKeys.feedPosts(category),
    queryFn: () => getFeedPosts(category),
    staleTime: 30_000,
  })
}

export function useCreateFeedPost() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ category, title, imageUrl }: { category: string; title: string; imageUrl?: string | null }) =>
      createFeedPost(category, title, imageUrl),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.feedPostsAll })
    },
  })
}

// 좋아요 토글은 캐시에 올라와 있는 모든 피드 목록 쿼리(주제별로 쿼리키가
// 갈라져 있음, queryKeys.feedPosts(category) 참고)에서 해당 글을 찾아
// likeCount/likedByMe를 직접 고쳐준다 - invalidate 후 재조회를 기다리면
// 버튼 반응이 한 박자 늦어 보인다.
function patchPostInCaches(
  queryClient: ReturnType<typeof useQueryClient>,
  postId: number,
  patch: (post: FeedPostResponse) => FeedPostResponse,
) {
  queryClient.setQueriesData<PageResponse<FeedPostResponse>>({ queryKey: queryKeys.feedPostsAll }, (data) => {
    if (!data) return data
    return { ...data, content: data.content.map((post) => (post.id === postId ? patch(post) : post)) }
  })
}

export function useLikeFeedPost() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (postId: number) => likeFeedPost(postId),
    onMutate: (postId) => {
      patchPostInCaches(queryClient, postId, (post) => ({
        ...post,
        likedByMe: true,
        likeCount: post.likedByMe ? post.likeCount : post.likeCount + 1,
      }))
    },
    onError: (_error, postId) => {
      patchPostInCaches(queryClient, postId, (post) => ({
        ...post,
        likedByMe: false,
        likeCount: Math.max(0, post.likeCount - 1),
      }))
    },
  })
}

export function useUnlikeFeedPost() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (postId: number) => unlikeFeedPost(postId),
    onMutate: (postId) => {
      patchPostInCaches(queryClient, postId, (post) => ({
        ...post,
        likedByMe: false,
        likeCount: post.likedByMe ? Math.max(0, post.likeCount - 1) : post.likeCount,
      }))
    },
    onError: (_error, postId) => {
      patchPostInCaches(queryClient, postId, (post) => ({
        ...post,
        likedByMe: true,
        likeCount: post.likeCount + 1,
      }))
    },
  })
}

// 수정은 좋아요처럼 낙관적으로 처리하지 않는다 - 카테고리/제목이 바뀌는
// 걸 실패할 경우까지 감안해 되돌리기보다, 서버가 확정한 응답으로 캐시를
// 그대로 덮어쓰는 편이 더 안전하고 간단하다(응답에 likeCount 등도 이미
// 다 채워져 있어 patchPostInCaches의 부분 patch보다 정확함).
export function useUpdateFeedPost(postId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ category, title, imageUrl }: { category: string; title: string; imageUrl?: string | null }) =>
      updateFeedPost(postId, category, title, imageUrl),
    onSuccess: (updated) => {
      patchPostInCaches(queryClient, postId, () => updated)
    },
  })
}

// 삭제된 글은 patch가 아니라 목록에서 아예 제거해야 한다.
function removePostFromCaches(queryClient: ReturnType<typeof useQueryClient>, postId: number) {
  queryClient.setQueriesData<PageResponse<FeedPostResponse>>({ queryKey: queryKeys.feedPostsAll }, (data) => {
    if (!data) return data
    return { ...data, content: data.content.filter((post) => post.id !== postId) }
  })
}

export function useDeleteFeedPost() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (postId: number) => deleteFeedPost(postId),
    onSuccess: (_data, postId) => {
      removePostFromCaches(queryClient, postId)
    },
  })
}

export function useFeedCommentsQuery(postId: number, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.feedComments(postId),
    queryFn: () => getFeedComments(postId),
    enabled,
    staleTime: 10_000,
  })
}

export function useCreateFeedComment(postId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (content: string) => createFeedComment(postId, content),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.feedComments(postId) })
      patchPostInCaches(queryClient, postId, (post) => ({ ...post, commentCount: post.commentCount + 1 }))
    },
  })
}
