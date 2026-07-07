import { lazy, Suspense, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  useStockChartQuery,
  useStockDetailQuery,
  useStockPriceQuery,
  useStockScoreQuery,
} from '../hooks/queries/useStockDetail'
import { useStockPriceSocket } from '../hooks/useStockPriceSocket'
import { LoadingSpinner } from '../components/common/LoadingSpinner'
import { ErrorState } from '../components/common/ErrorState'
import { EmptyState } from '../components/common/EmptyState'
import { ChartPeriodSelector } from '../components/chart/ChartPeriodSelector'
import { ScoreCard } from '../components/score/ScoreCard'
import { getErrorMessage, isNotFoundStatus } from '../api/errors'
import { changeRateColorClass, formatChangeRate, formatPrice } from '../utils/priceFormat'

// lightweight-charts는 이 페이지에서만 쓰이는데도 기본적으로는 전체
// 초기 번들(관심종목/로그인 등 다른 페이지 포함)에 함께 실려 500KB+
// 청크 경고를 유발했다 - 종목 상세 진입 시에만 불러오도록 지연 로딩.
const CandleChart = lazy(() =>
  import('../components/chart/CandleChart').then((module) => ({ default: module.CandleChart })),
)

const DEFAULT_CHART_DAYS = 90

export function StockDetailPage() {
  const { stockCode = '' } = useParams<{ stockCode: string }>()
  const [days, setDays] = useState(DEFAULT_CHART_DAYS)

  const detailQuery = useStockDetailQuery(stockCode)
  const priceQuery = useStockPriceQuery(stockCode)
  const chartQuery = useStockChartQuery(stockCode, days)
  const scoreQuery = useStockScoreQuery(stockCode)
  const livePrices = useStockPriceSocket([stockCode])
  const livePrice = livePrices[stockCode]

  if (detailQuery.isLoading) {
    return <LoadingSpinner />
  }
  if (detailQuery.isError) {
    return <ErrorState message={getErrorMessage(detailQuery.error, '종목 정보를 불러오지 못했습니다.')} />
  }
  if (!detailQuery.data) {
    return null
  }

  const stock = detailQuery.data
  // 실시간 시세가 아직 도착하지 않았으면(장외 시간 등) REST 조회값을 baseline으로 쓴다.
  const currentPrice = livePrice?.currentPrice ?? priceQuery.data?.price ?? null
  const changeRate = livePrice?.changeRate ?? null

  return (
    <div className="space-y-6">
      <section className="flex items-baseline justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{stock.stockName}</h1>
          <p className="text-sm text-gray-500">
            {stock.stockCode} · {stock.marketType} · {stock.sector}
          </p>
        </div>
        <div className="text-right">
          <p className="text-2xl font-semibold text-gray-900">{formatPrice(currentPrice)}</p>
          <p className={`text-sm ${changeRateColorClass(changeRate)}`}>{formatChangeRate(changeRate)}</p>
        </div>
      </section>

      <section className="rounded-xl border border-gray-200 bg-white p-4">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-gray-900">차트</h2>
          <ChartPeriodSelector value={days} onChange={setDays} />
        </div>
        {chartQuery.isLoading && <LoadingSpinner />}
        {chartQuery.isError && (
          <ErrorState message={getErrorMessage(chartQuery.error, '차트를 불러오지 못했습니다.')} />
        )}
        {chartQuery.data && chartQuery.data.length === 0 && <EmptyState message="차트 데이터가 없습니다." />}
        {chartQuery.data && chartQuery.data.length > 0 && (
          <Suspense fallback={<LoadingSpinner />}>
            <CandleChart data={chartQuery.data} />
          </Suspense>
        )}
      </section>

      <section>
        {scoreQuery.isLoading && <LoadingSpinner />}
        {scoreQuery.isError && isNotFoundStatus(scoreQuery.error) && (
          <EmptyState message="아직 계산된 스코어가 없습니다." />
        )}
        {scoreQuery.isError && !isNotFoundStatus(scoreQuery.error) && (
          <ErrorState message={getErrorMessage(scoreQuery.error, '스코어를 불러오지 못했습니다.')} />
        )}
        {scoreQuery.data && <ScoreCard score={scoreQuery.data} />}
      </section>
    </div>
  )
}
