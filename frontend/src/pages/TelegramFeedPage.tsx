import { useEffect, useState } from 'react'
import { TelegramFeedCard } from '../components/telegramfeed/TelegramFeedCard'
import { LoadingSpinner } from '../components/common/LoadingSpinner'
import { EmptyState } from '../components/common/EmptyState'
import { DateStepper } from '../components/common/DateStepper'
import { FilterChipGroup } from '../components/common/FilterChipGroup'
import { useTelegramDigestsQuery, useTelegramFeedChannelsQuery } from '../hooks/queries/useTelegramFeed'
import { useDateSkipNavigation } from '../hooks/useDateSkipNavigation'

const DATE_COOKIE_NAME = 'telegramFeedDate'

// 텔레그램 투자 채널의 하루치 글을 AI가 채널×날짜 단위 다이제스트로 종합
// 요약한 결과를 최신순으로 보여준다(Phase 8 P7-F2, 2026-08-15 다이제스트
// 재설계). VideoFeedPage와 구조는 동일하되 텔레그램은 글이 아니라 다이제스트
// 단위로 조회한다(TelegramFeedCard 참고). 날짜 네비게이션/채널 필터 칩 UI는
// P7-F1에서 공용화한 훅/컴포넌트(useDateSkipNavigation/DateStepper/
// FilterChipGroup)를 그대로 쓴다.
export function TelegramFeedPage() {
  const [selectedChannelId, setSelectedChannelId] = useState<number | undefined>(undefined)
  const dateNav = useDateSkipNavigation({ cookieName: DATE_COOKIE_NAME })
  const channelsQuery = useTelegramFeedChannelsQuery()
  const channels = channelsQuery.data ?? []
  const telegramDigestsQuery = useTelegramDigestsQuery(undefined, selectedChannelId, dateNav.selectedDate)
  const digests = telegramDigestsQuery.data?.content ?? []

  // 날짜별로 "다이제스트가 없어요" 빈 화면을 보여주는 대신, 조회 가능 범위
  // 안에서 콘텐츠가 있는 날짜를 찾을 때까지 자동으로 계속 넘어간다
  // (VideoFeedPage와 동일 동작).
  useEffect(() => {
    if (telegramDigestsQuery.isLoading || digests.length > 0 || !dateNav.canSkipFurther) return
    dateNav.skipDate()
    // eslint-disable-next-line react-hooks/exhaustive-deps -- dateNav.skipDate는 렌더마다 새 함수 참조라 deps에 넣으면 무한 루프
  }, [telegramDigestsQuery.isLoading, digests.length, dateNav.canSkipFurther])

  return (
    <div className="mx-auto max-w-2xl space-y-4 px-2">
      <div className="flex items-end justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold text-gray-900">텔레그램 요약</h1>
          <p className="mt-0.5 text-xs text-gray-500">투자 텔레그램 채널의 하루치 글을 AI가 종합 요약해드려요</p>
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

      {(telegramDigestsQuery.isLoading || (digests.length === 0 && dateNav.canSkipFurther)) && <LoadingSpinner />}
      {!telegramDigestsQuery.isLoading && digests.length === 0 && !dateNav.canSkipFurther && (
        <EmptyState message="아직 요약된 다이제스트가 없어요." />
      )}

      <div className="space-y-3">
        {digests.map((digest) => (
          <TelegramFeedCard key={digest.telegramDigestId} digest={digest} />
        ))}
      </div>
    </div>
  )
}
