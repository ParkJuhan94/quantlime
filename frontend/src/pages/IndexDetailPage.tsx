import { lazy, Suspense, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  useBitcoinCandlesQuery,
  useExchangeRateChartQuery,
  useIndexChartQuery,
  useIndexMinuteCandlesQuery,
  useMarketIndicesQuery,
} from '../hooks/queries/useMarketIndices'
import { formatRate } from '../components/home/MarketIndexRow'
import { MinuteCandleChart } from '../components/chart/MinuteCandleChart'
import { IndicatorControls } from '../components/chart/IndicatorControls'
import { OverlayIndicatorLegend, SubPanelIndicatorLegend } from '../components/chart/IndicatorLegend'
import type { CandleChartHandle } from '../components/chart/CandleChart'
import { LoadingSpinner } from '../components/common/LoadingSpinner'
import { ErrorState } from '../components/common/ErrorState'
import { EmptyState } from '../components/common/EmptyState'
import { ChartIntervalSelector, type ChartIntervalOrMinute } from '../components/chart/ChartIntervalSelector'
import { InvestorTradingSummary } from '../components/market/InvestorTradingSummary'
import { changeRateColorClass, formatChangeRate, formatPrice } from '../utils/priceFormat'
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
import { kstDateString } from '../utils/chartFormat'
import { useFillPages } from '../hooks/useFillPages'
import { indicatorSettingsStorage } from '../storage/indicatorSettingsStorage'
import type { DailyChartResponse, MinuteCandle } from '../types/stock'
import type { ChartIndexCode } from '../types/market'

// CandleChart는 종목 상세 페이지에서만 쓰이는데도 초기 번들에 실리면
// 500KB+ 청크 경고를 유발했던 전례가 있어(StockDetailPage 참고) 여기서도
// 지연 로딩한다. 지수는 거래량이 없어 volume:0으로 채우고 거래량 패널은 끈다(비트코인은 있음).
const CandleChart = lazy(() =>
  import('../components/chart/CandleChart').then((module) => ({ default: module.CandleChart })),
)

const INDEX_LABELS: Record<string, string> = {
  KOSPI: '코스피',
  KOSDAQ: '코스닥',
  NASDAQ: '나스닥',
  SP500: 'S&P 500',
  SOXX: 'SOXX',
  USDKRW: '달러 환율',
  'TREASURY-YIELD': '미국 10년물 국채 금리',
  BITCOIN: '비트코인',
}

// 이 5개(국내·해외 지수)만 IndexQuote(marketOpen 포함)로 내려온다 - 환율/
// 국채금리/비트코인은 정규장 개념 자체가 없는 별도 필드다.
const INDEX_QUOTE_CODES = new Set(['KOSPI', 'KOSDAQ', 'NASDAQ', 'SP500', 'SOXX'])

// 주식 상세와 같은 방식으로 처음 화면에 보여줄 최소 봉 수와 초기 표시 봉 수.
const MIN_BARS = { daily: 100, weekly: 80, monthly: 36, minute: 60 } as const
const DISPLAY_BARS = { daily: 250, weekly: 104, monthly: 36 } as const
const MAX_AUTO_PAGES = 6
const MAX_AUTO_MINUTE_PAGES = 8

// 비트코인(업비트)은 분봉 단위와 일/주/월봉을 서버가 직접 내려준다 - 선택 상태를 업비트 interval로 바꾼다.
const BITCOIN_PERIOD_INTERVALS: Record<ChartInterval, string> = { daily: '1d', weekly: '1w', monthly: '1M' }

// 업비트 일/주/월봉(epoch 초, 00:00 UTC = 09:00 KST)을 일봉 차트가 쓰는 영업일 문자열 봉으로 바꾼다.
function toDailyBar(candle: MinuteCandle): DailyChartResponse {
  return {
    tradeDate: kstDateString(candle.time),
    open: candle.open,
    high: candle.high,
    low: candle.low,
    close: candle.close,
    volume: candle.volume,
  }
}

// 종목 상세 페이지(StockDetailPage)와 최대한 같은 레이아웃을 쓴다(헤더 -
// 가격/등락률/장 상태 - 차트) - 다른 점은 관심종목이 아니라 하트 버튼만
// 없다는 것뿐이다(사용자 요청, 2026-07-16). 두 페이지를 아예 같은
// 라우트로 합치는 방안도 검토했으나(2026-07-18 논의) 보류했다 - 시세/
// 스코어/관심종목처럼 지수·환율·코인에는 없는 종목 전용 개념이 많아,
// 라우트까지 합치면 StockDetailPage에 조건 분기가 계속 늘어나며 오히려
// 두 도메인이 뒤섞인다. 대신 차트 렌더링처럼 실제로 같은 로직인 부분은
// CandleChart/ChartIntervalSelector 등 공용 컴포넌트를 그대로 재사용해
// "보기"만 맞추고 있다(README 대화 참고).
export function IndexDetailPage() {
  const { code = '' } = useParams<{ code: string }>()
  const upperCode = code.toUpperCase()
  const isValidCode = upperCode in INDEX_LABELS
  const label = INDEX_LABELS[upperCode] ?? code
  const [chartInterval, setChartInterval] = useState<ChartIntervalOrMinute>('daily')
  const [indicators, setIndicators] = useState<IndicatorSettings>(() => indicatorSettingsStorage.read())
  const candleChartRef = useRef<CandleChartHandle>(null)

  const isIndexQuoteCode = INDEX_QUOTE_CODES.has(upperCode)
  const isUsd = upperCode === 'USDKRW'
  const isTreasury = upperCode === 'TREASURY-YIELD'
  const isBitcoin = upperCode === 'BITCOIN'
  // 투자자별 매매대금은 Toss가 코스피/코스닥 지수만 지원한다(§4).
  const isDomesticIndexCode = upperCode === 'KOSPI' || upperCode === 'KOSDAQ'

  const indicesQuery = useMarketIndicesQuery()
  // React Hook은 조건부로 호출할 수 없어 쿼리를 전부 선언하고 enabled로만
  // 활성화 여부를 가른다(FeedComposeModal의 생성/수정 뮤테이션 분기와
  // 동일한 패턴).
  const minuteUnit = minuteUnitOf(chartInterval)
  const isMinuteMode = minuteUnit != null
  // 분봉 모드일 땐 일봉 쿼리/집계를 쓰지 않으므로 일봉으로 고정해 둔다(타입 좁히기 겸용).
  const dailyInterval: ChartInterval = isMinuteInterval(chartInterval) ? 'daily' : chartInterval
  const supportsMinute = isDomesticIndexCode || isBitcoin
  const bitcoinInterval = minuteUnit != null ? `${minuteUnit}m` : BITCOIN_PERIOD_INTERVALS[dailyInterval]

  const indexChartQuery = useIndexChartQuery(upperCode as ChartIndexCode, isIndexQuoteCode)
  const usdChartQuery = useExchangeRateChartQuery(isUsd)
  const indexMinuteQuery = useIndexMinuteCandlesQuery(
    upperCode as 'KOSPI' | 'KOSDAQ',
    isDomesticIndexCode && isMinuteMode,
  )
  const bitcoinQuery = useBitcoinCandlesQuery(bitcoinInterval, isBitcoin)

  const dailyChartQuery = isUsd ? usdChartQuery : indexChartQuery
  const chartData = useMemo(() => {
    if (isBitcoin) {
      const candles = normalizeMinuteCandles((bitcoinQuery.data?.pages ?? []).flatMap((page) => page.candles))
      return mergeDailyPages([candles.map(toDailyBar)])
    }
    return aggregateCandles(
      (dailyChartQuery.data ?? []).map<DailyChartResponse>((point) => ({
        tradeDate: point.tradeDate,
        open: point.open,
        high: point.high,
        low: point.low,
        close: point.close,
        volume: 0,
      })),
      dailyInterval,
    )
  }, [isBitcoin, bitcoinQuery.data, dailyChartQuery.data, dailyInterval])
  // 분봉: 코스피/코스닥은 1분봉을 프론트에서 묶고, 비트코인은 업비트가 단위별로 내려주므로 그대로 쓴다.
  const minuteCandles = useMemo(() => {
    if (!isMinuteMode) return []
    if (isBitcoin) {
      return normalizeMinuteCandles((bitcoinQuery.data?.pages ?? []).flatMap((page) => page.candles))
    }
    return aggregateMinuteCandles(
      normalizeMinuteCandles((indexMinuteQuery.data?.pages ?? []).flatMap((page) => page.candles)),
      minuteUnit,
    )
  }, [isMinuteMode, isBitcoin, bitcoinQuery.data, indexMinuteQuery.data, minuteUnit])

  // 분봉 조회는 비트코인(업비트)과 코스피/코스닥(토스)이 서로 다른 쿼리라 현재 화면의 쿼리를 골라 쓴다.
  const pagedQuery = isBitcoin ? bitcoinQuery : indexMinuteQuery
  const isFillingMinute = useFillPages(pagedQuery, minuteCandles.length, {
    minBars: MIN_BARS.minute,
    maxPages: MAX_AUTO_MINUTE_PAGES,
    enabled: isMinuteMode && !isUsd,
  })
  // 일/주/월봉은 비트코인만 페이지를 이어 받는다(지수는 서버가 한 번에 내려주는 구간이 전부).
  const isFillingBitcoinPeriod = useFillPages(bitcoinQuery, chartData.length, {
    minBars: MIN_BARS[dailyInterval],
    maxPages: MAX_AUTO_PAGES,
    enabled: isBitcoin && !isMinuteMode,
  })
  function loadOlderMinute() {
    if (pagedQuery.hasNextPage && !pagedQuery.isFetchingNextPage) void pagedQuery.fetchNextPage()
  }
  function loadOlderBitcoinPeriod() {
    if (bitcoinQuery.hasNextPage && !bitcoinQuery.isFetchingNextPage) void bitcoinQuery.fetchNextPage()
  }

  function handleIndicatorsChange(next: IndicatorSettings) {
    setIndicators(next)
    indicatorSettingsStorage.write(next)
  }
  // 지수는 거래량 데이터가 없어 거래량 패널은 항상 끈다(비트코인은 있음). 환율은 종가 라인만 그려서 지표를 쓰지 않는다.
  const chartIndicators = isBitcoin ? indicators : { ...indicators, volume: false }
  const showIndicators = !isUsd && !isMinuteMode
  const pricePrecision = isBitcoin ? 0 : 2

  if (!isValidCode) {
    return <ErrorState message="지원하지 않는 지수입니다." />
  }

  const data = indicesQuery.data
  const value = isUsd
    ? data?.usdKrwRate
    : isTreasury
      ? data?.usTreasuryYield10y
      : isBitcoin
        ? data?.bitcoinPriceKrw
        : { KOSPI: data?.kospi, KOSDAQ: data?.kosdaq, NASDAQ: data?.nasdaq, SP500: data?.sp500, SOXX: data?.soxx }[
            upperCode
          ]?.value
  const changeRate = isUsd
    ? data?.usdKrwChangeRate
    : isTreasury
      ? data?.usTreasuryYield10yChangeRate
      : isBitcoin
        ? data?.bitcoinChangeRate
        : { KOSPI: data?.kospi, KOSDAQ: data?.kosdaq, NASDAQ: data?.nasdaq, SP500: data?.sp500, SOXX: data?.soxx }[
            upperCode
          ]?.changeRate
  const marketOpen = isIndexQuoteCode
    ? { KOSPI: data?.kospi, KOSDAQ: data?.kosdaq, NASDAQ: data?.nasdaq, SP500: data?.sp500, SOXX: data?.soxx }[
        upperCode
      ]?.marketOpen
    : undefined
  // 환율·국채금리는 정규장 개념 자체가 없어(marketOpen 필드가 없음) 홈
  // 카드와 동일하게 항상 "장마감" 텍스트로 통일하고, 비트코인은 24시간
  // 거래라 항상 "장중"으로 표시한다(2026-07-18 피드백).
  const statusText = isBitcoin ? '장중' : marketOpen == null ? '장마감' : marketOpen ? '장중' : '장마감'
  const valueText = isBitcoin
    ? formatPrice(value ?? null)
    : isTreasury && value != null
      ? `${formatRate(value)}%`
      : formatRate(value ?? null)

  return (
    <div className="space-y-6">
      <section className="flex items-baseline gap-2">
        <h1 className="text-2xl font-bold text-gray-900">{label}</h1>
        {value != null && (
          <>
            <span className="text-lg font-semibold text-gray-900">{valueText}</span>
            <span className={`text-sm font-light ${changeRateColorClass(changeRate)}`}>
              {formatChangeRate(changeRate)}
            </span>
            <span className="text-xs text-gray-400">{statusText}</span>
          </>
        )}
      </section>

      <section className="rounded-xl border border-gray-200 bg-white p-4">
        {isTreasury ? (
          // FRED 등 공식 과거 시세 소스로 일봉 차트를 받아보려 했으나
          // Akamai로 추정되는 봇 차단에 걸려(2026-07-18) 포기했다 - 짧은
          // 폴링 누적 이력(홈 카드 미니차트용)을 "일봉 차트"인 것처럼
          // 상세 페이지에 늘려 보여주면 실제 기간을 오해하게 만들 수
          // 있어, 여기서는 정직하게 비워둔다.
          <EmptyState message="아직 이 지표는 과거 시세 차트를 지원하지 않아요." />
        ) : (
          <>
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              {isMinuteMode ? (
                <span className="text-xs text-gray-500">
                  {minuteUnit === 60 ? '1시간봉' : `${minuteUnit}분봉`} · 최근 거래분
                </span>
              ) : showIndicators ? (
                <OverlayIndicatorLegend
                  indicators={chartIndicators}
                  onItemClick={(key) => candleChartRef.current?.highlightSeries(key)}
                />
              ) : (
                <span />
              )}
              <div className="flex flex-wrap items-center gap-2">
                {isDomesticIndexCode && <InvestorTradingSummary code={upperCode as 'KOSPI' | 'KOSDAQ'} />}
                {showIndicators && <IndicatorControls value={indicators} onChange={handleIndicatorsChange} />}
                <ChartIntervalSelector value={chartInterval} onChange={setChartInterval} allowMinute={supportsMinute} />
              </div>
            </div>
            {isMinuteMode ? (
              <>
                {(pagedQuery.isLoading || isFillingMinute) && <LoadingSpinner />}
                {pagedQuery.isError && <ErrorState message="차트를 불러오지 못했습니다." />}
                {minuteCandles.length === 0 && !pagedQuery.isLoading && !pagedQuery.isError && (
                  <EmptyState message="차트 데이터가 없습니다." />
                )}
                {minuteCandles.length > 0 && !isFillingMinute && (
                  <MinuteCandleChart
                    candles={minuteCandles}
                    pricePrecision={pricePrecision}
                    viewKey={`${upperCode}:${chartInterval}`}
                    onReachStart={loadOlderMinute}
                  />
                )}
                {pagedQuery.isFetchingNextPage && !isFillingMinute && (
                  <p className="mt-2 text-center text-xs text-gray-400">이전 분봉을 불러오는 중...</p>
                )}
              </>
            ) : (
              <>
                {(isBitcoin ? bitcoinQuery.isLoading : dailyChartQuery.isLoading) && <LoadingSpinner />}
                {isFillingBitcoinPeriod && <LoadingSpinner />}
                {(isBitcoin ? bitcoinQuery.isError : dailyChartQuery.isError) && (
                  <ErrorState message="차트를 불러오지 못했습니다." />
                )}
                {chartData.length === 0 &&
                  !(isBitcoin ? bitcoinQuery.isLoading : dailyChartQuery.isLoading) &&
                  !(isBitcoin ? bitcoinQuery.isError : dailyChartQuery.isError) && (
                    <EmptyState message="차트 데이터가 없습니다." />
                  )}
                {chartData.length > 0 && !isFillingBitcoinPeriod && (
                  <Suspense fallback={<LoadingSpinner />}>
                    <CandleChart
                      ref={candleChartRef}
                      data={chartData}
                      displayDays={Math.min(chartData.length, DISPLAY_BARS[dailyInterval])}
                      indicators={chartIndicators}
                      pricePrecision={pricePrecision}
                      variant={isUsd ? 'line' : 'candle'}
                      viewKey={`${upperCode}:${chartInterval}`}
                      onReachStart={isBitcoin ? loadOlderBitcoinPeriod : undefined}
                    />
                  </Suspense>
                )}
                {isBitcoin && bitcoinQuery.isFetchingNextPage && !isFillingBitcoinPeriod && (
                  <p className="mt-2 text-center text-xs text-gray-400">이전 구간을 불러오는 중...</p>
                )}
                {showIndicators && <SubPanelIndicatorLegend indicators={chartIndicators} />}
              </>
            )}
          </>
        )}
      </section>
    </div>
  )
}
