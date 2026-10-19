import { apiClient } from './client'
import type { AdminFeedComment, AdminFeedPost } from '../types/admin'
import type { PageResponse } from '../types/stock'

export interface AdminNotificationRequest {
  title: string
  content: string
  linkUrl?: string
}

// 호출한 관리자 본인에게만 발송 - 전체 발송 전에 내용과 푸시 도착을 확인하는 용도
export async function sendTestNotification(request: AdminNotificationRequest): Promise<void> {
  await apiClient.post('/api/admin/notifications/test', request)
}

// 모든 사용자에게 발송(인앱 알림 저장 + 푸시)
export async function broadcastNotification(request: AdminNotificationRequest): Promise<void> {
  await apiClient.post('/api/admin/notifications/broadcast', request)
}

// ---- 피드 신고 관리(숨김 처리된 글·댓글) ----

export async function getHiddenPosts(page = 0, size = 20): Promise<PageResponse<AdminFeedPost>> {
  const { data } = await apiClient.get<PageResponse<AdminFeedPost>>('/api/admin/feed-moderation/posts/hidden', {
    params: { page, size },
  })
  return data
}

export async function getHiddenComments(page = 0, size = 20): Promise<PageResponse<AdminFeedComment>> {
  const { data } = await apiClient.get<PageResponse<AdminFeedComment>>('/api/admin/feed-moderation/comments/hidden', {
    params: { page, size },
  })
  return data
}

export async function restorePost(postId: number): Promise<void> {
  await apiClient.post(`/api/admin/feed-moderation/posts/${postId}/restore`)
}

export async function restoreComment(commentId: number): Promise<void> {
  await apiClient.post(`/api/admin/feed-moderation/comments/${commentId}/restore`)
}

// 되돌릴 수 없다 - 글과 그 댓글·좋아요·신고 기록이 함께 삭제된다
export async function deletePost(postId: number): Promise<void> {
  await apiClient.delete(`/api/admin/feed-moderation/posts/${postId}`)
}

export async function deleteComment(commentId: number): Promise<void> {
  await apiClient.delete(`/api/admin/feed-moderation/comments/${commentId}`)
}
