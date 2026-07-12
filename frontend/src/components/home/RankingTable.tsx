import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import type { MockRankingStock } from '../../mock/marketMock'
import type { PriceBroadcastMessage } from '../../types/realtime'
import { StockLogo } from '../common/StockLogo'
import { changeRateColorClass, formatChangeRate, formatPrice } from '../../utils/priceFormat'
import { buildStockLogoUrl } from '../../utils/stockLogo'
import { useMarketRankingQuery } from '../../hooks/queries/useMarketRanking'

interface RankingTableProps {
  stocks: MockRankingStock[]
  livePrices: Record<string, PriceBroadcastMessage>
  watchlistCodes: Set<string>
  onToggleWatch: (stockCode: string) => void
}

type SortKey = 'amount' | 'volume' | 'gainers' | 'losers'

const SORT_OPTIONS: { key: SortKey; label: string }[] = [
  { key: 'amount', label: '거래대금' },
  { key: 'volume', label: '거래량' },
  { key: 'gainers', label: '급상승' },
  { key: 'losers', label: '급하락' },
]

interface DisplayRow {
  stockCode: string
  stockName: string
  sector: string
  logoUrl: string
  price: number | null
  changeRate: number | null
  amount?: string
  marketCap?: string
}

function sortMockStocks(stocks: MockRankingStock[], sortKey: SortKey): MockRankingStock[] {
  const sorted = [...stocks]
  switch (sortKey) {
    case 'amount':
      return sorted.sort((a, b) => b.amountValue - a.amountValue)
    case 'volume':
      return sorted.sort((a, b) => b.volumeValue - a.volumeValue)
    default:
      return sorted
  }
}

export function RankingTable({ stocks, livePrices, watchlistCodes, onToggleWatch }: RankingTableProps) {
  const navigate = useNavigate()
  const [sortKey, setSortKey] = useState<SortKey>('amount')
  const isRealMode = sortKey === 'gainers' || sortKey === 'losers'

  const rankingQuery = useMarketRankingQuery(sortKey === 'losers' ? 'losers' : 'gainers', 10, isRealMode)

  const displayRows: DisplayRow[] = isRealMode
    ? (rankingQuery.data ?? []).map((row) => ({
        stockCode: row.stockCode,
        stockName: row.stockName,
        sector: row.sector,
        logoUrl: buildStockLogoUrl(row.stockCode),
        price: row.currentPrice,
        changeRate: row.changeRate,
      }))
    : sortMockStocks(stocks, sortKey).map((stock) => {
        const live = livePrices[stock.stockCode]
        return {
          stockCode: stock.stockCode,
          stockName: stock.stockName,
          sector: stock.sector,
          logoUrl: stock.logoUrl,
          price: live?.currentPrice ?? stock.basePrice,
          changeRate: live?.changeRate ?? stock.baseChangeRate,
          amount: stock.amount,
          marketCap: stock.marketCap,
        }
      })

  return (
    <section className="rounded-2xl border border-gray-100 bg-white p-4">
      <div className="mb-3 flex items-center justify-between">
        <div className="flex items-baseline gap-3">
          <h2 className="text-base font-semibold text-gray-900">실시간 시세</h2>
          <Link to="/dashboard" className="text-xs font-medium text-gray-400 hover:text-gray-600">
            관심종목 스코어 랭킹 →
          </Link>
        </div>
        <div className="flex rounded-xl bg-gray-100 p-1">
          {SORT_OPTIONS.map((option) => (
            <button
              key={option.key}
              type="button"
              onClick={() => setSortKey(option.key)}
              className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                sortKey === option.key ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>
      <p className="mb-2 text-xs text-gray-400">
        {isRealMode
          ? '실제 등락률로 정렬한 전종목 랭킹입니다 · 장중에만 갱신됩니다(거래대금/시가총액은 아직 미지원)'
          : '예시 데이터입니다 · 종목/로고/섹터는 실제 정보이며, 클릭하면 실제 상세·차트로 이동합니다'}
      </p>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[560px] text-left">
          <thead>
            <tr className="border-b border-gray-100 text-xs font-medium text-gray-400">
              <th className="w-14 pb-2">순위</th>
              <th className="pb-2">종목</th>
              <th className="pb-2 text-right">현재가</th>
              <th className="pb-2 text-right">등락률</th>
              {!isRealMode && <th className="pb-2 text-right">거래대금</th>}
              {!isRealMode && <th className="pb-2 text-right">시가총액</th>}
              <th className="pb-2 pl-4 text-left">산업</th>
            </tr>
          </thead>
          <tbody>
            {isRealMode && rankingQuery.isLoading && (
              <tr>
                <td colSpan={5} className="py-6 text-center text-sm text-gray-400">
                  불러오는 중...
                </td>
              </tr>
            )}
            {isRealMode && !rankingQuery.isLoading && displayRows.length === 0 && (
              <tr>
                <td colSpan={5} className="py-6 text-center text-sm text-gray-400">
                  장이 열려 있지 않거나 아직 데이터가 준비되지 않았습니다.
                </td>
              </tr>
            )}
            {displayRows.map((row, index) => {
              const isWatched = watchlistCodes.has(row.stockCode)
              return (
                <tr
                  key={row.stockCode}
                  onClick={() => navigate(`/stocks/${row.stockCode}`)}
                  className="cursor-pointer border-b border-gray-50 hover:bg-gray-50"
                >
                  <td className="py-2.5">
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        aria-label={isWatched ? '관심종목에서 삭제' : '관심종목에 추가'}
                        onClick={(event) => {
                          event.stopPropagation()
                          onToggleWatch(row.stockCode)
                        }}
                      >
                        <svg
                          width="15"
                          height="15"
                          viewBox="0 0 24 24"
                          fill={isWatched ? '#dc2626' : 'none'}
                          stroke={isWatched ? '#dc2626' : '#c6c6c6'}
                          strokeWidth="2"
                        >
                          <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 1 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8Z" />
                        </svg>
                      </button>
                      <span className="text-xs font-semibold text-gray-300">{index + 1}</span>
                    </div>
                  </td>
                  <td className="py-2.5">
                    <div className="flex items-center gap-2.5">
                      <StockLogo logoUrl={row.logoUrl} stockName={row.stockName} className="h-7 w-7" />
                      <div>
                        <p className="text-sm font-semibold text-gray-900">{row.stockName}</p>
                        <p className="text-xs text-gray-400">{row.stockCode}</p>
                      </div>
                    </div>
                  </td>
                  <td className="py-2.5 text-right text-sm font-semibold text-gray-900">
                    {formatPrice(row.price)}
                  </td>
                  <td className={`py-2.5 text-right text-sm font-semibold ${changeRateColorClass(row.changeRate)}`}>
                    {formatChangeRate(row.changeRate)}
                  </td>
                  {!isRealMode && (
                    <td className="py-2.5 text-right text-sm text-gray-600">{row.amount}</td>
                  )}
                  {!isRealMode && (
                    <td className="py-2.5 text-right text-sm text-gray-600">{row.marketCap}</td>
                  )}
                  <td className="py-2.5 pl-4">
                    <span className="rounded-md bg-gray-100 px-2 py-1 text-xs font-medium text-gray-600">
                      {row.sector}
                    </span>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </section>
  )
}
