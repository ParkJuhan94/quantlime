import { apiClient } from './client'
import type {
  AdminChannel,
  AdminFeedComment,
  AdminFeedPost,
  CollectResult,
  CrossSectionalParams,
  PriceJumpReport,
  PriceRepairMarket,
  RegularCloseBackfillResult,
  TelegramRetentionResult,
} from '../types/admin'
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

// ---- 영상(유튜브) 수집 ----

export async function getVideoChannels(): Promise<AdminChannel[]> {
  const { data } = await apiClient.get<AdminChannel[]>('/api/admin/feed/channels')
  return data
}

export async function collectVideos(): Promise<CollectResult[]> {
  const { data } = await apiClient.post<CollectResult[]>('/api/admin/feed/collect')
  return data
}

// 아래 두 호출의 응답은 처리 결과가 아니라 이벤트 "발행 건수"다(실제 처리는 Kafka 컨슈머가 비동기로 한다)
export async function republishTranscribe(): Promise<number> {
  const { data } = await apiClient.post<number>('/api/admin/feed/transcribe')
  return data
}

export async function republishSummarize(): Promise<number> {
  const { data } = await apiClient.post<number>('/api/admin/feed/summarize')
  return data
}

export async function initializeVelocity(channelId: number): Promise<number> {
  const { data } = await apiClient.post<number>(`/api/admin/feed/channels/${channelId}/velocity/initialize`)
  return data
}

export async function cleanupVideos(): Promise<number> {
  const { data } = await apiClient.post<number>('/api/admin/feed/retention/cleanup')
  return data
}

// ---- 텔레그램 수집 ----

export async function getTelegramChannels(): Promise<AdminChannel[]> {
  const { data } = await apiClient.get<AdminChannel[]>('/api/admin/telegram-feed/channels')
  return data
}

export async function collectTelegram(): Promise<CollectResult[]> {
  const { data } = await apiClient.post<CollectResult[]>('/api/admin/telegram-feed/collect')
  return data
}

export async function republishDigest(): Promise<number> {
  const { data } = await apiClient.post<number>('/api/admin/telegram-feed/digest/generate')
  return data
}

export async function cleanupTelegram(): Promise<TelegramRetentionResult> {
  const { data } = await apiClient.post<TelegramRetentionResult>('/api/admin/telegram-feed/retention/cleanup')
  return data
}

// ---- 백테스트(무거운 작업, 응답은 시작/완료 안내 문자열) ----

export async function prepareBacktestDataset(): Promise<string> {
  const { data } = await apiClient.post<string>('/api/admin/backtest/prepare-dataset')
  return data
}

export async function runBacktest(stockCode: string): Promise<string> {
  const { data } = await apiClient.post<string>('/api/admin/backtest/run', null, { params: { stockCode } })
  return data
}

export async function runUniverseBacktest(force: boolean): Promise<string> {
  const { data } = await apiClient.post<string>('/api/admin/backtest/run-universe', null, { params: { force } })
  return data
}

export async function runCrossSectionalBacktest(params: CrossSectionalParams): Promise<string> {
  const { data } = await apiClient.post<string>('/api/admin/backtest/cross-sectional', null, { params })
  return data
}

// ---- 데이터 운영(시세·스코어 복구, 응답은 완료 안내 문자열이거나 결과 객체) ----

export async function syncDomesticStockMaster(): Promise<string> {
  const { data } = await apiClient.post<string>('/api/admin/data/stock-master/sync')
  return data
}

export async function syncOverseasStockMaster(): Promise<string> {
  const { data } = await apiClient.post<string>('/api/admin/data/stock-master/overseas/sync')
  return data
}

export async function refreshPricesAndScores(stockCode?: string): Promise<string> {
  const { data } = await apiClient.post<string>('/api/admin/data/refresh', null, { params: { stockCode } })
  return data
}

export async function resettlePrices(market: PriceRepairMarket, from: string): Promise<string> {
  const { data } = await apiClient.post<string>('/api/admin/data/prices/resettle', null, { params: { market, from } })
  return data
}

export async function repairAdjustedPrices(from: string, dryRun: boolean): Promise<PriceJumpReport[]> {
  const { data } = await apiClient.post<PriceJumpReport[]>('/api/admin/data/prices/repair-adjusted', null, {
    params: { from, dryRun },
  })
  return data
}

export async function backfillRegularClose(stockCodes: string[], days?: number): Promise<RegularCloseBackfillResult> {
  const { data } = await apiClient.post<RegularCloseBackfillResult>(
    '/api/admin/data/prices/backfill-regular-close',
    null,
    { params: { stockCodes: stockCodes.length > 0 ? stockCodes.join(',') : undefined, days } },
  )
  return data
}

export async function rebuildScores(from: string): Promise<string> {
  const { data } = await apiClient.post<string>('/api/admin/data/scores/rebuild', null, {
    params: { from, confirm: true },
  })
  return data
}
