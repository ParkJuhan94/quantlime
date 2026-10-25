import { lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  useStockChartQuery,
  useStockMinuteChartQuery,
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
import { ChartIntervalSelector, type ChartIntervalOrMinute } from '../components/chart/ChartIntervalSelector'
import { MinuteCandleChart } from '../components/chart/MinuteCandleChart'
import { IndicatorControls } from '../components/chart/IndicatorControls'
import { StockMarketDepth } from '../components/stock/StockMarketDepth'
import { OverlayIndicatorLegend, SubPanelIndicatorLegend } from '../components/chart/IndicatorLegend'
import type { CandleChartHandle } from '../components/chart/CandleChart'
import { ScoreSummaryRow } from '../components/score/ScoreSummaryRow'
import { FundamentalsRow } from '../components/stock/FundamentalsRow'
import { StockLogo } from '../components/common/StockLogo'
import { AddToWatchlistGroupPicker } from '../components/home/AddToWatchlistGroupPicker'
import { PremiumGate } from '../components/common/PremiumGate'
import { useIsPremium } from '../hooks/queries/useSubscription'
import { getErrorMessage, isNotFoundStatus } from '../api/errors'
import { changeRateColorClass, currencyForMarketType, formatChangeRate, formatPrice } from '../utils/priceFormat'
import type { IndicatorSettings } from '../utils/indicators'
import {
  aggregateCandles,
  aggregateMinuteCandles,
  isMinuteInterval,
  mergeDailyPages,
  minuteUnitOf,
  normalizeMinuteCandles,
  type ChartInterval,
} from '../utils/candleAggregation'
import { pricePrecisionFor } from '../utils/chartFormat'
import { useFillPages } from '../hooks/useFillPages'
import { recentlyViewedStorage } from '../storage/recentlyViewedStorage'
import { indicatorSettingsStorage } from '../storage/indicatorSettingsStorage'

// lightweight-charts는 이 페이지에서만 쓰이는데도 기본적으로는 전체
// 초기 번들(관심종목/로그인 등 다른 페이지 포함)에 함께 실려 500KB+
// 청크 경고를 유발했다 - 종목 상세 진입 시에만 불러오도록 지연 로딩.
const CandleChart = lazy(() =>
  import('../components/chart/CandleChart').then((module) => ({ default: module.CandleChart })),
)

// 백엔드 PriceController의 @Max(365) 제약과 일치시킨다. 30일/90일 같은
// 기간 선택 대신 항상 한 페이지 최대치를 받아와 일봉/주봉/월봉으로만 나눠 보여주고,
// 더 과거는 차트를 왼쪽으로 밀 때 `to` 커서로 이어 받는다.
const MAX_CHART_DAYS = 365

// 주기별로 화면에 보여줄 최소 봉 수 - 첫 페이지만으로 모자라면(월봉은 12개 안팎) 이만큼 찰 때까지 자동으로
// 더 받는다. maxPages는 토스 호출 폭주를 막는 상한이고 그 이후는 사용자가 밀 때만 이어 받는다.
const MIN_BARS = { daily: 100, weekly: 80, monthly: 36, minute: 60 } as const
const MAX_AUTO_PAGES = 6
// 처음 화면에 보여줄 봉 수 - 불러온 이력이 더 길어도(예전엔 전부를 한 화면에 눌러 담았다) 최근 구간만 보이고,
// 나머지는 왼쪽으로 밀면 나온다. 일목균형표 선행스팬이나 MA120 워밍업에는 전체 이력이 계속 쓰인다.
const DISPLAY_BARS = { daily: 250, weekly: 104, monthly: 36 } as const
const MAX_AUTO_MINUTE_PAGES = 8

export function StockDetailPage() {
  const { stockCode = '' } = useParams<{ stockCode: string }>()
  const [chartInterval, setChartInterval] = useState<ChartIntervalOrMinute>('daily')
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
  const minuteUnit = minuteUnitOf(chartInterval)
  // 분봉 모드일 땐 일봉 쿼리/집계를 쓰지 않으므로 일봉으로 고정해 둔다(타입 좁히기 겸용).
  const dailyInterval: ChartInterval = isMinuteInterval(chartInterval) ? 'daily' : chartInterval
  const isMinuteMode = minuteUnit != null
  const minuteQuery = useStockMinuteChartQuery(stockCode, isMinuteMode)
  const minuteCandles = useMemo(
    () =>
      aggregateMinuteCandles(
        normalizeMinuteCandles((minuteQuery.data?.pages ?? []).flatMap((page) => page.candles)),
        minuteUnit ?? 1,
      ),
    [minuteQuery.data, minuteUnit],
  )
  const dailyBars = useMemo(() => mergeDailyPages(chartQuery.data?.pages ?? []), [chartQuery.data])
  const chartData = useMemo(() => aggregateCandles(dailyBars, dailyInterval), [dailyBars, dailyInterval])
  // 첫 페이지만으로 봉이 모자라면(60분봉, 월봉 등) 화면에 보이기 전에 필요한 만큼 자동으로 더 받는다.
  const isFillingMinute = useFillPages(minuteQuery, minuteCandles.length, {
    minBars: MIN_BARS.minute,
    maxPages: MAX_AUTO_MINUTE_PAGES,
    enabled: isMinuteMode,
  })
  const isFillingDaily = useFillPages(chartQuery, chartData.length, {
    minBars: MIN_BARS[dailyInterval],
    maxPages: MAX_AUTO_PAGES,
    enabled: !isMinuteMode,
  })

  function loadOlderMinute() {
    if (minuteQuery.hasNextPage && !minuteQuery.isFetchingNextPage) void minuteQuery.fetchNextPage()
  }
  function loadOlderDaily() {
    if (chartQuery.hasNextPage && !chartQuery.isFetchingNextPage) void chartQuery.fetchNextPage()
  }
  const { isPremium, isResolving } = useIsPremium()
  const scoreQuery = useStockScoreQuery(stockCode, isPremium)
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
  const watchlist = isAuthenticated ? (watchlistQuery.data ?? []) : []
  const watchlistGroups = isAuthenticated ? (groupsQuery.data ?? []) : []
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
      marketType: detailQuery.data.marketType,
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
  // 실시간 브로드캐스트(livePrice)는 통화 정보를 안 들고 있으므로
  // (PriceSnapshot 참고) REST 기준값(priceQuery)의 currency를 그대로
  // 쓴다 - 종목의 통화는 실시간/REST 어느 소스든 항상 같다.
  const currency = priceQuery.data?.currency === 'USD' ? 'USD' : 'KRW'
  const pricePrecision = pricePrecisionFor(currency)

  return (
    <div className="space-y-6">
      <section className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between md:gap-6">
        {/* 로고는 종목명 행 옆에 나란히(top-align) - 예전엔 items-center로
            전체 열(시총 등 부가정보 박스까지 포함)에 맞춰 세로 중앙정렬
            하다 보니 부가정보 줄이 늘어날 때마다 로고가 아래로 처지는
            것처럼 보였다(2026-07-17 피드백). */}
        <div className="flex items-start gap-3">
          <StockLogo
            logoUrl={stock.logoUrl}
            stockName={stock.stockName}
            overseas={currencyForMarketType(stock.marketType) === 'USD'}
            className="h-12 w-12"
          />
          <div>
            {/* 관심종목 하트를 종목명 헤더 바로 옆으로 이동(2026-07-30 요청) -
                예전엔 헤더 최우측(스코어 카드 옆)에 독립 배치했었으나, 다시
                이름 줄로 옮긴다. */}
            <div className="flex items-baseline gap-2">
              <h1 className="text-2xl font-bold text-gray-900">{stock.stockName}</h1>
              <span className="text-lg font-semibold text-gray-900">{formatPrice(currentPrice, currency)}</span>
              <span className={`text-sm font-light ${changeRateColorClass(changeRate)}`}>
                {formatChangeRate(changeRate)}
              </span>
              <button
                type="button"
                aria-label={isWatched ? '관심종목에서 삭제' : '관심종목에 추가'}
                onClick={toggleWatch}
                className={`shrink-0 self-center rounded-lg border p-1.5 transition ${
                  isWatched ? 'border-red-200 bg-red-50 hover:bg-red-100' : 'border-gray-200 hover:bg-gray-50'
                }`}
              >
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  className={isWatched ? 'fill-red-600 stroke-red-600' : 'fill-none stroke-gray-300'}
                  strokeWidth="2"
                >
                  <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 1 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8Z" />
                </svg>
              </button>
            </div>
            <p className="text-sm text-gray-500">
              {stock.stockCode} · {stock.marketType} · {stock.sector}
            </p>
            <div className="mt-1.5">
              <FundamentalsRow fundamentals={fundamentalsQuery.data} />
            </div>
            <Link
              to={`/stocks/${stockCode}/backtest`}
              className="mt-1 inline-block text-xs text-gray-500 hover:underline"
            >
              백테스트 보기 →
            </Link>
          </div>
        </div>

        {isResolving ? (
          <LoadingSpinner />
        ) : isPremium ? (
          scoreQuery.data && <ScoreSummaryRow score={scoreQuery.data} />
        ) : (
          <PremiumGate>
            <ScoreSummaryRow locked />
          </PremiumGate>
        )}
      </section>

      {addTargetStockCode && (
        <AddToWatchlistGroupPicker
          stockCode={addTargetStockCode}
          groups={watchlistGroups}
          onClose={() => setAddTargetStockCode(null)}
        />
      )}

      <section className="rounded-xl border border-gray-200 bg-white p-4">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          {/* 분봉은 지표 없이 캔들+거래량만 보여준다(MinuteCandleChart 주석 참고) - 지표 UI는 숨긴다. */}
          {isMinuteMode ? (
            <span className="text-xs text-gray-500">
              {minuteUnit === 60 ? '1시간봉' : `${minuteUnit}분봉`} · 최근 거래분
            </span>
          ) : (
            <OverlayIndicatorLegend
              indicators={indicators}
              onItemClick={(key) => candleChartRef.current?.highlightSeries(key)}
            />
          )}
          <div className="flex items-center gap-2">
            {!isMinuteMode && <IndicatorControls value={indicators} onChange={handleIndicatorsChange} />}
            <ChartIntervalSelector value={chartInterval} onChange={setChartInterval} allowMinute />
          </div>
        </div>
        {isMinuteMode ? (
          <>
            {(minuteQuery.isLoading || isFillingMinute) && <LoadingSpinner />}
            {minuteQuery.isError && (
              <ErrorState message={getErrorMessage(minuteQuery.error, '분봉 차트를 불러오지 못했습니다.')} />
            )}
            {!minuteQuery.isLoading && !minuteQuery.isError && minuteCandles.length === 0 && (
              <EmptyState message="분봉 데이터가 없습니다." />
            )}
            {minuteCandles.length > 0 && !isFillingMinute && (
              <MinuteCandleChart
                candles={minuteCandles}
                pricePrecision={pricePrecision}
                viewKey={`${stockCode}:${chartInterval}`}
                onReachStart={loadOlderMinute}
              />
            )}
            {minuteQuery.isFetchingNextPage && !isFillingMinute && (
              <p className="mt-2 text-center text-xs text-gray-400">이전 분봉을 불러오는 중...</p>
            )}
          </>
        ) : (
          <>
            {(chartQuery.isLoading || isFillingDaily) && <LoadingSpinner />}
            {chartQuery.isError && (
              <ErrorState message={getErrorMessage(chartQuery.error, '차트를 불러오지 못했습니다.')} />
            )}
            {chartData.length === 0 && !chartQuery.isLoading && !chartQuery.isError && (
              <EmptyState message="차트 데이터가 없습니다." />
            )}
            {chartData.length > 0 && !isFillingDaily && (
              <Suspense fallback={<LoadingSpinner />}>
                <CandleChart
                  ref={candleChartRef}
                  data={chartData}
                  displayDays={Math.min(chartData.length, DISPLAY_BARS[dailyInterval])}
                  indicators={indicators}
                  livePrice={chartInterval === 'daily' ? livePrice : undefined}
                  pricePrecision={pricePrecision}
                  viewKey={`${stockCode}:${chartInterval}`}
                  onReachStart={loadOlderDaily}
                />
              </Suspense>
            )}
            {chartQuery.isFetchingNextPage && !isFillingDaily && (
              <p className="mt-2 text-center text-xs text-gray-400">이전 구간을 불러오는 중...</p>
            )}
            <SubPanelIndicatorLegend indicators={indicators} />
          </>
        )}
      </section>

      <StockMarketDepth stockCode={stockCode} />

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
