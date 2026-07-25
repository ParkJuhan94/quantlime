import type { DailyScoreResponse } from '../../types/backtest'
import { QUADRANT_BADGE_STYLES, QUADRANT_ORDER } from '../../utils/quadrantStyle'

export function QuadrantSummary({ dailyScores }: { dailyScores: DailyScoreResponse[] }) {
  const withQuadrant = dailyScores.filter((d) => d.quadrant != null)
  const total = withQuadrant.length
  if (total === 0) return null

  const counts = QUADRANT_ORDER.map((label) => ({
    label,
    count: withQuadrant.filter((d) => d.quadrant === label).length,
  }))

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4">
      <p className="mb-3 text-xs font-semibold text-gray-700">사분면 분포(전체 {total}일 중)</p>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        {counts.map(({ label, count }) => (
          <div key={label} className={`rounded-lg p-3 text-center ${QUADRANT_BADGE_STYLES[label]}`}>
            <p className="text-[11px] font-medium">{label}</p>
            <p className="mt-1 text-lg font-bold">{((count / total) * 100).toFixed(0)}%</p>
            <p className="text-[10px] opacity-70">{count}일</p>
          </div>
        ))}
      </div>
    </div>
  )
}
