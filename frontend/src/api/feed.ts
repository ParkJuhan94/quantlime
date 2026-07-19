import { apiClient } from './client'
import type { FeedCommentResponse, FeedPostResponse } from '../types/feed'
import type { PageResponse } from '../types/stock'

export async function createFeedPost(
  category: string,
  title: string,
  imageUrl?: string | null,
): Promise<FeedPostResponse> {
  const { data } = await apiClient.post<FeedPostResponse>('/api/feed/posts', { category, title, imageUrl })
  return data
}

export async function getFeedPosts(category?: string): Promise<PageResponse<FeedPostResponse>> {
  const { data } = await apiClient.get<PageResponse<FeedPostResponse>>('/api/feed/posts', {
    params: category ? { category } : undefined,
  })
  return data
}

export async function updateFeedPost(
  postId: number,
  category: string,
  title: string,
  imageUrl?: string | null,
): Promise<FeedPostResponse> {
  const { data } = await apiClient.patch<FeedPostResponse>(`/api/feed/posts/${postId}`, {
    category,
    title,
    imageUrl,
  })
  return data
}

export async function deleteFeedPost(postId: number): Promise<void> {
  await apiClient.delete(`/api/feed/posts/${postId}`)
}

export async function likeFeedPost(postId: number): Promise<void> {
  await apiClient.post(`/api/feed/posts/${postId}/like`)
}

export async function unlikeFeedPost(postId: number): Promise<void> {
  await apiClient.delete(`/api/feed/posts/${postId}/like`)
}

export async function getFeedComments(postId: number): Promise<PageResponse<FeedCommentResponse>> {
  const { data } = await apiClient.get<PageResponse<FeedCommentResponse>>(`/api/feed/posts/${postId}/comments`)
  return data
}

export async function createFeedComment(postId: number, content: string): Promise<FeedCommentResponse> {
  const { data } = await apiClient.post<FeedCommentResponse>(`/api/feed/posts/${postId}/comments`, { content })
  return data
}
