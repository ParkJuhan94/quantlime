import type { VideoFeedTicker } from '../../types/videoFeed'

// GradeBadge(등급 매수/매도 배지)와 동일한 색 규칙을 재사용한다 - 국내
// 주식 상승/하락 관례(빨강/파랑)를 그대로 따르는 방향성 데이터라
// 강조색 없이 색으로만 의미를 전달한다(frontend/CLAUDE.md 참고).
const STANCE_STYLES: Record<string, string> = {
  BULLISH: 'bg-red-100 text-red-700',
  BEARISH: 'bg-blue-100 text-blue-700',
  NEUTRAL: 'bg-gray-200 text-gray-700',
  MENTIONED: 'bg-gray-100 text-gray-600',
}

export function VideoFeedTickerChip({ ticker }: { ticker: VideoFeedTicker }) {
  const style = STANCE_STYLES[ticker.stance] ?? 'bg-gray-100 text-gray-600'
  return (
    <span className={`rounded-lg px-2 py-0.5 text-xs font-semibold ${style}`}>
      {ticker.tickerName ?? ticker.tickerCode}
    </span>
  )
}
