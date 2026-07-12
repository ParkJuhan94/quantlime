import { useAddWatchlist, useRemoveWatchlist, useWatchlistQuery } from '../hooks/queries/useWatchlist'
import { useStockPriceSocket } from '../hooks/useStockPriceSocket'
import { MarketIndexRow } from '../components/home/MarketIndexRow'
import { RankingTable } from '../components/home/RankingTable'
import { HomeSidePanel } from '../components/home/HomeSidePanel'
import { MOCK_RANKING_STOCKS } from '../mock/marketMock'

export function HomePage() {
  const watchlistQuery = useWatchlistQuery()
  const addWatchlist = useAddWatchlist()
  const removeWatchlist = useRemoveWatchlist()

  const watchlist = watchlistQuery.data ?? []
  const watchlistCodes = new Set(watchlist.map((item) => item.stockCode))
  const watchlistLivePrices = useStockPriceSocket(watchlist.map((item) => item.stockCode))

  const rankingStockCodes = MOCK_RANKING_STOCKS.map((stock) => stock.stockCode)
  const rankingLivePrices = useStockPriceSocket(rankingStockCodes)

  function toggleWatch(stockCode: string) {
    if (watchlistCodes.has(stockCode)) {
      removeWatchlist.mutate(stockCode)
    } else {
      addWatchlist.mutate(stockCode)
    }
  }

  return (
    <div className="flex gap-4">
      <div className="min-w-0 flex-1 space-y-4">
        <MarketIndexRow />
        <RankingTable
          stocks={MOCK_RANKING_STOCKS}
          livePrices={rankingLivePrices}
          watchlistCodes={watchlistCodes}
          onToggleWatch={toggleWatch}
        />
      </div>
      <div className="w-[354px] shrink-0">
        <HomeSidePanel
          watchlist={watchlist}
          watchlistLoading={watchlistQuery.isLoading}
          watchlistLivePrices={watchlistLivePrices}
          onRemoveWatch={(stockCode) => removeWatchlist.mutate(stockCode)}
        />
      </div>
    </div>
  )
}
