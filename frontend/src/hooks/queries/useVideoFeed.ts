import { useQuery } from '@tanstack/react-query'
import { getVideoFeed, getVideoFeedDetail } from '../../api/videoFeed'
import { queryKeys } from '../queryKeys'

export function useVideoFeedQuery(tickerCode?: string, date?: string) {
  return useQuery({
    queryKey: queryKeys.videoFeed(tickerCode, date),
    queryFn: () => getVideoFeed(tickerCode, date),
    staleTime: 60_000,
  })
}

// 목록 API는 요약 한 줄만 내려주고 핵심 포인트/고지문은 상세 API에만 있다
// (백엔드 VideoFeedItemResponse/VideoFeedDetailResponse 분리와 동일한 이유 -
// 목록에서 페이지당 N개 영상의 전체 payload를 매번 다 안 실어보내기 위함).
// 카드를 펼칠 때만 enabled로 상세를 불러온다.
export function useVideoFeedDetailQuery(videoId: number, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.videoFeedDetail(videoId),
    queryFn: () => getVideoFeedDetail(videoId),
    enabled,
    staleTime: 60_000,
  })
}
