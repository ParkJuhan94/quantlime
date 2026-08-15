import { apiClient } from './client'
import type { TelegramFeedChannel, TelegramFeedDetail, TelegramFeedPost } from '../types/telegramFeed'
import type { PageResponse } from '../types/stock'

// api/videoFeed.ts와 구조적으로 동일(Phase 8 P7-F2).
export async function getTelegramFeed(
  tickerCode?: string,
  channelId?: number,
  date?: string,
): Promise<PageResponse<TelegramFeedPost>> {
  const { data } = await apiClient.get<PageResponse<TelegramFeedPost>>('/api/telegram-feed/posts', {
    params: { tickerCode, channelId, date },
  })
  return data
}

export async function getTelegramFeedChannels(): Promise<TelegramFeedChannel[]> {
  const { data } = await apiClient.get<TelegramFeedChannel[]>('/api/telegram-feed/channels')
  return data
}

export async function getTelegramFeedDetail(telegramPostId: number): Promise<TelegramFeedDetail> {
  const { data } = await apiClient.get<TelegramFeedDetail>(`/api/telegram-feed/posts/${telegramPostId}`)
  return data
}
