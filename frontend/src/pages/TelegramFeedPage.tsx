import { useEffect, useState } from 'react'
import { TelegramFeedCard } from '../components/telegramfeed/TelegramFeedCard'
import { LoadingSpinner } from '../components/common/LoadingSpinner'
import { EmptyState } from '../components/common/EmptyState'
import { DateStepper } from '../components/common/DateStepper'
import { FilterChipGroup } from '../components/common/FilterChipGroup'
import { useTelegramFeedChannelsQuery, useTelegramFeedQuery } from '../hooks/queries/useTelegramFeed'
import { useDateSkipNavigation } from '../hooks/useDateSkipNavigation'

const DATE_COOKIE_NAME = 'telegramFeedDate'

// 텔레그램 투자 채널 신규 글을 AI가 요약+종목 태깅한 결과를 최신순으로
// 보여준다(Phase 8 P7-F2). VideoFeedPage(유튜브 요약)와 구조는 동일하되
// 텔레그램 글은 제목이 없어 카드가 다르다(TelegramFeedCard 참고). 날짜
// 네비게이션/채널 필터 칩 UI는 P7-F1에서 공용화한 훅/컴포넌트(
// useDateSkipNavigation/DateStepper/FilterChipGroup)를 그대로 쓴다.
export function TelegramFeedPage() {
  const [selectedChannelId, setSelectedChannelId] = useState<number | undefined>(undefined)
  const dateNav = useDateSkipNavigation({ cookieName: DATE_COOKIE_NAME })
  const channelsQuery = useTelegramFeedChannelsQuery()
  const channels = channelsQuery.data ?? []
  const telegramFeedQuery = useTelegramFeedQuery(undefined, selectedChannelId, dateNav.selectedDate)
  const posts = telegramFeedQuery.data?.content ?? []

  // 날짜별로 "글이 없어요" 빈 화면을 보여주는 대신, 조회 가능 범위 안에서
  // 콘텐츠가 있는 날짜를 찾을 때까지 자동으로 계속 넘어간다(VideoFeedPage와
  // 동일 동작).
  useEffect(() => {
    if (telegramFeedQuery.isLoading || posts.length > 0 || !dateNav.canSkipFurther) return
    dateNav.skipDate()
    // eslint-disable-next-line react-hooks/exhaustive-deps -- dateNav.skipDate는 렌더마다 새 함수 참조라 deps에 넣으면 무한 루프
  }, [telegramFeedQuery.isLoading, posts.length, dateNav.canSkipFurther])

  return (
    <div className="mx-auto max-w-2xl space-y-4 px-2">
      <div className="flex items-end justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold text-gray-900">텔레그램 요약</h1>
          <p className="mt-0.5 text-xs text-gray-500">투자 텔레그램 채널의 신규 글을 AI가 요약해드려요</p>
        </div>

        <DateStepper
          selectedDate={dateNav.selectedDate}
          canGoPrev={dateNav.canGoPrev}
          canGoNext={dateNav.canGoNext}
          onPrev={dateNav.goPrev}
          onNext={dateNav.goNext}
        />
      </div>

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

      {(telegramFeedQuery.isLoading || (posts.length === 0 && dateNav.canSkipFurther)) && <LoadingSpinner />}
      {!telegramFeedQuery.isLoading && posts.length === 0 && !dateNav.canSkipFurther && (
        <EmptyState message="아직 요약된 글이 없어요." />
      )}

      <div className="space-y-3">
        {posts.map((post) => (
          <TelegramFeedCard key={post.telegramPostId} post={post} />
        ))}
      </div>
    </div>
  )
}
