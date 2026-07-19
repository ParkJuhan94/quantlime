// 비트코인 상세 페이지 전용 - Upbit 30분봉(최근 24시간)은 캔들스틱이
// 아니라 촘촘한 시계열이라 CandleChart(lightweight-charts, 영업일 단위
// 문자열만 허용)에 그대로 넣을 수 없다(코스피/코스닥 홈 카드의 당일
// 분봉도 같은 이유로 CandleChart 대신 SVG 라인을 쓴다, MarketIndexRow
// 참고 - 이 컴포넌트는 그걸 상세 페이지 크기로 키운 버전).
const HEIGHT = 240

export function SimpleLineChart({ prices, isUp }: { prices: number[]; isUp: boolean | null }) {
  if (prices.length < 2) return null

  const min = Math.min(...prices)
  const max = Math.min(...prices) === Math.max(...prices) ? min + 1 : Math.max(...prices)
  const range = max - min
  const width = 600
  const stepX = width / (prices.length - 1)
  const points = prices
    .map((value, i) => `${(i * stepX).toFixed(1)},${(HEIGHT - ((value - min) / range) * HEIGHT).toFixed(1)}`)
    .join(' ')
  const strokeColor = isUp === false ? '#2563eb' : isUp === true ? '#dc2626' : '#6b7280'

  return (
    <svg width="100%" height={HEIGHT} viewBox={`0 0 ${width} ${HEIGHT}`} preserveAspectRatio="none">
      <polyline points={points} fill="none" stroke={strokeColor} strokeWidth="2" />
    </svg>
  )
}
