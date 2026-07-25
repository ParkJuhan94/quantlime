import type { DailyScoreResponse } from '../../types/backtest'
import { QUADRANT_LINE_COLORS } from '../../utils/quadrantStyle'
import { formatPrice } from '../../utils/priceFormat'

// lightweight-charts(CandleChart)는 캔들+거래일 문자열 축에 맞춰 설계돼 있어
// "가격 + 0~100 스코어 두 축 + 사분면 리본"을 함께 그리기엔 맞지 않는다 -
// 종목상세의 SimpleLineChart(단일 폴리라인)와 같은 계열로, 이 화면 전용
// SVG를 직접 그린다. 가격/스코어를 같은 픽셀에 포개면 단위가 달라(원 vs
// 0~100점) 오독하기 쉬워, 같은 x축을 공유하는 두 패널로 위아래에 쌓는다
// (거래 플랫폼이 가격 아래 MACD/RSI 서브패널을 두는 것과 같은 방식).
const WIDTH = 800
const PRICE_HEIGHT = 180
const SCORE_HEIGHT = 90
const RIBBON_HEIGHT = 14
const GAP = 8
const TOTAL_HEIGHT = PRICE_HEIGHT + GAP + SCORE_HEIGHT + GAP + RIBBON_HEIGHT + 20

function buildSegments(
  values: (number | null)[],
  min: number,
  max: number,
  height: number,
): string[] {
  const n = values.length
  const stepX = WIDTH / Math.max(n - 1, 1)
  const range = max - min || 1
  const segments: string[] = []
  let current: string[] = []
  values.forEach((value, i) => {
    if (value == null) {
      if (current.length > 1) segments.push(current.join(' '))
      current = []
      return
    }
    const x = i * stepX
    const y = height - ((value - min) / range) * height
    current.push(`${x.toFixed(1)},${y.toFixed(1)}`)
  })
  if (current.length > 1) segments.push(current.join(' '))
  return segments
}

export function BacktestPriceScoreChart({ dailyScores }: { dailyScores: DailyScoreResponse[] }) {
  if (dailyScores.length < 2) return null

  const closes = dailyScores.map((d) => d.closePrice)
  const priceMin = Math.min(...closes)
  const priceMax = Math.max(...closes)
  const priceSegments = buildSegments(closes, priceMin, priceMax, PRICE_HEIGHT)

  const trendSegments = buildSegments(
    dailyScores.map((d) => d.trendScore),
    0,
    100,
    SCORE_HEIGHT,
  )
  const meanReversionSegments = buildSegments(
    dailyScores.map((d) => d.meanReversionScore),
    0,
    100,
    SCORE_HEIGHT,
  )

  const stepX = WIDTH / Math.max(dailyScores.length - 1, 1)
  const firstDate = dailyScores[0].tradeDate
  const lastDate = dailyScores[dailyScores.length - 1].tradeDate
  const midDate = dailyScores[Math.floor(dailyScores.length / 2)].tradeDate

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4">
      <div className="mb-2 flex flex-wrap items-center gap-4 text-xs text-gray-500">
        <LegendDot color="#111827" label="종가" />
        <LegendDot color="#7c3aed" label="추세추종 점수" />
        <LegendDot color="#0d9488" label="평균회귀 점수" />
        <span className="text-gray-400">최저 {formatPrice(priceMin)} · 최고 {formatPrice(priceMax)}</span>
      </div>

      <svg width="100%" viewBox={`0 0 ${WIDTH} ${TOTAL_HEIGHT}`} preserveAspectRatio="none">
        {/* 가격 패널 */}
        {priceSegments.map((points, i) => (
          <polyline key={i} points={points} fill="none" stroke="#111827" strokeWidth="1.5" />
        ))}

        {/* 스코어 패널(0~100) */}
        <g transform={`translate(0, ${PRICE_HEIGHT + GAP})`}>
          <line x1={0} y1={SCORE_HEIGHT / 2} x2={WIDTH} y2={SCORE_HEIGHT / 2} stroke="#e5e7eb" strokeDasharray="4 4" />
          {trendSegments.map((points, i) => (
            <polyline key={`trend-${i}`} points={points} fill="none" stroke="#7c3aed" strokeWidth="1.5" />
          ))}
          {meanReversionSegments.map((points, i) => (
            <polyline key={`mr-${i}`} points={points} fill="none" stroke="#0d9488" strokeWidth="1.5" />
          ))}
        </g>

        {/* 사분면 리본 - 하루 단위 색 막대. 워밍업 등 사분면 없음(null)은 회색 */}
        <g transform={`translate(0, ${PRICE_HEIGHT + GAP + SCORE_HEIGHT + GAP})`}>
          {dailyScores.map((d, i) => (
            <rect
              key={i}
              x={i * stepX}
              y={0}
              width={Math.max(stepX, 1)}
              height={RIBBON_HEIGHT}
              fill={d.quadrant ? QUADRANT_LINE_COLORS[d.quadrant] ?? '#d1d5db' : '#e5e7eb'}
            />
          ))}
        </g>

        {/* x축 날짜 라벨 3개(처음/중간/끝) */}
        <text x={0} y={TOTAL_HEIGHT} fontSize="11" fill="#9ca3af">{firstDate}</text>
        <text x={WIDTH / 2} y={TOTAL_HEIGHT} fontSize="11" fill="#9ca3af" textAnchor="middle">{midDate}</text>
        <text x={WIDTH} y={TOTAL_HEIGHT} fontSize="11" fill="#9ca3af" textAnchor="end">{lastDate}</text>
      </svg>
      <p className="mt-1 text-[11px] text-gray-400">하단 리본 색상은 그날의 사분면(추세추종 x 평균회귀)을 나타냅니다.</p>
    </div>
  )
}

function LegendDot({ color, label }: { color: string; label: string }) {
  return (
    <span className="flex items-center gap-1">
      <span className="inline-block h-2 w-2 rounded-full" style={{ backgroundColor: color }} />
      {label}
    </span>
  )
}
