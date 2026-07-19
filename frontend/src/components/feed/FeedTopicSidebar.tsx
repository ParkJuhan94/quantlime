import { FEED_CATEGORIES, type FeedFilter } from '../../mock/feedMock'

const TOPIC_EMOJIS: Record<FeedFilter, string> = {
  전체: '📋',
  국내주식토론: '🇰🇷',
  미국주식이야기: '🇺🇸',
  아무말대잔치: '💬',
}

const FILTERS: FeedFilter[] = ['전체', ...FEED_CATEGORIES]

interface FeedTopicSidebarProps {
  active: FeedFilter
  onSelect: (filter: FeedFilter) => void
}

// 실제 토스증권 피드의 "주제별 커뮤니티" 목록(이모지 아이콘 + 카테고리명)을
// 참고했다 - 게시글 영역엔 별도 필터 탭을 두지 않고 여기서만 필터링한다.
export function FeedTopicSidebar({ active, onSelect }: FeedTopicSidebarProps) {
  return (
    <aside className="hidden w-56 shrink-0 lg:block">
      <div className="sticky top-6 rounded-2xl border border-gray-100 bg-white p-4">
        <h3 className="mb-2 text-sm font-semibold text-gray-900">주제별 커뮤니티</h3>
        <div className="flex flex-col gap-0.5">
          {FILTERS.map((filter) => (
            <button
              key={filter}
              type="button"
              onClick={() => onSelect(filter)}
              className={`flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-left text-sm transition ${
                active === filter ? 'bg-gray-100 font-semibold text-gray-900' : 'text-gray-600 hover:bg-gray-50'
              }`}
            >
              <span>{TOPIC_EMOJIS[filter]}</span>
              {filter}
            </button>
          ))}
        </div>
      </div>
    </aside>
  )
}
