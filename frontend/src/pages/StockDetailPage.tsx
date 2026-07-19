import { lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  useStockChartQuery,
  useStockDetailQuery,
  useStockFundamentalsQuery,
  useStockPriceQuery,
  useStockScoreQuery,
} from '../hooks/queries/useStockDetail'
import { useStockPriceSocket } from '../hooks/useStockPriceSocket'
import { useAuth } from '../auth/useAuth'
import { useRemoveWatchlist, useWatchlistQuery } from '../hooks/queries/useWatchlist'
import { useWatchlistGroupsQuery } from '../hooks/queries/useWatchlistGroups'
import { LoadingSpinner } from '../components/common/LoadingSpinner'
import { ErrorState } from '../components/common/ErrorState'
import { EmptyState } from '../components/common/EmptyState'
import { ChartIntervalSelector } from '../components/chart/ChartIntervalSelector'
import { IndicatorControls } from '../components/chart/IndicatorControls'
import { OverlayIndicatorLegend, SubPanelIndicatorLegend } from '../components/chart/IndicatorLegend'
import type { CandleChartHandle } from '../components/chart/CandleChart'
import { ScoreSummaryRow } from '../components/score/ScoreSummaryRow'
import { FundamentalsRow } from '../components/stock/FundamentalsRow'
import { StockLogo } from '../components/common/StockLogo'
import { AddToWatchlistGroupPicker } from '../components/home/AddToWatchlistGroupPicker'
import { getErrorMessage, isNotFoundStatus } from '../api/errors'
import { changeRateColorClass, formatChangeRate, formatPrice } from '../utils/priceFormat'
import type { IndicatorSettings } from '../utils/indicators'
import { aggregateCandles, type ChartInterval } from '../utils/candleAggregation'
import { recentlyViewedStorage } from '../storage/recentlyViewedStorage'
import { indicatorSettingsStorage } from '../storage/indicatorSettingsStorage'

// lightweight-charts는 이 페이지에서만 쓰이는데도 기본적으로는 전체
// 초기 번들(관심종목/로그인 등 다른 페이지 포함)에 함께 실려 500KB+
// 청크 경고를 유발했다 - 종목 상세 진입 시에만 불러오도록 지연 로딩.
const CandleChart = lazy(() =>
  import('../components/chart/CandleChart').then((module) => ({ default: module.CandleChart })),
)

// 백엔드 PriceController의 @Max(365) 제약과 일치시킨다. 30일/90일 같은
// 기간 선택 대신 항상 최대치를 받아와 일봉/주봉/월봉으로만 나눠 보여준다.
const MAX_CHART_DAYS = 365

export function StockDetailPage() {
  const { stockCode = '' } = useParams<{ stockCode: string }>()
  const [chartInterval, setChartInterval] = useState<ChartInterval>('daily')
  const [indicators, setIndicators] = useState<IndicatorSettings>(() => indicatorSettingsStorage.read())
  const [addTargetStockCode, setAddTargetStockCode] = useState<string | null>(null)
  const candleChartRef = useRef<CandleChartHandle>(null)

  function handleIndicatorsChange(next: IndicatorSettings) {
    setIndicators(next)
    indicatorSettingsStorage.write(next)
  }

  const detailQuery = useStockDetailQuery(stockCode)
  const priceQuery = useStockPriceQuery(stockCode)
  const chartQuery = useStockChartQuery(stockCode, MAX_CHART_DAYS)
  const chartData = useMemo(
    () => aggregateCandles(chartQuery.data ?? [], chartInterval),
    [chartQuery.data, chartInterval],
  )
  const scoreQuery = useStockScoreQuery(stockCode)
  const fundamentalsQuery = useStockFundamentalsQuery(stockCode)
  const livePrices = useStockPriceSocket([stockCode])
  const livePrice = livePrices[stockCode]

  // 종목 상세는 비로그인도 볼 수 있는 공개 페이지라(App.tsx 라우팅
  // 참고) 하트 버튼 관련 데이터는 홈 화면과 동일하게 isAuthenticated로
  // 게이팅한다 - 관심종목 등록은 항상 그룹 지정이 필요하다("미분류" 폐지).
  const { isAuthenticated } = useAuth()
  const watchlistQuery = useWatchlistQuery(isAuthenticated)
  const groupsQuery = useWatchlistGroupsQuery(isAuthenticated)
  const removeWatchlist = useRemoveWatchlist()
  const watchlist = isAuthenticated ? watchlistQuery.data ?? [] : []
  const watchlistGroups = isAuthenticated ? groupsQuery.data ?? [] : []
  const isWatched = watchlist.some((item) => item.stockCode === stockCode)

  function toggleWatch() {
    if (isWatched) {
      removeWatchlist.mutate(stockCode)
    } else {
      setAddTargetStockCode(stockCode)
    }
  }

  useEffect(() => {
    if (!detailQuery.data) return
    recentlyViewedStorage.record({
      stockCode: detailQuery.data.stockCode,
      stockName: detailQuery.data.stockName,
      logoUrl: detailQuery.data.logoUrl,
    })
  }, [detailQuery.data])

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
  // 실시간 시세가 아직 도착하지 않았으면(장외 시간, 관심종목이 아니라
  // 브로드캐스트 폴링 대상이 아닌 경우 등) REST 조회값을 baseline으로 쓴다.
  const currentPrice = livePrice?.currentPrice ?? priceQuery.data?.price ?? null
  const changeRate = livePrice?.changeRate ?? priceQuery.data?.changeRate ?? null

  return (
    <div className="space-y-6">
      <section className="flex items-start justify-between gap-6">
        {/* 로고는 종목명 행 옆에 나란히(top-align) - 예전엔 items-center로
            전체 열(시총 등 부가정보 박스까지 포함)에 맞춰 세로 중앙정렬
            하다 보니 부가정보 줄이 늘어날 때마다 로고가 아래로 처지는
            것처럼 보였다(2026-07-17 피드백). */}
        <div className="flex items-start gap-3">
          <StockLogo logoUrl={stock.logoUrl} stockName={stock.stockName} className="h-12 w-12" />
          <div>
            <div className="flex items-baseline gap-2">
              <h1 className="text-2xl font-bold text-gray-900">{stock.stockName}</h1>
              <span className="text-lg font-semibold text-gray-900">{formatPrice(currentPrice)}</span>
              <span className={`text-sm font-light ${changeRateColorClass(changeRate)}`}>
                {formatChangeRate(changeRate)}
              </span>
            </div>
            <p className="text-sm text-gray-500">
              {stock.stockCode} · {stock.marketType} · {stock.sector}
            </p>
            <div className="mt-1.5">
              <FundamentalsRow fundamentals={fundamentalsQuery.data} />
            </div>
          </div>
        </div>

        {/* 관심종목 하트는 이름 줄 안이 아니라 헤더 섹션 전체의
            최우측(스코어 카드보다 더 바깥)에 독립 배치한다 - 이름 줄에
            끼어 있으면 스코어 카드와 가까이 붙어 "스코어에 속한 버튼"처럼
            보인다는 피드백(2026-07-19). 종목 페이지 전체에 대한 액션이라는
            걸 위치로 분명히 하기 위해 스코어 유무와 무관하게 항상 이 자리에 둔다. */}
        <div className="flex items-start gap-3">
          {scoreQuery.data && <ScoreSummaryRow score={scoreQuery.data} />}
          <button
            type="button"
            aria-label={isWatched ? '관심종목에서 삭제' : '관심종목에 추가'}
            onClick={toggleWatch}
            className={`shrink-0 rounded-lg border p-1.5 transition ${
              isWatched
                ? 'border-red-200 bg-red-50 hover:bg-red-100'
                : 'border-gray-200 hover:bg-gray-50'
            }`}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill={isWatched ? '#dc2626' : 'none'} stroke={isWatched ? '#dc2626' : '#c6c6c6'} strokeWidth="2">
              <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 1 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8Z" />
            </svg>
          </button>
        </div>
      </section>

      {addTargetStockCode && (
        <AddToWatchlistGroupPicker
          stockCode={addTargetStockCode}
          groups={watchlistGroups}
          onClose={() => setAddTargetStockCode(null)}
        />
      )}

      <section className="rounded-xl border border-gray-200 bg-white p-4">
        <div className="mb-3 flex items-center justify-between">
          <OverlayIndicatorLegend
            indicators={indicators}
            onItemClick={(key) => candleChartRef.current?.highlightSeries(key)}
          />
          <div className="flex items-center gap-2">
            <IndicatorControls value={indicators} onChange={handleIndicatorsChange} />
            <ChartIntervalSelector value={chartInterval} onChange={setChartInterval} />
          </div>
        </div>
        {chartQuery.isLoading && <LoadingSpinner />}
        {chartQuery.isError && (
          <ErrorState message={getErrorMessage(chartQuery.error, '차트를 불러오지 못했습니다.')} />
        )}
        {chartData.length === 0 && !chartQuery.isLoading && !chartQuery.isError && (
          <EmptyState message="차트 데이터가 없습니다." />
        )}
        {chartData.length > 0 && (
          <Suspense fallback={<LoadingSpinner />}>
            <CandleChart
              ref={candleChartRef}
              data={chartData}
              displayDays={chartData.length}
              indicators={indicators}
              livePrice={chartInterval === 'daily' ? livePrice : undefined}
            />
          </Suspense>
        )}
        <SubPanelIndicatorLegend indicators={indicators} />
      </section>

      {scoreQuery.isLoading && <LoadingSpinner />}
      {scoreQuery.isError && isNotFoundStatus(scoreQuery.error) && (
        <EmptyState message="아직 계산된 스코어가 없습니다." />
      )}
      {scoreQuery.isError && !isNotFoundStatus(scoreQuery.error) && (
        <ErrorState message={getErrorMessage(scoreQuery.error, '스코어를 불러오지 못했습니다.')} />
      )}
    </div>
  )
}
