// 비트코인 상세 페이지 전용 - Upbit 30분봉(최근 24시간)은 캔들스틱이
// 아니라 촘촘한 시계열이라 CandleChart(lightweight-charts, 영업일 단위
// 문자열만 허용)에 그대로 넣을 수 없다(코스피/코스닥 홈 카드의 당일
// 분봉도 같은 이유로 CandleChart 대신 SVG 라인을 쓴다, MarketIndexRow
// 참고 - 이 컴포넌트는 그걸 상세 페이지 크기로 키운 버전).
const HEIGHT = 240

// 상승/하락 라인 색(2026-07-30 요청 - 기존 red-600/blue-600보다 밝고
// 선명한 "형광" 톤으로 교체, MarketIndexRow의 미니차트와 동일한 팔레트).
const NEON_RED = '#ff3b30'
const NEON_BLUE = '#0a84ff'

export function SimpleLineChart({ prices, isUp }: { prices: number[]; isUp: boolean | null }) {
  if (prices.length < 2) return null

  const min = Math.min(...prices)
  const max = Math.min(...prices) === Math.max(...prices) ? min + 1 : Math.max(...prices)
  const range = max - min
  const width = 600
  const stepX = width / (prices.length - 1)
  const toY = (value: number) => HEIGHT - ((value - min) / range) * HEIGHT
  const points = prices.map((value, i) => `${(i * stepX).toFixed(1)},${toY(value).toFixed(1)}`).join(' ')
  const strokeColor = isUp === false ? NEON_BLUE : isUp === true ? NEON_RED : '#6b7280'
  // "0%" 기준선 - 시리즈 시작값(prices[0])을 기준으로 지금까지 위/아래로
  // 얼마나 움직였는지 한눈에 보이게 한다(2026-07-30 요청).
  const baselineY = toY(prices[0])

  return (
    <svg width="100%" height={HEIGHT} viewBox={`0 0 ${width} ${HEIGHT}`} preserveAspectRatio="none">
      <line
        x1="0"
        y1={baselineY}
        x2={width}
        y2={baselineY}
        stroke="#9ca3af"
        strokeWidth="1"
        strokeDasharray="4 4"
      />
      <polyline points={points} fill="none" stroke={strokeColor} strokeWidth="4" />
    </svg>
  )
}
