import { useEffect, useState } from 'react'
import { VideoFeedCard } from '../components/videofeed/VideoFeedCard'
import { LoadingSpinner } from '../components/common/LoadingSpinner'
import { EmptyState } from '../components/common/EmptyState'
import { useVideoFeedQuery } from '../hooks/queries/useVideoFeed'
import { getCookie, setCookie } from '../utils/cookie'
import {
  VIDEO_FEED_RETENTION_DAYS,
  clampToRetentionWindow,
  formatDayLabel,
  shiftDateString,
  todayDateString,
} from '../utils/dateFilter'

const DATE_COOKIE_NAME = 'videoFeedDate'
const DATE_COOKIE_DAYS = 30

// 조회 가능 범위(오늘~14일 전) 밖에 남아있던 예전 쿠키값이면 범위 안으로
// 당겨온다 - 서버 쪽 VideoRetentionScheduler가 그보다 오래된 데이터를
// 실제로 지우기 때문에, 범위 밖 날짜를 그대로 쓰면 항상 빈 목록만 보인다.
function initialDate(): string {
  const saved = getCookie(DATE_COOKIE_NAME)
  return saved ? clampToRetentionWindow(saved) : todayDateString()
}

// 유튜브 투자 채널(한국경제TV/런던고라니/주덕) 신규 영상을 AI가 요약+종목
// 태깅한 결과를 최신순으로 보여준다(Phase 8 P6, 2026-07-31). 글쓰기/좋아요
// 같은 사용자 상호작용이 없는 읽기 전용 피드라 커뮤니티 피드(FeedPage)와
// 달리 사이드바/작성 카드 없이 목록만 노출한다.
export function VideoFeedPage() {
  const [selectedDate, setSelectedDate] = useState(initialDate)
  const videoFeedQuery = useVideoFeedQuery(undefined, selectedDate)
  const videos = videoFeedQuery.data?.content ?? []

  useEffect(() => {
    setCookie(DATE_COOKIE_NAME, selectedDate, DATE_COOKIE_DAYS)
  }, [selectedDate])

  const oldestSelectableDate = shiftDateString(todayDateString(), -VIDEO_FEED_RETENTION_DAYS)
  const canGoPrev = selectedDate > oldestSelectableDate
  const canGoNext = selectedDate < todayDateString()

  return (
    <div className="mx-auto max-w-2xl space-y-4 px-2">
      <div className="flex items-end justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold text-gray-900">영상 요약</h1>
          <p className="mt-0.5 text-xs text-gray-500">투자 유튜브 채널의 신규 영상을 AI가 요약해드려요</p>
        </div>

        {/* 이전/다음 버튼을 날짜 양옆에 각각 떨어뜨려두면 클릭할 때마다
            손이 날짜 텍스트를 가로질러 이동해야 해 불편하다는 피드백
            (2026-07-31) - 두 버튼을 하나의 테두리 안에 서로 붙여 묶고,
            날짜 표기는 그 왼쪽에 별도로 둔다. */}
        <div className="flex items-center gap-2">
          <span className="min-w-[104px] text-right text-xs font-medium text-gray-700">
            {formatDayLabel(selectedDate)}
          </span>

          <div className="flex items-center overflow-hidden rounded-lg border border-gray-200">
            <button
              type="button"
              disabled={!canGoPrev}
              onClick={() => setSelectedDate((prev) => shiftDateString(prev, -1))}
              aria-label="하루 전"
              className="px-2 py-1.5 text-gray-500 transition hover:bg-gray-100 hover:text-gray-700 disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:bg-transparent"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="m15 18-6-6 6-6" />
              </svg>
            </button>

            <div className="h-4 w-px bg-gray-200" />

            <button
              type="button"
              disabled={!canGoNext}
              onClick={() => setSelectedDate((prev) => shiftDateString(prev, 1))}
              aria-label="하루 다음"
              className="px-2 py-1.5 text-gray-500 transition hover:bg-gray-100 hover:text-gray-700 disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:bg-transparent"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="m9 18 6-6-6-6" />
              </svg>
            </button>
          </div>
        </div>
      </div>

      {videoFeedQuery.isLoading && <LoadingSpinner />}
      {!videoFeedQuery.isLoading && videos.length === 0 && (
        <EmptyState message="이 날짜엔 요약된 영상이 없어요." />
      )}

      <div className="space-y-3">
        {videos.map((video) => (
          <VideoFeedCard key={video.videoId} video={video} />
        ))}
      </div>
    </div>
  )
}
