import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useStockSearch } from '../../hooks/queries/useStockSearch'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { searchHistoryStorage } from '../../storage/searchHistoryStorage'
import { StockLogo } from '../common/StockLogo'
import { usePopularStocksQuery } from '../../hooks/queries/usePopularStocks'
import type { StockDetailResponse } from '../../types/stock'
import { useAuth } from '../../auth/useAuth'
import { useRemoveWatchlist, useWatchlistQuery } from '../../hooks/queries/useWatchlist'
import { useWatchlistGroupsQuery } from '../../hooks/queries/useWatchlistGroups'
import { AddToWatchlistGroupPicker } from '../home/AddToWatchlistGroupPicker'

interface SearchOverlayProps {
  open: boolean
  onClose: () => void
}

// 관심종목을 목록 맨 위로 올린다 - Array.prototype.sort는 스펙상 안정
// 정렬이라 동점(관심종목 여부만 비교) 항목끼리는 원래 순서(검색
// 관련도·인기순)가 그대로 유지된다.
function sortWatchedFirst<T extends { stockCode: string }>(items: T[], watchlistCodes: Set<string>): T[] {
  return [...items].sort((a, b) => {
    const aWatched = watchlistCodes.has(a.stockCode) ? 0 : 1
    const bWatched = watchlistCodes.has(b.stockCode) ? 0 : 1
    return aWatched - bWatched
  })
}

function WatchHeartButton({
  isWatched,
  onToggle,
}: {
  isWatched: boolean
  onToggle: () => void
}) {
  // 종목상세 하트 버튼과 동일하게 border+배경 박스 스타일로 통일한다
  // (2026-07-17 피드백).
  return (
    <button
      type="button"
      aria-label={isWatched ? '관심종목에서 삭제' : '관심종목에 추가'}
      onClick={(event) => {
        event.stopPropagation()
        onToggle()
      }}
      className={`mr-1 shrink-0 rounded-lg border p-1 transition ${
        isWatched ? 'border-red-200 bg-red-50 hover:bg-red-100' : 'border-gray-200 hover:bg-gray-50'
      }`}
    >
      <svg width="14" height="14" viewBox="0 0 24 24" fill={isWatched ? '#dc2626' : 'none'} stroke={isWatched ? '#dc2626' : '#c6c6c6'} strokeWidth="2">
        <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 1 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8Z" />
      </svg>
    </button>
  )
}

export function SearchOverlay({ open, onClose }: SearchOverlayProps) {
  const navigate = useNavigate()
  const inputRef = useRef<HTMLInputElement>(null)
  const [query, setQuery] = useState('')
  const [recentSearches, setRecentSearches] = useState<string[]>([])
  const [addTargetStockCode, setAddTargetStockCode] = useState<string | null>(null)
  const [highlightedIndex, setHighlightedIndex] = useState(0)
  const debouncedQuery = useDebouncedValue(query, 300)
  const searchQuery = useStockSearch(debouncedQuery)
  const popularStocksQuery = usePopularStocksQuery(5)
  const resultRefs = useRef<(HTMLLIElement | null)[]>([])

  const recentSearchScrollRef = useRef<HTMLDivElement>(null)
  const [canScrollRecentSearches, setCanScrollRecentSearches] = useState(false)

  // 최근 검색이 가로 스크롤 가능하다는 걸 알려주려고, 아직 끝까지 스크롤
  // 안 한 상태에서만 오른쪽 끝에 그라데이션 힌트를 보여준다(스크롤하면
  // 자연히 옅어져 사라짐 - 2026-07-17 피드백).
  function updateScrollHint() {
    const el = recentSearchScrollRef.current
    if (!el) return
    setCanScrollRecentSearches(el.scrollWidth - el.scrollLeft - el.clientWidth > 4)
  }

  const { isAuthenticated } = useAuth()
  const watchlistQuery = useWatchlistQuery(isAuthenticated)
  const groupsQuery = useWatchlistGroupsQuery(isAuthenticated)
  const removeWatchlist = useRemoveWatchlist()
  const watchlist = isAuthenticated ? watchlistQuery.data ?? [] : []
  const watchlistGroups = isAuthenticated ? groupsQuery.data ?? [] : []
  const watchlistCodes = new Set(watchlist.map((item) => item.stockCode))

  // 관심종목 등록은 항상 그룹 지정이 필요하다("미분류" 폐지) - 삭제는
  // 바로 처리하되, 추가는 그룹 선택 모달을 띄운다(HomePage/StockDetailPage와
  // 동일한 패턴).
  function toggleWatch(stockCode: string) {
    if (watchlistCodes.has(stockCode)) {
      removeWatchlist.mutate(stockCode)
    } else {
      setAddTargetStockCode(stockCode)
    }
  }

  useEffect(() => {
    if (open) {
      setQuery('')
      setHighlightedIndex(0)
      setRecentSearches(searchHistoryStorage.read())
      // 오버레이 진입 애니메이션 없이 즉시 열리므로 다음 프레임에 포커스.
      requestAnimationFrame(() => inputRef.current?.focus())
    }
  }, [open])

  // 검색어가 바뀌면(새 결과 세트) 하이라이트를 항상 맨 위로 되돌린다.
  useEffect(() => {
    setHighlightedIndex(0)
  }, [debouncedQuery])

  // 방향키로 하이라이트가 목록 가시 영역을 벗어나면 스크롤해서 따라간다.
  useEffect(() => {
    resultRefs.current[highlightedIndex]?.scrollIntoView({ block: 'nearest' })
  }, [highlightedIndex])

  // recentSearches가 채워진 다음 프레임에야 스크롤 컨테이너의 실제
  // scrollWidth를 잴 수 있다 - 렌더 직후 한 번 측정해 힌트 표시 여부를 정한다.
  useEffect(() => {
    requestAnimationFrame(updateScrollHint)
  }, [recentSearches])

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

  const isSearching = debouncedQuery.trim().length > 0
  const sortedSearchResults = sortWatchedFirst(searchQuery.data?.content ?? [], watchlistCodes)

  function handleInputKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (!isSearching || sortedSearchResults.length === 0) return
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setHighlightedIndex((prev) => Math.min(prev + 1, sortedSearchResults.length - 1))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setHighlightedIndex((prev) => Math.max(prev - 1, 0))
    } else if (event.key === 'Enter') {
      const target = sortedSearchResults[highlightedIndex]
      if (target) goToStock(target.stockCode, target.stockName)
    }
  }

  return (
    <div
      className="fixed inset-0 z-40 flex justify-center bg-black/35 pt-16"
      onClick={onClose}
    >
      <div
        onClick={(event) => event.stopPropagation()}
        className="h-fit max-h-[70vh] w-full max-w-[360px] overflow-y-auto rounded-2xl bg-white p-5 shadow-2xl"
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
              {sortedSearchResults.map((stock: StockDetailResponse, index) => (
                <li
                  key={stock.stockCode}
                  ref={(el) => {
                    resultRefs.current[index] = el
                  }}
                  onMouseEnter={() => setHighlightedIndex(index)}
                  className={`flex items-center rounded-lg ${
                    index === highlightedIndex ? 'bg-gray-100' : 'hover:bg-gray-50'
                  }`}
                >
                  <button
                    type="button"
                    onClick={() => goToStock(stock.stockCode, stock.stockName)}
                    className="flex flex-1 items-center gap-3 px-2 py-2 text-left"
                  >
                    <StockLogo logoUrl={stock.logoUrl} stockName={stock.stockName} className="h-7 w-7" />
                    <span className="text-sm font-medium text-gray-900">{stock.stockName}</span>
                    <span className="text-xs text-gray-400">{stock.stockCode}</span>
                  </button>
                  {isAuthenticated && (
                    <WatchHeartButton
                      isWatched={watchlistCodes.has(stock.stockCode)}
                      onToggle={() => toggleWatch(stock.stockCode)}
                    />
                  )}
                </li>
              ))}
            </ul>
          </div>
        ) : (
          <>
            {recentSearches.length > 0 && (
              <div className="mb-5">
                <p className="mb-2 text-xs font-semibold text-gray-400">최근 검색</p>
                {/* 모달 폭을 줄이면서(30% 축소) 최대 10개까지 늘어난 최근
                    검색어가 폭보다 넓어질 수 있다 - 줄바꿈 대신 가로
                    스크롤로 한 줄에 담는다(2026-07-17). 오른쪽 끝
                    그라데이션은 스크롤 여지가 남아있을 때만 보이다가,
                    끝까지 스크롤하면 옅어져 사라진다 - 스크롤 가능하다는
                    걸 알리는 심플한 힌트(2026-07-17 피드백). */}
                <div className="relative">
                  <div
                    ref={recentSearchScrollRef}
                    onScroll={updateScrollHint}
                    className="scrollbar-hide flex flex-nowrap gap-2 overflow-x-auto pb-1"
                  >
                    {recentSearches.map((term) => (
                      <span
                        key={term}
                        className="flex shrink-0 items-center gap-1.5 rounded-full bg-gray-100 px-3 py-1 text-xs text-gray-600"
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
                  <div
                    aria-hidden
                    className={`pointer-events-none absolute right-0 top-0 h-full w-8 bg-gradient-to-r from-transparent to-white transition-opacity duration-300 ${
                      canScrollRecentSearches ? 'opacity-100' : 'opacity-0'
                    }`}
                  />
                </div>
              </div>
            )}

            {popularStocksQuery.data && popularStocksQuery.data.length > 0 && (
              <div>
                <p className="mb-2 text-xs font-semibold text-gray-400">인기 종목</p>
                <ul className="flex flex-col">
                  {sortWatchedFirst(popularStocksQuery.data, watchlistCodes).map((stock, index) => (
                    <li key={stock.stockCode} className="flex items-center rounded-lg hover:bg-gray-50">
                      <button
                        type="button"
                        onClick={() => goToStock(stock.stockCode, stock.stockName)}
                        className="flex flex-1 items-center gap-3 px-2 py-1.5 text-left"
                      >
                        <span className="w-3.5 text-xs font-semibold text-gray-300">{index + 1}</span>
                        <StockLogo logoUrl={stock.logoUrl} stockName={stock.stockName} className="h-6 w-6" />
                        <span className="text-sm font-medium text-gray-900">{stock.stockName}</span>
                      </button>
                      {isAuthenticated && (
                        <WatchHeartButton
                          isWatched={watchlistCodes.has(stock.stockCode)}
                          onToggle={() => toggleWatch(stock.stockCode)}
                        />
                      )}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </>
        )}

        <div className="mt-4 flex gap-4 border-t border-gray-100 pt-3 text-xs text-gray-400">
          {isSearching && sortedSearchResults.length > 0 && <span>↑↓ 탐색</span>}
          <span>↵ 종목으로 이동하기</span>
          <span>ESC 닫기</span>
        </div>
      </div>

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
