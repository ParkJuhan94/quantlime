import { useRef, useState } from 'react'
import { useStockSearch } from '../../hooks/queries/useStockSearch'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { StockLogo } from '../common/StockLogo'
import { useAddWatchlist, useMoveWatchlistGroup } from '../../hooks/queries/useWatchlist'
import type { WatchlistResponse } from '../../types/watchlist'

interface AddStockToGroupPopoverProps {
  groupId: number
  watchlist: WatchlistResponse[]
  onClose: () => void
}

// 관심 종목 편집 모달의 "+ 종목 추가" - 검색해서 고르면 관심종목으로
// 등록하고(아직 등록 전이면) 곧바로 지금 보고 있는 그룹에 넣는다.
export function AddStockToGroupPopover({ groupId, watchlist, onClose }: AddStockToGroupPopoverProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query, 300)
  const searchQuery = useStockSearch(debouncedQuery)
  const addWatchlist = useAddWatchlist()
  const moveWatchlistGroup = useMoveWatchlistGroup()

  const watchlistedCodes = new Set(watchlist.map((item) => item.stockCode))

  async function handleSelect(stockCode: string) {
    if (watchlistedCodes.has(stockCode)) {
      moveWatchlistGroup.mutate({ stockCode, groupId })
    } else {
      await addWatchlist.mutateAsync({ stockCode, groupId })
    }
    onClose()
  }

  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/35" onClick={onClose}>
      <div
        ref={containerRef}
        onClick={(event) => event.stopPropagation()}
        className="w-full max-w-sm rounded-2xl bg-white p-5 shadow-2xl"
      >
        <p className="mb-3 text-sm font-semibold text-gray-900">종목 추가</p>
        <input
          type="text"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="종목명 또는 코드로 검색"
          autoFocus
          className="mb-3 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm text-gray-900 outline-none focus:border-gray-400"
        />
        <div className="max-h-64 overflow-y-auto">
          {searchQuery.isLoading && <p className="px-1 py-2 text-sm text-gray-400">검색 중...</p>}
          {debouncedQuery.trim().length > 0 && searchQuery.data?.content.length === 0 && (
            <p className="px-1 py-2 text-sm text-gray-400">검색 결과가 없습니다.</p>
          )}
          <ul className="flex flex-col">
            {searchQuery.data?.content.map((stock) => (
              <li key={stock.stockCode}>
                <button
                  type="button"
                  onClick={() => void handleSelect(stock.stockCode)}
                  className="flex w-full items-center gap-2.5 rounded-lg px-2 py-2 text-left hover:bg-gray-50"
                >
                  <StockLogo logoUrl={stock.logoUrl} stockName={stock.stockName} className="h-7 w-7" />
                  <span className="text-sm font-medium text-gray-900">{stock.stockName}</span>
                  <span className="text-xs text-gray-400">{stock.stockCode}</span>
                  {watchlistedCodes.has(stock.stockCode) && (
                    <span className="ml-auto text-[11px] text-gray-400">관심 등록됨</span>
                  )}
                </button>
              </li>
            ))}
          </ul>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="mt-3 w-full rounded-lg border border-gray-200 py-2 text-sm font-semibold text-gray-700 hover:bg-gray-50"
        >
          닫기
        </button>
      </div>
    </div>
  )
}
