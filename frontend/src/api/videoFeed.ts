import { apiClient } from './client'
import type { VideoFeedChannel, VideoFeedDetail, VideoFeedItem } from '../types/videoFeed'
import type { PageResponse } from '../types/stock'

export async function getVideoFeed(
  tickerCode?: string,
  channelId?: number,
  date?: string,
): Promise<PageResponse<VideoFeedItem>> {
  const { data } = await apiClient.get<PageResponse<VideoFeedItem>>('/api/video-feed/videos', {
    params: { tickerCode, channelId, date },
  })
  return data
}

export async function getVideoFeedChannels(): Promise<VideoFeedChannel[]> {
  const { data } = await apiClient.get<VideoFeedChannel[]>('/api/video-feed/channels')
  return data
}

export async function getVideoFeedDetail(videoId: number): Promise<VideoFeedDetail> {
  const { data } = await apiClient.get<VideoFeedDetail>(`/api/video-feed/videos/${videoId}`)
  return data
}
