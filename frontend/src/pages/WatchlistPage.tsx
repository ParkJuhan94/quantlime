import { useState } from 'react'
import { useAddWatchlist, useRemoveWatchlist, useWatchlistQuery } from '../hooks/queries/useWatchlist'
import { useStockSearch } from '../hooks/queries/useStockSearch'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { useStockPriceSocket } from '../hooks/useStockPriceSocket'
import { LoadingSpinner } from '../components/common/LoadingSpinner'
import { ErrorState } from '../components/common/ErrorState'
import { EmptyState } from '../components/common/EmptyState'
import { SearchResultItem } from '../components/search/SearchResultItem'
import { WatchlistRow } from '../components/watchlist/WatchlistRow'
import { getErrorMessage } from '../api/errors'

export function WatchlistPage() {
  const [searchInput, setSearchInput] = useState('')
  const debouncedQuery = useDebouncedValue(searchInput, 300)

  const watchlistQuery = useWatchlistQuery()
  const searchQuery = useStockSearch(debouncedQuery)
  const addWatchlist = useAddWatchlist()
  const removeWatchlist = useRemoveWatchlist()

  const watchlistStockCodes = watchlistQuery.data?.map((item) => item.stockCode) ?? []
  const livePrices = useStockPriceSocket(watchlistStockCodes)

  const watchlistCodes = new Set(watchlistStockCodes)
  const isSearching = debouncedQuery.trim().length > 0

  return (
    <div className="space-y-6">
      <section>
        <h1 className="mb-3 text-xl font-semibold text-gray-900">관심 종목</h1>
        <input
          type="text"
          value={searchInput}
          onChange={(event) => setSearchInput(event.target.value)}
          placeholder="종목명 또는 코드로 검색해 추가하세요"
          className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-gray-500 focus:outline-none"
        />
        {isSearching && (
          <ul className="mt-2 divide-y divide-gray-100 rounded-lg border border-gray-200 bg-white">
            {searchQuery.isLoading && <li className="px-3 py-2 text-sm text-gray-500">검색 중...</li>}
            {searchQuery.data?.content.length === 0 && (
              <li className="px-3 py-2 text-sm text-gray-500">검색 결과가 없습니다.</li>
            )}
            {searchQuery.data?.content.map((stock) => (
              <SearchResultItem
                key={stock.stockCode}
                stock={stock}
                disabled={watchlistCodes.has(stock.stockCode) || addWatchlist.isPending}
                onAdd={(stockCode) => addWatchlist.mutate(stockCode)}
              />
            ))}
          </ul>
        )}
      </section>

      <section>
        {watchlistQuery.isLoading && <LoadingSpinner />}
        {watchlistQuery.isError && (
          <ErrorState message={getErrorMessage(watchlistQuery.error, '관심 종목을 불러오지 못했습니다.')} />
        )}
        {watchlistQuery.data && watchlistQuery.data.length === 0 && (
          <EmptyState message="관심 종목이 없습니다. 위 검색으로 추가해보세요." />
        )}
        {watchlistQuery.data && watchlistQuery.data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[420px] text-left">
              <thead>
                <tr className="border-b border-gray-200 text-xs text-gray-500">
                  <th className="pb-2 font-medium">종목</th>
                  <th className="pb-2 font-medium">시장</th>
                  <th className="pb-2 font-medium">섹터</th>
                  <th className="pb-2 text-right font-medium">현재가</th>
                  <th className="pb-2 text-right font-medium">등락률</th>
                  <th className="pb-2"></th>
                </tr>
              </thead>
              <tbody>
                {watchlistQuery.data.map((item) => (
                  <WatchlistRow
                    key={item.id}
                    item={item}
                    livePrice={livePrices[item.stockCode]}
                    onRemove={(stockCode) => removeWatchlist.mutate(stockCode)}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
