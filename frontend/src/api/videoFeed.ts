import { apiClient } from './client'
import type { VideoFeedDetail, VideoFeedItem } from '../types/videoFeed'
import type { PageResponse } from '../types/stock'

export async function getVideoFeed(tickerCode?: string, date?: string): Promise<PageResponse<VideoFeedItem>> {
  const { data } = await apiClient.get<PageResponse<VideoFeedItem>>('/api/video-feed/videos', {
    params: { tickerCode, date },
  })
  return data
}

export async function getVideoFeedDetail(videoId: number): Promise<VideoFeedDetail> {
  const { data } = await apiClient.get<VideoFeedDetail>(`/api/video-feed/videos/${videoId}`)
  return data
}
