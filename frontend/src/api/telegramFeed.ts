import { apiClient } from './client'
import type { TelegramFeedChannel, TelegramFeedDigest, TelegramFeedDigestDetail } from '../types/telegramFeed'
import type { PageResponse } from '../types/stock'

// api/videoFeed.ts와 구조적으로 동일(Phase 8 P7-F2). 2026-08-15부로 글 단위
// (/posts)가 아니라 채널×날짜 다이제스트 단위(/digests)로 조회한다.
export async function getTelegramDigests(
  tickerCode?: string,
  channelId?: number,
  date?: string,
): Promise<PageResponse<TelegramFeedDigest>> {
  const { data } = await apiClient.get<PageResponse<TelegramFeedDigest>>('/api/telegram-feed/digests', {
    params: { tickerCode, channelId, date },
  })
  return data
}

export async function getTelegramFeedChannels(): Promise<TelegramFeedChannel[]> {
  const { data } = await apiClient.get<TelegramFeedChannel[]>('/api/telegram-feed/channels')
  return data
}

export async function getTelegramDigestDetail(telegramDigestId: number): Promise<TelegramFeedDigestDetail> {
  const { data } = await apiClient.get<TelegramFeedDigestDetail>(`/api/telegram-feed/digests/${telegramDigestId}`)
  return data
}
