import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useStockSearch } from '../../hooks/queries/useStockSearch'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { searchHistoryStorage } from '../../storage/searchHistoryStorage'
import { StockLogo } from '../common/StockLogo'
import { MOCK_RANKING_STOCKS, MOCK_TRENDING_SECTORS } from '../../mock/marketMock'
import type { StockDetailResponse } from '../../types/stock'

interface SearchOverlayProps {
  open: boolean
  onClose: () => void
}

const POPULAR_STOCKS = MOCK_RANKING_STOCKS.slice(0, 5)

export function SearchOverlay({ open, onClose }: SearchOverlayProps) {
  const navigate = useNavigate()
  const inputRef = useRef<HTMLInputElement>(null)
  const [query, setQuery] = useState('')
  const [recentSearches, setRecentSearches] = useState<string[]>([])
  const debouncedQuery = useDebouncedValue(query, 300)
  const searchQuery = useStockSearch(debouncedQuery)

  useEffect(() => {
    if (open) {
      setQuery('')
      setRecentSearches(searchHistoryStorage.read())
      // 오버레이 진입 애니메이션 없이 즉시 열리므로 다음 프레임에 포커스.
      requestAnimationFrame(() => inputRef.current?.focus())
    }
  }, [open])

  useEffect(() => {
    if (!open) return
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [open, onClose])

  if (!open) return null

  function goToStock(stockCode: string, stockName: string) {
    setRecentSearches(searchHistoryStorage.add(stockName))
    onClose()
    navigate(`/stocks/${stockCode}`)
  }

  function handleInputKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (event.key !== 'Enter') return
    const first = searchQuery.data?.content[0]
    if (first) goToStock(first.stockCode, first.stockName)
  }

  const isSearching = debouncedQuery.trim().length > 0

  return (
    <div
      className="fixed inset-0 z-40 flex justify-center bg-black/35 pt-16"
      onClick={onClose}
    >
      <div
        onClick={(event) => event.stopPropagation()}
        className="h-fit max-h-[70vh] w-full max-w-lg overflow-y-auto rounded-2xl bg-white p-5 shadow-2xl"
      >
        <div className="mb-4 flex items-center gap-2.5 rounded-xl bg-gray-100 px-3.5 py-2.5">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#999" strokeWidth="2" className="shrink-0">
            <circle cx="11" cy="11" r="7" />
            <path d="m21 21-4.3-4.3" />
          </svg>
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={handleInputKeyDown}
            placeholder="종목명 또는 코드로 검색"
            className="flex-1 bg-transparent text-sm text-gray-900 outline-none"
          />
        </div>

        {isSearching ? (
          <div>
            <p className="mb-2 text-xs font-semibold text-gray-400">검색 결과</p>
            {searchQuery.isLoading && <p className="px-1 py-2 text-sm text-gray-500">검색 중...</p>}
            {searchQuery.data?.content.length === 0 && (
              <p className="px-1 py-2 text-sm text-gray-500">검색 결과가 없습니다.</p>
            )}
            <ul className="flex flex-col">
              {searchQuery.data?.content.map((stock: StockDetailResponse) => (
                <li key={stock.stockCode}>
                  <button
                    type="button"
                    onClick={() => goToStock(stock.stockCode, stock.stockName)}
                    className="flex w-full items-center gap-3 rounded-lg px-2 py-2 text-left hover:bg-gray-50"
                  >
                    <StockLogo logoUrl={stock.logoUrl} stockName={stock.stockName} className="h-7 w-7" />
                    <span className="text-sm font-medium text-gray-900">{stock.stockName}</span>
                    <span className="text-xs text-gray-400">{stock.stockCode}</span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        ) : (
          <>
            {recentSearches.length > 0 && (
              <div className="mb-5">
                <p className="mb-2 text-xs font-semibold text-gray-400">최근 검색</p>
                <div className="flex flex-wrap gap-2">
                  {recentSearches.map((term) => (
                    <span
                      key={term}
                      className="flex items-center gap-1.5 rounded-full bg-gray-100 px-3 py-1 text-xs text-gray-600"
                    >
                      <button type="button" onClick={() => setQuery(term)} className="hover:underline">
                        {term}
                      </button>
                      <button
                        type="button"
                        aria-label={`${term} 최근 검색 삭제`}
                        onClick={() => setRecentSearches(searchHistoryStorage.remove(term))}
                        className="text-gray-400 hover:text-gray-600"
                      >
                        ×
                      </button>
                    </span>
                  ))}
                </div>
              </div>
            )}

            <div className="mb-5">
              <p className="mb-2 flex items-baseline gap-1.5 text-xs font-semibold text-gray-400">
                인기 종목 <span className="font-normal text-gray-300">· 예시 데이터</span>
              </p>
              <ul className="flex flex-col">
                {POPULAR_STOCKS.map((stock, index) => (
                  <li key={stock.stockCode}>
                    <button
                      type="button"
                      onClick={() => goToStock(stock.stockCode, stock.stockName)}
                      className="flex w-full items-center gap-3 rounded-lg px-2 py-1.5 text-left hover:bg-gray-50"
                    >
                      <span className="w-3.5 text-xs font-semibold text-gray-300">{index + 1}</span>
                      <StockLogo logoUrl={stock.logoUrl} stockName={stock.stockName} className="h-6 w-6" />
                      <span className="text-sm font-medium text-gray-900">{stock.stockName}</span>
                    </button>
                  </li>
                ))}
              </ul>
            </div>

            <div>
              <p className="mb-2 flex items-baseline gap-1.5 text-xs font-semibold text-gray-400">
                지금 뜨는 산업 <span className="font-normal text-gray-300">· 예시 데이터</span>
              </p>
              <ul className="flex flex-col gap-1.5">
                {MOCK_TRENDING_SECTORS.map((sector) => (
                  <li key={sector.name} className="flex items-center justify-between px-2 text-sm">
                    <span className="font-medium text-gray-900">{sector.name}</span>
                    <span className="font-semibold text-red-600">{sector.changeLabel}</span>
                  </li>
                ))}
              </ul>
            </div>
          </>
        )}

        <div className="mt-4 flex gap-4 border-t border-gray-100 pt-3 text-xs text-gray-400">
          <span>↵ 종목으로 이동하기</span>
          <span>ESC 닫기</span>
        </div>
      </div>
    </div>
  )
}
