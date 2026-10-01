import { useState } from 'react'
import {
  useStockOrderbookQuery,
  useStockPriceLimitQuery,
  useStockTradesQuery,
  useStockWarningsQuery,
} from '../../hooks/queries/useStockDetail'
import type { OrderbookLevel } from '../../types/stock'
import { formatPrice } from '../../utils/priceFormat'

type Tab = 'orderbook' | 'trades'

const TABS: { key: Tab; label: string }[] = [
  { key: 'orderbook', label: '호가' },
  { key: 'trades', label: '체결' },
]

function formatVolume(value: number | null): string {
  return value == null ? '-' : value.toLocaleString('ko-KR', { maximumFractionDigits: 2 })
}

function formatTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleTimeString('ko-KR', { hour12: false })
}

// 매도호가는 파랑, 매수호가는 빨강(국내 HTS 관례, 상승 빨강/하락 파랑 규칙과는 별개로
// "사는 쪽이 빨강")이고, 잔량 막대는 같은 면(매도/매수) 안의 최대 잔량 대비 비율이다.
function LevelRow({
  level,
  maxVolume,
  side,
  currency,
}: {
  level: OrderbookLevel
  maxVolume: number
  side: 'ask' | 'bid'
  currency: 'KRW' | 'USD'
}) {
  const ratio = maxVolume > 0 && level.volume != null ? Math.min(100, (level.volume / maxVolume) * 100) : 0
  const barColor = side === 'ask' ? 'bg-blue-50' : 'bg-red-50'
  const textColor = side === 'ask' ? 'text-blue-600' : 'text-red-600'
  return (
    <div className="relative flex items-center justify-between px-2 py-1 text-xs">
      <div className={`absolute inset-y-0 right-0 ${barColor}`} style={{ width: `${ratio}%` }} />
      <span className={`relative font-medium ${textColor}`}>{formatPrice(level.price, currency)}</span>
      <span className="relative text-gray-700">{formatVolume(level.volume)}</span>
    </div>
  )
}

// 종목 상세의 호가·체결 패널 + 상/하한가 한 줄 + 매수 유의사항 배지.
// 시세 자체가 없는 장외 시간에도 마지막 호가가 올 수 있어, 서버 응답이 비면 정직하게
// "데이터가 없어요"로 보여준다(frontend/CLAUDE.md "없는 데이터를 꾸며내지 않는다").
export function StockMarketDepth({ stockCode }: { stockCode: string }) {
  const [tab, setTab] = useState<Tab>('orderbook')
  const orderbookQuery = useStockOrderbookQuery(stockCode)
  const tradesQuery = useStockTradesQuery(stockCode)
  const priceLimitQuery = useStockPriceLimitQuery(stockCode)
  const warningsQuery = useStockWarningsQuery(stockCode)

  const orderbook = orderbookQuery.data
  const asks = [...(orderbook?.asks ?? [])].reverse() // 위에서 아래로 높은 매도호가 → 낮은 매도호가
  const bids = orderbook?.bids ?? []
  const maxAskVolume = Math.max(0, ...asks.map((level) => level.volume ?? 0))
  const maxBidVolume = Math.max(0, ...bids.map((level) => level.volume ?? 0))
  const currency = orderbook?.currency ?? 'KRW'
  const priceLimit = priceLimitQuery.data
  const warnings = warningsQuery.data ?? []
  const hasLimit = priceLimit != null && (priceLimit.upperLimitPrice != null || priceLimit.lowerLimitPrice != null)

  return (
    <section className="rounded-xl border border-gray-200 bg-white p-4">
      {warnings.length > 0 && (
        <div className="mb-3 flex flex-wrap items-center gap-1.5">
          {warnings.map((warning) => (
            <span
              key={`${warning.warningType}-${warning.exchange ?? ''}`}
              className="rounded-lg bg-red-50 px-2 py-1 text-xs font-medium text-red-600"
            >
              {warning.label}
              {warning.exchange ? ` · ${warning.exchange}` : ''}
            </span>
          ))}
        </div>
      )}

      <div className="mb-3 flex items-center justify-between gap-2">
        <div className="flex rounded-xl bg-gray-100 p-1">
          {TABS.map((option) => (
            <button
              key={option.key}
              type="button"
              onClick={() => setTab(option.key)}
              className={`rounded-lg px-3 py-1 text-xs font-medium transition ${
                tab === option.key ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
        {hasLimit && (
          <div className="flex flex-wrap items-center gap-x-1.5 text-xs text-gray-500">
            <span>
              상한{' '}
              <span className="font-semibold text-red-600">{formatPrice(priceLimit.upperLimitPrice, currency)}</span>
            </span>
            <span className="text-gray-300">·</span>
            <span>
              하한{' '}
              <span className="font-semibold text-blue-600">{formatPrice(priceLimit.lowerLimitPrice, currency)}</span>
            </span>
          </div>
        )}
      </div>

      {tab === 'orderbook' && (
        <>
          {orderbookQuery.isLoading && <p className="py-6 text-center text-xs text-gray-400">불러오는 중...</p>}
          {orderbookQuery.isError && !orderbook && (
            <p className="py-6 text-center text-xs text-gray-400">호가를 불러오지 못했어요.</p>
          )}
          {orderbook && asks.length === 0 && bids.length === 0 && (
            <p className="py-6 text-center text-xs text-gray-400">호가 데이터가 없어요.</p>
          )}
          {orderbook && (asks.length > 0 || bids.length > 0) && (
            <div className="overflow-hidden rounded-xl border border-gray-100">
              {asks.map((level) => (
                <LevelRow
                  key={`ask-${level.price}`}
                  level={level}
                  maxVolume={maxAskVolume}
                  side="ask"
                  currency={currency}
                />
              ))}
              <div className="border-t border-gray-100" />
              {bids.map((level) => (
                <LevelRow
                  key={`bid-${level.price}`}
                  level={level}
                  maxVolume={maxBidVolume}
                  side="bid"
                  currency={currency}
                />
              ))}
            </div>
          )}
        </>
      )}

      {tab === 'trades' && (
        <>
          {tradesQuery.isLoading && <p className="py-6 text-center text-xs text-gray-400">불러오는 중...</p>}
          {tradesQuery.isError && !tradesQuery.data && (
            <p className="py-6 text-center text-xs text-gray-400">체결 내역을 불러오지 못했어요.</p>
          )}
          {tradesQuery.data && tradesQuery.data.length === 0 && (
            <p className="py-6 text-center text-xs text-gray-400">체결 내역이 없어요.</p>
          )}
          {tradesQuery.data && tradesQuery.data.length > 0 && (
            <div className="max-h-72 overflow-y-auto rounded-xl border border-gray-100">
              {tradesQuery.data.map((trade, index) => (
                <div
                  key={`${trade.timestamp}-${index}`}
                  className="flex items-center justify-between px-2 py-1 text-xs"
                >
                  <span className="text-gray-400">{formatTime(trade.timestamp)}</span>
                  <span className="font-medium text-gray-900">{formatPrice(trade.price, trade.currency)}</span>
                  <span className="text-gray-700">{formatVolume(trade.volume)}</span>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </section>
  )
}
