import { useNavigate } from 'react-router-dom'
import {
  useBitcoinChartQuery,
  useExchangeRateChartQuery,
  useIndexChartQuery,
  useIndexMinuteChartQuery,
  useMarketIndicesQuery,
} from '../../hooks/queries/useMarketIndices'
import { formatChangeRate, formatPrice } from '../../utils/priceFormat'
import type { IndexQuote, WorldIndexCode } from '../../types/market'

export function formatRate(rate: number | null): string {
  return rate != null ? rate.toLocaleString('ko-KR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '-'
}

// 등락 방향에 따라 카드 배경·테두리를 은은하게 물들인다 - 국내 주식
// 관례대로 상승은 빨강, 하락은 파랑 계열(bg는 /60 투명도로 옅게 눌러
// 텍스트 대비를 해치지 않는 선에서만). 삼각형(▲▼)은 색상만으로도 방향이
// 충분히 구분돼 뺐다(사용자 피드백, 2026-07-16 - "삼각형 상승,하락은 제거").
function cardToneClass(isUp: boolean | null): string {
  if (isUp === true) return 'border-red-100 bg-red-50/60'
  if (isUp === false) return 'border-blue-100 bg-blue-50/60'
  return 'border-gray-100 bg-gray-50'
}

// 촘촘한 원시 데이터(코스피/코스닥 1분봉은 최대 391개)를 카드 안 작은
// 차트에 그대로 그리면 지나치게 삐죽삐죽해 보인다 - 균등 간격으로
// 솎아내 "더 심플한" 라인으로 보이게 한다(처음·끝 값은 항상 보존).
function downsample(values: number[], maxPoints: number): number[] {
  if (values.length <= maxPoints) return values
  const step = (values.length - 1) / (maxPoints - 1)
  return Array.from({ length: maxPoints }, (_, i) => values[Math.round(i * step)])
}

const CHART_WIDTH = 64
const CHART_HEIGHT = 30

// live일 때 차트 진행 방향(가장 오른쪽=최신 지점)에 작은 펄스 점을
// 찍는다 - "지금 실시간으로 움직이는 중"이라는 신호를 상태 라벨 옆
// 고정된 위치가 아니라 차트가 실제로 갱신되는 지점에 붙여야 자연스럽다
// (2026-07-16, 이전엔 상태 라벨 옆 고정 위치였는데 어색하다는 피드백).
function MiniChart({ prices, isUp, live }: { prices: number[]; isUp: boolean | null; live?: boolean }) {
  if (prices.length < 2) return null

  const min = Math.min(...prices)
  const max = Math.max(...prices)
  const range = max - min || 1
  const stepX = CHART_WIDTH / Math.max(prices.length - 1, 1)
  const coords = prices.map((value, i) => ({
    x: i * stepX,
    y: CHART_HEIGHT - ((value - min) / range) * CHART_HEIGHT,
  }))
  const points = coords.map(({ x, y }) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ')
  const strokeColor = isUp === false ? '#2563eb' : '#dc2626'
  const last = coords[coords.length - 1]

  return (
    <div className="h-[30px] w-16 shrink-0">
      {/* overflow:visible이 없으면 마지막 지점이 오른쪽 끝(x=64)에 가까울 때
          펄스가 확대되면서 SVG 뷰포트 밖으로 클리핑돼 반쪽만 보인다
          (2026-07-17 피드백 - "점 오른쪽이 카드 레이아웃에 겹쳐 잘려 보임"). */}
      <svg
        width="100%"
        height={CHART_HEIGHT}
        viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
        preserveAspectRatio="none"
        style={{ overflow: 'visible' }}
      >
        <polyline points={points} fill="none" stroke={strokeColor} strokeWidth="1.5" />
        {live && (
          <>
            <circle
              cx={last.x}
              cy={last.y}
              r="3"
              fill={strokeColor}
              opacity="0.75"
              className="animate-ping"
              style={{ transformOrigin: `${last.x}px ${last.y}px` }}
            />
            <circle cx={last.x} cy={last.y} r="1.5" fill={strokeColor} />
          </>
        )}
      </svg>
    </div>
  )
}

// 카드 라벨 옆에 붙는 작은 정보 아이콘 - 비트코인("차트가 24시간
// 기준"이라는 설명)·SOXX("지수가 아니라 ETF") 처럼 라벨만으론 오해하기
// 쉬운 카드에 짧은 설명을 달아준다(ScoreSummaryRow의 정보 아이콘과
// 동일한 시각 언어, 2026-07-16).
function InfoTooltip({ text }: { text: string }) {
  return (
    <span className="group relative inline-flex shrink-0">
      <svg
        width="12"
        height="12"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        className="cursor-help text-gray-300 transition hover:text-gray-400"
      >
        <circle cx="12" cy="12" r="9" />
        <path d="M12 11v5.5" strokeLinecap="round" />
        <circle cx="12" cy="8" r="0.75" fill="currentColor" stroke="none" />
      </svg>
      <span className="invisible absolute left-0 top-5 z-20 w-40 rounded-lg border border-gray-100 bg-white p-2 text-[10px] leading-relaxed text-gray-600 opacity-0 shadow-lg transition group-hover:visible group-hover:opacity-100">
        {text}
      </span>
    </span>
  )
}

function IndexCard({
  label,
  tooltip,
  valueText,
  changeText,
  isUp,
  statusLabel,
  subInfo,
  live,
  chart,
  onClick,
}: {
  label: string
  tooltip?: string
  valueText: string
  changeText: string
  isUp: boolean | null
  statusLabel?: string
  subInfo?: string
  live?: boolean
  chart?: number[]
  onClick?: () => void
}) {
  const color = isUp == null ? 'text-gray-500' : isUp ? 'text-red-600' : 'text-blue-600'

  return (
    <div
      onClick={onClick}
      role={onClick ? 'button' : undefined}
      className={`flex min-w-0 items-center justify-between gap-2 rounded-xl border p-3 transition ${cardToneClass(isUp)} ${
        onClick ? 'cursor-pointer hover:brightness-95' : ''
      }`}
    >
      <div className="min-w-0">
        <div className="flex items-center gap-1">
          <span className="truncate text-xs font-semibold text-gray-900">{label}</span>
          {tooltip && <InfoTooltip text={tooltip} />}
        </div>
        <p className="mt-0.5 truncate text-base font-bold text-gray-900">{valueText}</p>
        <div className="flex items-center gap-1.5">
          <span className={`rounded px-1 py-0.5 text-xs font-light ${color}`}>{changeText}</span>
          {statusLabel && <span className="text-[10px] text-gray-400">{statusLabel}</span>}
        </div>
        {subInfo && <p className="mt-0.5 truncate text-[10px] text-gray-400">{subInfo}</p>}
      </div>
      {chart && chart.length > 1 && <MiniChart prices={chart} isUp={isUp} live={live} />}
    </div>
  )
}

function directionOf(changeRate: number): boolean | null {
  if (changeRate > 0) return true
  if (changeRate < 0) return false
  return null
}

function KrIndexCard({
  code,
  label,
  quote,
}: {
  code: 'KOSPI' | 'KOSDAQ'
  label: string
  quote: IndexQuote | null | undefined
}) {
  const navigate = useNavigate()
  const minuteChartQuery = useIndexMinuteChartQuery(code, quote != null)
  const prices = downsample((minuteChartQuery.data ?? []).map((point) => point.price), 30)

  if (!quote) {
    return <IndexCard label={label} valueText="-" changeText="-" isUp={null} />
  }

  return (
    <IndexCard
      label={label}
      valueText={formatRate(quote.value)}
      changeText={formatChangeRate(quote.changeRate)}
      isUp={directionOf(quote.changeRate)}
      // 장중엔 펄스 점이 이미 "지금 살아있음"을 보여주므로 "장중" 텍스트는
      // 중복이라 뺀다 - 펄스가 없는 장마감일 때만 텍스트로 보완한다
      // (2026-07-17 피드백).
      statusLabel={quote.marketOpen ? undefined : '장마감'}
      live={quote.marketOpen}
      chart={prices}
      onClick={() => navigate(`/indices/${code.toLowerCase()}`)}
    />
  )
}

const WORLD_INDEX_LABELS: Record<WorldIndexCode, string> = {
  NASDAQ: '나스닥',
  SP500: 'S&P 500',
  SOXX: 'SOXX',
}

const WORLD_INDEX_TOOLTIPS: Partial<Record<WorldIndexCode, string>> = {
  SOXX: 'SOXX는 지수가 아니라 아이셰어즈 반도체 ETF(iShares Semiconductor ETF)예요 - 미국에 상장된 개별 종목이에요.',
}

const OVER_MARKET_LABELS: Record<'PRE_MARKET' | 'AFTER_MARKET', string> = {
  PRE_MARKET: '프리마켓',
  AFTER_MARKET: '애프터마켓',
}

// 해외지수/ETF는 네이버 금융에 당일 분봉 조회가 없어(실제 호출로 확인)
// 일봉 이력으로 미니 차트를 그린다 - 시세 자체는 코스피/코스닥과 동일하게
// delayTime=0(실시간)으로 내려온다. SOXX는 지수가 아니라 아이셰어즈
// 반도체 ETF(iShares Semiconductor ETF)다.
function WorldIndexCard({ code, quote }: { code: WorldIndexCode; quote: IndexQuote | null | undefined }) {
  const navigate = useNavigate()
  const chartQuery = useIndexChartQuery(code, quote != null)
  const prices = (chartQuery.data ?? []).map((point) => point.close)
  const label = WORLD_INDEX_LABELS[code]
  const tooltip = WORLD_INDEX_TOOLTIPS[code]

  if (!quote) {
    return <IndexCard label={label} tooltip={tooltip} valueText="-" changeText="-" isUp={null} />
  }

  // 정규장이 닫혀 있어도 프리·애프터마켓이 열려 있으면(실제 호출로 확인
  // 가능한 필드) "장마감" 대신 그 세션 이름을 보여준다 - 이 라벨은
  // statusLabel 한 곳에만 쓰고 subInfo에서 다시 반복하지 않는다(예전엔
  // "프리마켓 6,820.60 · -1.28%"처럼 statusLabel과 문구가 겹쳐 보였다는
  // 피드백, 2026-07-17).
  const overMarketLabel = quote.overMarketSessionType ? OVER_MARKET_LABELS[quote.overMarketSessionType] : null
  // 장중엔 펄스 점이 이미 신호를 주므로 텍스트를 생략 - 프리/애프터마켓은
  // 펄스가 없는(live=false) 상태라 텍스트로 어떤 세션인지 보여줘야 한다.
  const statusLabel = quote.marketOpen ? undefined : (overMarketLabel ?? '장마감')

  const isPreMarket = quote.overMarketSessionType === 'PRE_MARKET'
  const isAfterMarket = quote.overMarketSessionType === 'AFTER_MARKET'
  // 프리마켓은 정규장이 아직 시작 전이라 "지금 거래되는" 프리마켓 가격·
  // 등락률을 메인 값으로 보여준다. 애프터마켓은 이미 정규장 종가가
  // 확정된 뒤라 메인 값은 종가로 고정해두고, 그 이후 애프터마켓에서
  // 얼마나 더 움직였는지만 등락률 숫자 하나로 보조 줄에 덧붙인다(가격·
  // 라벨 반복 없이).
  const displayValue = isPreMarket && quote.overMarketValue != null ? quote.overMarketValue : quote.value
  const displayChangeRate =
    isPreMarket && quote.overMarketChangeRate != null ? quote.overMarketChangeRate : quote.changeRate
  const subInfo =
    isAfterMarket && quote.overMarketChangeRate != null ? formatChangeRate(quote.overMarketChangeRate) : undefined

  return (
    <IndexCard
      label={label}
      tooltip={tooltip}
      valueText={formatRate(displayValue)}
      changeText={formatChangeRate(displayChangeRate)}
      isUp={directionOf(displayChangeRate)}
      statusLabel={statusLabel}
      subInfo={subInfo}
      live={quote.marketOpen}
      chart={prices}
      onClick={() => navigate(`/indices/${code.toLowerCase()}`)}
    />
  )
}

function BitcoinCard({
  priceKrw,
  changeRate,
}: {
  priceKrw: number | null
  changeRate: number | null
}) {
  const navigate = useNavigate()
  const chartQuery = useBitcoinChartQuery(priceKrw != null)
  const prices = downsample((chartQuery.data ?? []).map((point) => point.price), 30)

  return (
    <IndexCard
      label="비트코인"
      tooltip="24시간 쉬지 않고 거래돼 항상 장중이에요. 차트는 현재 시점 기준 최근 24시간 데이터예요."
      valueText={formatPrice(priceKrw)}
      changeText={formatChangeRate(changeRate)}
      isUp={changeRate == null ? null : directionOf(changeRate)}
      // 비트코인은 24시간 항상 live라 펄스 점이 계속 떠 있다 - "장중"
      // 텍스트를 표시할 순간이 아예 없으므로 statusLabel 자체를 생략.
      live
      chart={prices}
      onClick={() => navigate('/indices/bitcoin')}
    />
  )
}

function TreasuryYieldCard({
  yieldValue,
  changeRate,
  history,
}: {
  yieldValue: number | null
  changeRate: number | null
  history: number[]
}) {
  // 금리는 오르면(가격 하락) 통상 "위험 신호"로 읽혀 국내 주식의 상승=빨강
  // 관례를 그대로 적용하면 오해를 살 수 있지만, 이 카드도 다른 카드들과
  // 시각적 일관성을 맞추는 걸 우선한다(상승=빨강 계열 그대로 유지) - 값
  // 자체의 해석은 사용자 몫으로 남겨둔다. 관례상 채권 금리는 포인트
  // 변화(%p)로 표기하지만, 다른 카드와 값·차트 표현을 통일해달라는
  // 요청(2026-07-17)에 따라 등락률(%)로 맞췄다. 차트는 FRED로 실제 일봉을
  // 받아보려 했으나 Akamai로 추정되는 봇 차단에 걸려(2026-07-18) 포기 -
  // 대신 백엔드가 폴링할 때마다 값을 누적한 이력(MarketIndexCache)을
  // 쓴다(재시작 시 비고, 일봉이 아니라 몇 분 단위 스냅샷이라는 한계는
  // 있지만 값을 지어내진 않는다).
  const navigate = useNavigate()

  return (
    <IndexCard
      label="미국 10년물 국채 금리"
      valueText={yieldValue != null ? `${formatRate(yieldValue)}%` : '-'}
      changeText={formatChangeRate(changeRate)}
      isUp={changeRate == null ? null : directionOf(changeRate)}
      // 이 카드는 펄스(live)가 없는(marketOpen 개념 자체가 없음) 카드라
      // 다른 카드들의 "장중엔 펄스만, 장마감엔 텍스트" 규칙을 그대로 적용해
      // 항상 텍스트로 보여준다(2026-07-18 피드백).
      statusLabel="장마감"
      chart={history}
      onClick={() => navigate('/indices/treasury-yield')}
    />
  )
}

function UsdCard({ rate, changeRate }: { rate: number | null; changeRate: number | null }) {
  const navigate = useNavigate()
  const chartQuery = useExchangeRateChartQuery(rate != null)
  const prices = (chartQuery.data ?? []).map((point) => point.close)

  return (
    <IndexCard
      label="달러 환율"
      valueText={formatRate(rate)}
      changeText={formatChangeRate(changeRate)}
      isUp={changeRate == null ? null : directionOf(changeRate)}
      statusLabel="장마감"
      chart={prices}
      onClick={() => navigate('/indices/usdkrw')}
    />
  )
}

// 코스피/코스닥/나스닥/S&P500/SOXX·환율·비트코인·美 10년물 금리 전부
// 실데이터다(네이버 금융·토스·Upbit·TradingView). "15분 지연" 같은
// 라벨은 붙이지 않는다 - 네이버 금융 API 응답의 delayTime/delayTimeName이
// 이 7개 전부 "실시간"(0)으로 내려온다(실제 호출로 확인). 지연 표시가
// 흔한 건 대개 해외 상품(금·WTI 등)인데 QuantLime은 아직 그 데이터를
// 다루지 않는다. 국채금리는 토스·네이버 어디에도 없는 심볼이라
// TradingView 공개 스캐너 API로 보완했다(인베스팅닷컴은 Cloudflare
// 봇 차단으로 서버 간 호출이 아예 불가능해 제외). 프론트는 8초
// 주기로 재조회한다(백엔드 캐시 TTL과 동일, useMarketIndicesQuery 참고).
export function MarketIndexRow() {
  const { data } = useMarketIndicesQuery()

  return (
    <section className="rounded-2xl border border-gray-100 bg-gradient-to-br from-white to-gray-50 p-3">
      <p className="mb-2 text-xs font-medium text-gray-400">주요 지수 · 전부 실시간 데이터예요</p>
      {/* 지수류(국내→해외)를 먼저 묶고 환율·코인은 뒤로 - 2026-07-16,
          이전엔 환율·코인이 맨 앞이라 "지수 카드"라는 제목과 안 맞았음. */}
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <KrIndexCard code="KOSPI" label="코스피" quote={data?.kospi} />
        <KrIndexCard code="KOSDAQ" label="코스닥" quote={data?.kosdaq} />
        <WorldIndexCard code="NASDAQ" quote={data?.nasdaq} />
        <WorldIndexCard code="SP500" quote={data?.sp500} />
        <WorldIndexCard code="SOXX" quote={data?.soxx} />
        <TreasuryYieldCard
          yieldValue={data?.usTreasuryYield10y ?? null}
          changeRate={data?.usTreasuryYield10yChangeRate ?? null}
          history={data?.usTreasuryYield10yHistory ?? []}
        />
        <UsdCard rate={data?.usdKrwRate ?? null} changeRate={data?.usdKrwChangeRate ?? null} />
        <BitcoinCard priceKrw={data?.bitcoinPriceKrw ?? null} changeRate={data?.bitcoinChangeRate ?? null} />
      </div>
    </section>
  )
}
