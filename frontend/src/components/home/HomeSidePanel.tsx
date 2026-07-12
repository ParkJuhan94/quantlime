import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import type { WatchlistResponse } from '../../types/watchlist'
import type { PriceBroadcastMessage } from '../../types/realtime'
import { StockLogo } from '../common/StockLogo'
import { changeRateColorClass, formatChangeRate, formatPrice } from '../../utils/priceFormat'
import { buildStockLogoUrl } from '../../utils/stockLogo'
import { recentlyViewedStorage } from '../../storage/recentlyViewedStorage'
import { useMarketRankingQuery } from '../../hooks/queries/useMarketRanking'

interface HomeSidePanelProps {
  watchlist: WatchlistResponse[]
  watchlistLoading: boolean
  watchlistLivePrices: Record<string, PriceBroadcastMessage>
  onRemoveWatch: (stockCode: string) => void
}

type Tab = 'watch' | 'recent' | 'realtime'

const TAB_ICONS: { key: Tab; label: string }[] = [
  { key: 'watch', label: '관심' },
  { key: 'recent', label: '최근 본' },
  { key: 'realtime', label: '실시간' },
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
          <p className={`text-xs font-semibold ${changeRateColorClass(live?.changeRate)}`}>
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

export function HomeSidePanel({
  watchlist,
  watchlistLoading,
  watchlistLivePrices,
  onRemoveWatch,
}: HomeSidePanelProps) {
  const [activeTab, setActiveTab] = useState<Tab>('watch')
  const [collapsed, setCollapsed] = useState(false)
  const [recentlyViewed, setRecentlyViewed] = useState(() => recentlyViewedStorage.read())
  const rankingQuery = useMarketRankingQuery('gainers', 8, activeTab === 'realtime')

  useEffect(() => {
    if (activeTab === 'recent') setRecentlyViewed(recentlyViewedStorage.read())
  }, [activeTab])

  function selectTab(tab: Tab) {
    setActiveTab(tab)
    setCollapsed(false)
  }

  return (
    <div className="flex overflow-hidden rounded-2xl border border-gray-100 bg-white">
      <div
        className="overflow-hidden transition-[width] duration-200"
        style={{ width: collapsed ? 0 : 300 }}
      >
        <div className="flex h-full w-[300px] flex-col p-4">
          {activeTab === 'watch' && (
            <>
              <h3 className="mb-2 text-sm font-semibold text-gray-900">관심</h3>
              {watchlistLoading && <p className="text-xs text-gray-400">불러오는 중...</p>}
              {!watchlistLoading && watchlist.length === 0 && (
                <p className="text-xs text-gray-400">관심 종목이 없습니다. 검색해서 추가해보세요.</p>
              )}
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
            </>
          )}

          {activeTab === 'recent' && (
            <>
              <h3 className="mb-2 text-sm font-semibold text-gray-900">최근 본</h3>
              {recentlyViewed.length === 0 && (
                <p className="text-xs text-gray-400">최근에 본 종목이 없습니다.</p>
              )}
              <div className="flex flex-col overflow-y-auto">
                {recentlyViewed.map((stock) => (
                  <StockRow
                    key={stock.stockCode}
                    stockCode={stock.stockCode}
                    stockName={stock.stockName}
                    logoUrl={stock.logoUrl}
                  />
                ))}
              </div>
            </>
          )}

          {activeTab === 'realtime' && (
            <>
              <h3 className="mb-1 text-sm font-semibold text-gray-900">실시간 급상승</h3>
              {rankingQuery.isLoading && <p className="mb-2 text-xs text-gray-400">불러오는 중...</p>}
              {!rankingQuery.isLoading && (rankingQuery.data?.length ?? 0) === 0 && (
                <p className="mb-2 text-xs text-gray-400">장중에만 데이터가 갱신됩니다.</p>
              )}
              <div className="flex flex-col overflow-y-auto">
                {(rankingQuery.data ?? []).map((stock) => (
                  <StockRow
                    key={stock.stockCode}
                    stockCode={stock.stockCode}
                    stockName={stock.stockName}
                    logoUrl={buildStockLogoUrl(stock.stockCode)}
                    live={{
                      stockCode: stock.stockCode,
                      currentPrice: stock.currentPrice,
                      changeRate: stock.changeRate,
                      timestamp: '',
                    }}
                  />
                ))}
              </div>
            </>
          )}
        </div>
      </div>

      <div className="flex w-14 shrink-0 flex-col items-center gap-2 border-l border-gray-100 bg-gray-50 py-4">
        <button
          type="button"
          aria-label={collapsed ? '패널 펼치기' : '패널 접기'}
          onClick={() => setCollapsed((prev) => !prev)}
          className="mb-1 flex h-7 w-7 items-center justify-center rounded-lg text-sm font-bold text-gray-300 hover:bg-gray-100"
        >
          {collapsed ? '«' : '»'}
        </button>
        {TAB_ICONS.map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => selectTab(tab.key)}
            className="flex flex-col items-center gap-1 rounded-lg px-1 py-1.5 hover:bg-gray-100"
          >
            <TabIcon tab={tab.key} active={activeTab === tab.key && !collapsed} />
            <span
              className={`text-[10px] font-semibold ${
                activeTab === tab.key && !collapsed ? 'text-accent' : 'text-gray-400'
              }`}
            >
              {tab.label}
            </span>
          </button>
        ))}
      </div>
    </div>
  )
}

function TabIcon({ tab, active }: { tab: Tab; active: boolean }) {
  const color = active ? '#3752ff' : '#999'
  if (tab === 'watch') {
    return (
      <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2">
        <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 1 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8Z" />
      </svg>
    )
  }
  if (tab === 'recent') {
    return (
      <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2">
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7v5l3 3" />
      </svg>
    )
  }
  return (
    <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2">
      <path d="M3 17h4v4H3zM10 10h4v11h-4zM17 4h4v17h-4z" />
    </svg>
  )
}
