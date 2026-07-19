import { useEffect, useState } from 'react'
import { useAuth } from '../../auth/useAuth'
import { useRemoveWatchlist, useWatchlistQuery } from '../../hooks/queries/useWatchlist'
import { useWatchlistGroupsQuery } from '../../hooks/queries/useWatchlistGroups'
import { useStockPricesQuery } from '../../hooks/queries/useStockPrices'
import { useStockPriceSocket } from '../../hooks/useStockPriceSocket'
import { recentlyViewedStorage } from '../../storage/recentlyViewedStorage'
import { HomeSidePanel, type SidePanelTab } from '../home/HomeSidePanel'
import type { PriceBroadcastMessage } from '../../types/realtime'

export const PANEL_CONTENT_WIDTH = 300
export const PANEL_RAIL_WIDTH = 56

interface AppSidePanelProps {
  onWidthChange: (width: number) => void
}

// 헤더/메인 콘텐츠와 별개로 App.tsx 최상위에서 렌더링된다 - 종목상세·피드
// 등 페이지가 바뀌어도 이 컴포넌트는 언마운트되지 않아 접힘 상태·선택된
// 탭이 그대로 유지된다(CLAUDE.md 참고).
export function AppSidePanel({ onWidthChange }: AppSidePanelProps) {
  const { isAuthenticated } = useAuth()
  const watchlistQuery = useWatchlistQuery(isAuthenticated)
  const groupsQuery = useWatchlistGroupsQuery(isAuthenticated)
  const removeWatchlist = useRemoveWatchlist()

  const [panelTab, setPanelTab] = useState<SidePanelTab>('watch')
  const [panelCollapsed, setPanelCollapsed] = useState(false)
  const [recentlyViewed, setRecentlyViewed] = useState(() => recentlyViewedStorage.read())

  const watchlist = isAuthenticated ? watchlistQuery.data ?? [] : []
  const watchlistGroups = isAuthenticated ? groupsQuery.data ?? [] : []
  const watchlistCodes = watchlist.map((item) => item.stockCode)
  // WebSocket 푸시는 장중에만 브로드캐스트된다 - 장마감엔 관심종목이
  // 전부 "-"로만 보이는 문제가 있었다(사용자 리포트, 2026-07-16). REST
  // 폴링을 베이스라인으로 깔고 실시간 푸시가 오면 그걸 우선시하는 이중
  // 소스 패턴으로 수정(StockDetailPage의 livePrice ?? restPrice와 동일).
  const watchlistSocketPrices = useStockPriceSocket(watchlistCodes)
  const watchlistRestPrices = useStockPricesQuery(watchlistCodes)
  const watchlistLivePrices: Record<string, PriceBroadcastMessage> = {}
  watchlistCodes.forEach((code) => {
    const price = watchlistSocketPrices[code] ?? watchlistRestPrices[code]
    if (price) watchlistLivePrices[code] = price
  })
  // 최근 본 종목은 관심종목이 아닌 이상 PriceBroadcastScheduler의 폴링
  // 대상에서 애초에 빠져 있어(관심종목 기준으로만 폴링) WebSocket
  // 구독만으로는 시세가 영원히 오지 않는다 - REST 폴링으로 대체한다.
  const recentlyViewedLivePrices = useStockPricesQuery(recentlyViewed.map((item) => item.stockCode))

  useEffect(() => {
    onWidthChange((panelCollapsed ? 0 : PANEL_CONTENT_WIDTH) + PANEL_RAIL_WIDTH)
  }, [panelCollapsed, onWidthChange])

  // 탭이 "최근 본"으로 바뀔 때마다 localStorage를 다시 읽어 다른 곳에서
  // 방금 본 종목도 반영한다.
  useEffect(() => {
    if (panelTab === 'recent') setRecentlyViewed(recentlyViewedStorage.read())
  }, [panelTab])

  // 같은 탭을 다시 누르면 접고, 다른 탭을 누르면 그 탭으로 펼친다.
  function handleTabClick(tab: SidePanelTab) {
    if (tab === panelTab && !panelCollapsed) {
      setPanelCollapsed(true)
    } else {
      setPanelTab(tab)
      setPanelCollapsed(false)
    }
  }

  return (
    <div
      className="fixed right-0 top-0 z-30 h-screen shrink-0 transition-[width] duration-200 ease-in-out"
      style={{ width: (panelCollapsed ? 0 : PANEL_CONTENT_WIDTH) + PANEL_RAIL_WIDTH }}
    >
      <HomeSidePanel
        isAuthenticated={isAuthenticated}
        watchlist={watchlist}
        watchlistGroups={watchlistGroups}
        watchlistLoading={isAuthenticated && watchlistQuery.isLoading}
        watchlistLivePrices={watchlistLivePrices}
        onRemoveWatch={(stockCode) => removeWatchlist.mutate(stockCode)}
        recentlyViewed={recentlyViewed}
        recentlyViewedLivePrices={recentlyViewedLivePrices}
        activeTab={panelTab}
        collapsed={panelCollapsed}
        onTabClick={handleTabClick}
      />
    </div>
  )
}
