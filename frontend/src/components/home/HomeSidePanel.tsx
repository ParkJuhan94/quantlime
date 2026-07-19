import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { WatchlistGroupResponse, WatchlistResponse } from '../../types/watchlist'
import type { PriceBroadcastMessage } from '../../types/realtime'
import type { RecentlyViewedStock } from '../../storage/recentlyViewedStorage'
import { StockLogo } from '../common/StockLogo'
import { changeRateColorClass, formatChangeRate, formatPrice } from '../../utils/priceFormat'
import { buildStockLogoUrl } from '../../utils/stockLogo'
import { WatchlistGroupMenu } from './WatchlistGroupMenu'
import { GroupNameDialog } from './GroupNameDialog'
import { WatchlistGroupEditModal } from './WatchlistGroupEditModal'
import { GroupQuickActionModal } from './GroupQuickActionModal'
import { AddStockToGroupPopover } from './AddStockToGroupPopover'
import { FeedbackModal } from '../layout/FeedbackModal'
import { useCreateWatchlistGroup, useRenameWatchlistGroup } from '../../hooks/queries/useWatchlistGroups'

export type SidePanelTab = 'watch' | 'recent'

interface HomeSidePanelProps {
  isAuthenticated: boolean
  watchlist: WatchlistResponse[]
  watchlistGroups: WatchlistGroupResponse[]
  watchlistLoading: boolean
  watchlistLivePrices: Record<string, PriceBroadcastMessage>
  onRemoveWatch: (stockCode: string) => void
  recentlyViewed: RecentlyViewedStock[]
  recentlyViewedLivePrices: Record<string, PriceBroadcastMessage>
  activeTab: SidePanelTab
  collapsed: boolean
  onTabClick: (tab: SidePanelTab) => void
}

const TAB_ICONS: { key: SidePanelTab; label: string }[] = [
  { key: 'watch', label: '관심' },
  { key: 'recent', label: '최근 본' },
]

function StockRow({
  stockCode,
  stockName,
  logoUrl,
  live,
  onRemove,
}: {
  stockCode: string
  stockName: string
  logoUrl: string
  live?: PriceBroadcastMessage
  onRemove?: () => void
}) {
  return (
    <div className="flex items-center justify-between border-b border-gray-50 py-2">
      <Link to={`/stocks/${stockCode}`} className="flex min-w-0 flex-1 items-center gap-2.5">
        <StockLogo logoUrl={logoUrl} stockName={stockName} className="h-7 w-7" />
        <span className="truncate text-sm font-medium text-gray-900">{stockName}</span>
      </Link>
      <div className="flex items-center gap-2">
        <div className="text-right">
          <p className="text-xs font-semibold text-gray-900">{formatPrice(live?.currentPrice)}</p>
          <p className={`text-xs font-light ${changeRateColorClass(live?.changeRate)}`}>
            {formatChangeRate(live?.changeRate)}
          </p>
        </div>
        {onRemove && (
          <button
            type="button"
            aria-label={`${stockName} 관심종목에서 삭제`}
            onClick={onRemove}
            className="text-xs text-gray-300 hover:text-red-600"
          >
            ×
          </button>
        )}
      </div>
    </div>
  )
}

// 관심/최근 본이 비어 있을 때 공통으로 쓰는 빈 상태 - 패널이 전체 높이를
// 차지하게 되면서(App.tsx 전역화) 빈 문구 한 줄만 왼쪽 정렬로 떠 있으면
// 여백이 어색해 아이콘과 함께 세로 중앙에 크게 배치한다. 로그인이 필요한
// 경우는 "관심"과 무관한 문서/페이지 아이콘을 쓴다.
function EmptyState({ icon, message }: { icon: 'heart' | 'clock' | 'document'; message: string }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-3 text-center">
      <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" strokeWidth="1.6">
        {icon === 'heart' && (
          <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 1 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8Z" />
        )}
        {icon === 'clock' && (
          <>
            <circle cx="12" cy="12" r="9" />
            <path d="M12 7v5l3 3" />
          </>
        )}
        {icon === 'document' && (
          <>
            <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V9l-5-6Z" />
            <path d="M14 3v6h5" />
            <path d="M9 13h6M9 17h6" />
          </>
        )}
      </svg>
      <p className="text-base font-semibold text-gray-400">{message}</p>
    </div>
  )
}

// 그룹 하나(또는 미분류 묶음)를 접었다 펼쳤다 할 수 있는 섹션. 토글
// 아이콘은 패널 접기/펼치기 버튼과 동일한 두줄 화살표(<<) 디자인을
// 그대로 재사용하고(회전 애니메이션 포함), 행의 오른쪽 끝에 둔다.
function GroupSection({
  title,
  count,
  items,
  watchlistLivePrices,
  onRemoveWatch,
  onTitleClick,
}: {
  title: string
  count: number
  items: WatchlistResponse[]
  watchlistLivePrices: Record<string, PriceBroadcastMessage>
  onRemoveWatch: (stockCode: string) => void
  onTitleClick: () => void
}) {
  const [expanded, setExpanded] = useState(true)

  return (
    <div className="border-b border-gray-50 py-1.5">
      <div className="flex w-full items-center justify-between rounded-lg px-1 py-1.5 hover:bg-gray-50">
        <button
          type="button"
          aria-label={`${title} 그룹 관리`}
          onClick={(event) => {
            event.stopPropagation()
            onTitleClick()
          }}
          className="flex items-center gap-1.5 rounded px-0.5 hover:underline"
        >
          <span className="text-xs font-semibold text-gray-500">{title}</span>
          <span className="text-xs text-gray-300">{count}</span>
        </button>
        <button
          type="button"
          aria-label={expanded ? '그룹 접기' : '그룹 펼치기'}
          onClick={() => setExpanded((prev) => !prev)}
          className="flex shrink-0 items-center justify-center rounded p-1"
        >
          <svg
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill="none"
            stroke="#999"
            strokeWidth="2"
            className={`transition-transform duration-300 ${expanded ? 'rotate-90' : '-rotate-90'}`}
          >
            <path d="M11 17l-5-5 5-5M18 17l-5-5 5-5" />
          </svg>
        </button>
      </div>
      {expanded && (
        <div className="flex flex-col pl-1">
          {items.map((item) => (
            <StockRow
              key={item.id}
              stockCode={item.stockCode}
              stockName={item.stockName}
              logoUrl={buildStockLogoUrl(item.stockCode)}
              live={watchlistLivePrices[item.stockCode]}
              onRemove={() => onRemoveWatch(item.stockCode)}
            />
          ))}
        </div>
      )}
    </div>
  )
}

export function HomeSidePanel({
  isAuthenticated,
  watchlist,
  watchlistGroups,
  watchlistLoading,
  watchlistLivePrices,
  onRemoveWatch,
  recentlyViewed,
  recentlyViewedLivePrices,
  activeTab,
  collapsed,
  onTabClick,
}: HomeSidePanelProps) {
  const [createGroupOpen, setCreateGroupOpen] = useState(false)
  const [editModalOpen, setEditModalOpen] = useState(false)
  const [quickActionGroup, setQuickActionGroup] = useState<WatchlistGroupResponse | null>(null)
  const [renameGroupTarget, setRenameGroupTarget] = useState<WatchlistGroupResponse | null>(null)
  const [addStockGroupId, setAddStockGroupId] = useState<number | null>(null)
  const [feedbackOpen, setFeedbackOpen] = useState(false)
  const createGroup = useCreateWatchlistGroup()
  const renameGroup = useRenameWatchlistGroup()

  return (
    <div className="flex h-full overflow-hidden border-l border-gray-100 bg-white">
      <div
        className="overflow-hidden transition-[width] duration-200"
        style={{ width: collapsed ? 0 : 300 }}
      >
        <div className="flex h-full w-[300px] flex-col p-4">
          {activeTab === 'watch' && (
            <>
              <div className="mb-2 flex items-center justify-between">
                <h3 className="text-sm font-semibold text-gray-900">관심</h3>
                {isAuthenticated && (
                  <WatchlistGroupMenu
                    onEditGroups={() => setEditModalOpen(true)}
                    onCreateGroup={() => setCreateGroupOpen(true)}
                  />
                )}
              </div>
              {!isAuthenticated && <EmptyState icon="document" message="로그인이 필요해요" />}
              {isAuthenticated && watchlistLoading && (
                <p className="text-xs text-gray-400">불러오는 중...</p>
              )}
              {isAuthenticated && !watchlistLoading && watchlist.length === 0 && (
                <EmptyState icon="heart" message="관심 종목이 없어요" />
              )}
              {isAuthenticated && watchlist.length > 0 && watchlistGroups.length === 0 && (
                <div className="flex flex-col overflow-y-auto">
                  {watchlist.map((item) => (
                    <StockRow
                      key={item.id}
                      stockCode={item.stockCode}
                      stockName={item.stockName}
                      logoUrl={buildStockLogoUrl(item.stockCode)}
                      live={watchlistLivePrices[item.stockCode]}
                      onRemove={() => onRemoveWatch(item.stockCode)}
                    />
                  ))}
                </div>
              )}
              {isAuthenticated && watchlistGroups.length > 0 && (
                <div className="flex flex-col overflow-y-auto">
                  {watchlistGroups.map((group) => (
                    <GroupSection
                      key={group.id}
                      title={group.name}
                      count={watchlist.filter((item) => item.groupId === group.id).length}
                      items={watchlist.filter((item) => item.groupId === group.id)}
                      watchlistLivePrices={watchlistLivePrices}
                      onRemoveWatch={onRemoveWatch}
                      onTitleClick={() => setQuickActionGroup(group)}
                    />
                  ))}
                </div>
              )}
            </>
          )}

          {activeTab === 'recent' && (
            <>
              <h3 className="mb-2 text-sm font-semibold text-gray-900">최근 본</h3>
              {recentlyViewed.length === 0 && <EmptyState icon="clock" message="최근에 본 종목이 없어요" />}
              {recentlyViewed.length > 0 && (
                <div className="flex flex-col overflow-y-auto">
                  {recentlyViewed.map((stock) => (
                    <StockRow
                      key={stock.stockCode}
                      stockCode={stock.stockCode}
                      stockName={stock.stockName}
                      logoUrl={stock.logoUrl}
                      live={recentlyViewedLivePrices[stock.stockCode]}
                    />
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      </div>

      <div className="flex w-14 shrink-0 flex-col items-center gap-2 border-l border-gray-100 bg-gray-50 py-4">
        <div className="mb-1 flex w-full flex-col items-center border-b border-gray-100 pb-2">
          <button
            type="button"
            onClick={() => onTabClick(activeTab)}
            aria-label={collapsed ? '패널 펼치기' : '패널 접기'}
            className="flex h-8 w-8 items-center justify-center rounded-lg hover:bg-gray-100"
          >
            <svg
              width="17"
              height="17"
              viewBox="0 0 24 24"
              fill="none"
              stroke="#999"
              strokeWidth="2"
              className={`transition-transform duration-300 ${collapsed ? 'rotate-180' : ''}`}
            >
              <path d="M11 17l-5-5 5-5M18 17l-5-5 5-5" />
            </svg>
          </button>
        </div>
        {TAB_ICONS.map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => onTabClick(tab.key)}
            className={`flex flex-col items-center gap-1 rounded-lg px-1 py-1.5 transition ${
              activeTab === tab.key && !collapsed ? 'bg-gray-100' : 'hover:bg-gray-100'
            }`}
          >
            <TabIcon tab={tab.key} active={activeTab === tab.key && !collapsed} />
            <span
              className={`text-[10px] font-semibold ${
                activeTab === tab.key && !collapsed ? 'text-gray-900' : 'text-gray-400'
              }`}
            >
              {tab.label}
            </span>
          </button>
        ))}

        <button
          type="button"
          onClick={() => setFeedbackOpen(true)}
          aria-label="의견 보내기"
          className="mt-auto flex flex-col items-center gap-1 rounded-lg px-1 py-1.5 hover:bg-gray-100"
        >
          <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="#999" strokeWidth="2">
            <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5Z" />
          </svg>
          <span className="text-[10px] font-semibold text-gray-400">의견</span>
        </button>
      </div>

      <FeedbackModal open={feedbackOpen} onClose={() => setFeedbackOpen(false)} />

      <GroupNameDialog
        open={createGroupOpen}
        title="그룹 추가하기"
        submitLabel="추가"
        onClose={() => setCreateGroupOpen(false)}
        onSubmit={(name) => {
          createGroup.mutate(name)
          setCreateGroupOpen(false)
        }}
      />
      <WatchlistGroupEditModal
        open={editModalOpen}
        onClose={() => setEditModalOpen(false)}
        watchlist={watchlist}
        groups={watchlistGroups}
      />

      {quickActionGroup && (
        <GroupQuickActionModal
          groupName={quickActionGroup.name}
          onClose={() => setQuickActionGroup(null)}
          onRename={() => {
            setRenameGroupTarget(quickActionGroup)
            setQuickActionGroup(null)
          }}
          onAddStock={() => {
            setAddStockGroupId(quickActionGroup.id)
            setQuickActionGroup(null)
          }}
        />
      )}

      <GroupNameDialog
        open={renameGroupTarget !== null}
        title="그룹 이름 변경"
        initialName={renameGroupTarget?.name}
        submitLabel="변경"
        onClose={() => setRenameGroupTarget(null)}
        onSubmit={(name) => {
          if (renameGroupTarget) {
            renameGroup.mutate({ groupId: renameGroupTarget.id, name })
          }
          setRenameGroupTarget(null)
        }}
      />

      {addStockGroupId !== null && (
        <AddStockToGroupPopover
          groupId={addStockGroupId}
          watchlist={watchlist}
          onClose={() => setAddStockGroupId(null)}
        />
      )}
    </div>
  )
}

function TabIcon({ tab, active }: { tab: SidePanelTab; active: boolean }) {
  const color = active ? '#111827' : '#999'
  if (tab === 'watch') {
    return (
      <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2">
        <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 1 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8Z" />
      </svg>
    )
  }
  return (
    <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 3" />
    </svg>
  )
}
