import { MOCK_MARKET_INDICES } from '../../mock/marketMock'
import { useMarketIndicesQuery } from '../../hooks/queries/useMarketIndices'
import { changeRateColorClass, formatChangeRate, formatPrice } from '../../utils/priceFormat'

function formatRate(rate: number | null): string {
  return rate != null ? rate.toLocaleString('ko-KR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '-'
}

function rateArrowColorClass(changeType: string | null): string {
  if (changeType === 'UP') return 'text-red-600'
  if (changeType === 'DOWN') return 'text-blue-600'
  return 'text-gray-400'
}

function rateArrow(changeType: string | null): string {
  if (changeType === 'UP') return '▲'
  if (changeType === 'DOWN') return '▼'
  return '-'
}

// 코스피/코스닥/해외지수는 아직 실데이터 연동 전이라 예시 데이터로
// 남아 있다(docs/ROADMAP.md #1). 환율·비트코인은 실데이터.
export function MarketIndexRow() {
  const { data } = useMarketIndicesQuery()

  return (
    <section className="rounded-2xl border border-gray-100 bg-gradient-to-br from-white to-gray-50 p-4">
      <p className="mb-3 text-xs font-medium text-gray-400">
        주요 지수 · 환율·비트코인은 실시간, 나머지는 예시 데이터
      </p>
      <div className="grid grid-cols-2 gap-x-6 gap-y-3 sm:grid-cols-4">
        <div className="flex items-center gap-2.5">
          <span className={`w-[34px] shrink-0 text-center text-sm ${rateArrowColorClass(data?.usdKrwChangeType ?? null)}`}>
            {rateArrow(data?.usdKrwChangeType ?? null)}
          </span>
          <div className="min-w-0">
            <p className="truncate text-xs text-gray-500">달러 환율</p>
            <p className="text-sm font-semibold text-gray-900">{formatRate(data?.usdKrwRate ?? null)}</p>
          </div>
        </div>
        <div className="flex items-center gap-2.5">
          <span className="w-[34px] shrink-0 text-center text-sm">₿</span>
          <div className="min-w-0">
            <p className="truncate text-xs text-gray-500">비트코인</p>
            <p className="text-sm font-semibold text-gray-900">
              {formatPrice(data?.bitcoinPriceKrw ?? null)}원{' '}
              <span className={changeRateColorClass(data?.bitcoinChangeRate ?? null)}>
                {formatChangeRate(data?.bitcoinChangeRate ?? null)}
              </span>
            </p>
          </div>
        </div>

        {MOCK_MARKET_INDICES.map((index) => (
          <div key={index.label} className="flex items-center gap-2.5">
            <svg width="34" height="20" viewBox="0 0 40 24" className="shrink-0">
              <polyline
                points={index.points}
                fill="none"
                stroke={index.up ? '#dc2626' : '#2563eb'}
                strokeWidth="2"
              />
            </svg>
            <div className="min-w-0">
              <p className="truncate text-xs text-gray-500">{index.label}</p>
              <p className="text-sm font-semibold text-gray-900">
                {index.value}{' '}
                {index.changeLabel && (
                  <span className={index.up ? 'text-red-600' : 'text-blue-600'}>{index.changeLabel}</span>
                )}
              </p>
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}
