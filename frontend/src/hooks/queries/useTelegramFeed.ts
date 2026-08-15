import { useQuery } from '@tanstack/react-query'
import { getTelegramDigestDetail, getTelegramDigests, getTelegramFeedChannels } from '../../api/telegramFeed'
import { queryKeys } from '../queryKeys'

// hooks/queries/useVideoFeed.ts와 구조적으로 동일(Phase 8 P7-F2).
export function useTelegramDigestsQuery(tickerCode?: string, channelId?: number, date?: string) {
  return useQuery({
    queryKey: queryKeys.telegramFeed(tickerCode, channelId, date),
    queryFn: () => getTelegramDigests(tickerCode, channelId, date),
    staleTime: 60_000,
  })
}

// 채널 필터 칩 옵션용 - 새 채널이 추가돼도 프론트 코드 변경 없이 목록에
// 반영되도록 하드코딩하지 않고 서버에서 동적으로 받아온다.
export function useTelegramFeedChannelsQuery() {
  return useQuery({
    queryKey: queryKeys.telegramFeedChannels(),
    queryFn: getTelegramFeedChannels,
    staleTime: 5 * 60_000,
  })
}

// 목록 API는 요약 한 줄만 내려주고 원문 링크 목록/핵심 포인트/고지문은 상세
// API에만 있다 - 카드를 펼칠 때만 enabled로 상세를 불러온다.
export function useTelegramDigestDetailQuery(telegramDigestId: number, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.telegramFeedDetail(telegramDigestId),
    queryFn: () => getTelegramDigestDetail(telegramDigestId),
    enabled,
    staleTime: 60_000,
  })
}
