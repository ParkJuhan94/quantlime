import { useEffect, useState } from 'react'
import { VideoFeedCard } from '../components/videofeed/VideoFeedCard'
import { LoadingSpinner } from '../components/common/LoadingSpinner'
import { EmptyState } from '../components/common/EmptyState'
import { DateStepper } from '../components/common/DateStepper'
import { FilterChipGroup } from '../components/common/FilterChipGroup'
import { useVideoFeedChannelsQuery, useVideoFeedQuery } from '../hooks/queries/useVideoFeed'
import { useDateSkipNavigation } from '../hooks/useDateSkipNavigation'

const DATE_COOKIE_NAME = 'videoFeedDate'

// 유튜브 투자 채널 신규 영상을 AI가 요약+종목 태깅한 결과를 최신순으로
// 보여준다(Phase 8 P6, 2026-07-31). 글쓰기/좋아요 같은 사용자 상호작용이
// 없는 읽기 전용 피드라 커뮤니티 피드(FeedPage)와 달리 사이드바/작성 카드
// 없이 목록만 노출한다. 채널 목록은 하드코딩하지 않고 /api/video-feed/channels
// 로 동적으로 받아온다(2026-08-09, 채널 필터 추가) - 채널이 늘어날 때마다
// 이 파일을 고칠 필요가 없게 하기 위함. 날짜 네비게이션/채널 필터 칩 UI는
// TelegramFeedPage와 공유하는 공용 훅/컴포넌트로 추출돼 있다(Phase 8 P7-F1).
export function VideoFeedPage() {
  const [selectedChannelId, setSelectedChannelId] = useState<number | undefined>(undefined)
  const dateNav = useDateSkipNavigation({ cookieName: DATE_COOKIE_NAME })
  const channelsQuery = useVideoFeedChannelsQuery()
  const channels = channelsQuery.data ?? []
  const videoFeedQuery = useVideoFeedQuery(undefined, selectedChannelId, dateNav.selectedDate)
  const videos = videoFeedQuery.data?.content ?? []

  // 날짜별로 "영상이 없어요" 빈 화면을 보여주는 대신, 조회 가능 범위 안에서
  // 콘텐츠가 있는 날짜를 찾을 때까지 자동으로 계속 넘어간다(2026-08-09).
  useEffect(() => {
    if (videoFeedQuery.isLoading || videos.length > 0 || !dateNav.canSkipFurther) return
    dateNav.skipDate()
    // eslint-disable-next-line react-hooks/exhaustive-deps -- dateNav.skipDate는 렌더마다 새 함수 참조라 deps에 넣으면 무한 루프
  }, [videoFeedQuery.isLoading, videos.length, dateNav.canSkipFurther])

  return (
    <div className="mx-auto max-w-2xl space-y-4 px-2">
      <div className="flex items-end justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold text-gray-900">영상 요약</h1>
          <p className="mt-0.5 text-xs text-gray-500">투자 유튜브 채널의 신규 영상을 AI가 요약해드려요</p>
        </div>

        <DateStepper
          selectedDate={dateNav.selectedDate}
          canGoPrev={dateNav.canGoPrev}
          canGoNext={dateNav.canGoNext}
          onPrev={dateNav.goPrev}
          onNext={dateNav.goNext}
        />
      </div>

      {/* 채널이 늘어나도 프론트 코드 변경 없이 반영되도록 서버에서 동적으로
          받아온 채널 목록으로 칩을 구성한다. */}
      {channels.length > 0 && (
        <FilterChipGroup
          options={[
            { key: undefined, label: '전체' },
            ...channels.map((channel) => ({ key: channel.channelId, label: channel.name })),
          ]}
          selected={selectedChannelId}
          onSelect={setSelectedChannelId}
        />
      )}

      {(videoFeedQuery.isLoading || (videos.length === 0 && dateNav.canSkipFurther)) && <LoadingSpinner />}
      {!videoFeedQuery.isLoading && videos.length === 0 && !dateNav.canSkipFurther && (
        <EmptyState message="아직 요약된 영상이 없어요." />
      )}

      <div className="space-y-3">
        {videos.map((video) => (
          <VideoFeedCard key={video.videoId} video={video} />
        ))}
      </div>
    </div>
  )
}
