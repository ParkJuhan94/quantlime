import { useState } from 'react'
import { useAuth } from '../auth/useAuth'
import { useRemoveWatchlist, useWatchlistQuery } from '../hooks/queries/useWatchlist'
import { useWatchlistGroupsQuery } from '../hooks/queries/useWatchlistGroups'
import { MarketIndexRow } from '../components/home/MarketIndexRow'
import { RankingTable } from '../components/home/RankingTable'
import { AddToWatchlistGroupPicker } from '../components/home/AddToWatchlistGroupPicker'

export function HomePage() {
  const { isAuthenticated } = useAuth()
  const watchlistQuery = useWatchlistQuery(isAuthenticated)
  const groupsQuery = useWatchlistGroupsQuery(isAuthenticated)
  const removeWatchlist = useRemoveWatchlist()
  const [addTargetStockCode, setAddTargetStockCode] = useState<string | null>(null)

  const watchlist = isAuthenticated ? watchlistQuery.data ?? [] : []
  const watchlistGroups = isAuthenticated ? groupsQuery.data ?? [] : []
  const watchlistCodes = new Set(watchlist.map((item) => item.stockCode))

  // 관심종목 등록은 항상 그룹 지정이 필요하다("미분류" 폐지) - 삭제는
  // 바로 처리하되, 추가는 그룹 선택 모달을 띄운다.
  function toggleWatch(stockCode: string) {
    if (watchlistCodes.has(stockCode)) {
      removeWatchlist.mutate(stockCode)
    } else {
      setAddTargetStockCode(stockCode)
    }
  }

  return (
    <div className="space-y-4">
      <MarketIndexRow />
      <RankingTable watchlistCodes={watchlistCodes} onToggleWatch={toggleWatch} />
      <p className="px-4 py-3 text-center text-xs leading-relaxed text-gray-400">
        퀀트라임에서 제공되는 스코어·코멘트는 투자 판단을 위한 단순 참고용 정보이며, 투자 제안이나 종목 추천이 아닙니다.
        <br />
        투자 판단과 그 책임은 본인에게 있습니다.
      </p>
      {addTargetStockCode && (
        <AddToWatchlistGroupPicker
          stockCode={addTargetStockCode}
          groups={watchlistGroups}
          onClose={() => setAddTargetStockCode(null)}
        />
      )}
    </div>
  )
}
