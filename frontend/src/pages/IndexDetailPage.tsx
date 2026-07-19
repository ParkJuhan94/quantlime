import { lazy, Suspense, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  useBitcoinChartQuery,
  useExchangeRateChartQuery,
  useIndexChartQuery,
  useMarketIndicesQuery,
} from '../hooks/queries/useMarketIndices'
import { formatRate } from '../components/home/MarketIndexRow'
import { SimpleLineChart } from '../components/chart/SimpleLineChart'
import { LoadingSpinner } from '../components/common/LoadingSpinner'
import { ErrorState } from '../components/common/ErrorState'
import { EmptyState } from '../components/common/EmptyState'
import { ChartIntervalSelector } from '../components/chart/ChartIntervalSelector'
import { changeRateColorClass, formatChangeRate, formatPrice } from '../utils/priceFormat'
import { DEFAULT_INDICATOR_SETTINGS } from '../utils/indicators'
import { aggregateCandles, type ChartInterval } from '../utils/candleAggregation'
import type { DailyChartResponse } from '../types/stock'
import type { ChartIndexCode } from '../types/market'

// CandleChart는 종목 상세 페이지에서만 쓰이는데도 초기 번들에 실리면
// 500KB+ 청크 경고를 유발했던 전례가 있어(StockDetailPage 참고) 여기서도
// 지연 로딩한다. 거래량/보조지표 없이 캔들만 그리는 용도로 재사용한다 -
// 지수는 거래량이 없어 volume:0으로 채우고 모든 지표를 끈다.
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

const NO_INDICATORS = {
  ...DEFAULT_INDICATOR_SETTINGS,
  volume: false,
  ma: false,
  bollingerBands: false,
  ichimoku: false,
  macd: false,
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
  const [chartInterval, setChartInterval] = useState<ChartInterval>('daily')

  const isIndexQuoteCode = INDEX_QUOTE_CODES.has(upperCode)
  const isUsd = upperCode === 'USDKRW'
  const isTreasury = upperCode === 'TREASURY-YIELD'
  const isBitcoin = upperCode === 'BITCOIN'

  const indicesQuery = useMarketIndicesQuery()
  // React Hook은 조건부로 호출할 수 없어 쿼리를 전부 선언하고 enabled로만
  // 활성화 여부를 가른다(FeedComposeModal의 생성/수정 뮤테이션 분기와
  // 동일한 패턴).
  const indexChartQuery = useIndexChartQuery(upperCode as ChartIndexCode, isIndexQuoteCode)
  const usdChartQuery = useExchangeRateChartQuery(isUsd)
  const bitcoinChartQuery = useBitcoinChartQuery(isBitcoin)

  const dailyChartQuery = isUsd ? usdChartQuery : indexChartQuery
  const chartData = useMemo(
    () =>
      aggregateCandles(
        (dailyChartQuery.data ?? []).map<DailyChartResponse>((point) => ({
          tradeDate: point.tradeDate,
          open: point.open,
          high: point.high,
          low: point.low,
          close: point.close,
          volume: 0,
        })),
        chartInterval,
      ),
    [dailyChartQuery.data, chartInterval],
  )
  // 비트코인은 24시간 30분봉(시간별 시계열)이라 일봉 기반 CandleChart에
  // 넣을 수 없다(lightweight-charts가 날짜 하나당 한 점만 허용) - 같은
  // 날짜가 여러 번 나오는 촘촘한 데이터라 별도의 단순 라인차트로 그린다
  // (SimpleLineChart 참고, 홈 카드 미니차트를 상세 페이지 크기로 키운 것).
  const bitcoinPrices = (bitcoinChartQuery.data ?? []).map((point) => point.price)

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
  const isUp = changeRate == null ? null : changeRate > 0 ? true : changeRate < 0 ? false : null
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
            {!isBitcoin && (
              <div className="mb-3 flex justify-end">
                <ChartIntervalSelector value={chartInterval} onChange={setChartInterval} />
              </div>
            )}
            {isBitcoin ? (
              <>
                {bitcoinChartQuery.isLoading && <LoadingSpinner />}
                {bitcoinChartQuery.isError && <ErrorState message="차트를 불러오지 못했습니다." />}
                {bitcoinPrices.length === 0 && !bitcoinChartQuery.isLoading && !bitcoinChartQuery.isError && (
                  <EmptyState message="차트 데이터가 없습니다." />
                )}
                {bitcoinPrices.length > 0 && <SimpleLineChart prices={bitcoinPrices} isUp={isUp} />}
              </>
            ) : (
              <>
                {dailyChartQuery.isLoading && <LoadingSpinner />}
                {dailyChartQuery.isError && <ErrorState message="차트를 불러오지 못했습니다." />}
                {chartData.length === 0 && !dailyChartQuery.isLoading && !dailyChartQuery.isError && (
                  <EmptyState message="차트 데이터가 없습니다." />
                )}
                {chartData.length > 0 && (
                  <Suspense fallback={<LoadingSpinner />}>
                    <CandleChart data={chartData} displayDays={chartData.length} indicators={NO_INDICATORS} />
                  </Suspense>
                )}
              </>
            )}
          </>
        )}
      </section>
    </div>
  )
}
