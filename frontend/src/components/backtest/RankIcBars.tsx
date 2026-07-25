import type { HorizonBacktestResponse } from '../../types/backtest'

// Rank IC는 이론상 -1~1이지만 실제로는 훨씬 작은 값(대개 ±0.1~0.3)이라,
// 0을 중심으로 데이터 자체의 최대 절대값에 맞춰 스케일을 잡는다(고정
// -1~1로 그리면 막대가 항상 중앙 근처에 눈에 안 띄게 뭉친다).
const MIN_SCALE = 0.1

export function RankIcBars({ horizons }: { horizons: HorizonBacktestResponse[] }) {
  const maxAbs = Math.max(
    MIN_SCALE,
    ...horizons.flatMap((h) => [h.rankIc, h.rankIcCiLow, h.rankIcCiHigh])
      .filter((v): v is number => v != null)
      .map(Math.abs),
  )

  return (
    <div className="space-y-3">
      {horizons.map((h) => {
        const ic = h.rankIc
        const hasCi = h.rankIcCiLow != null && h.rankIcCiHigh != null
        return (
          <div key={h.horizonDays} className="flex items-center gap-3">
            <span className="w-10 shrink-0 text-xs text-gray-500">{h.horizonDays}일</span>
            <div className="relative h-5 flex-1 rounded bg-gray-100">
              <div className="absolute inset-y-0 left-1/2 w-px bg-gray-300" />
              {hasCi && (
                <div
                  className="absolute inset-y-1 rounded bg-gray-300/70"
                  style={{
                    left: `${50 + ((h.rankIcCiLow as number) / maxAbs) * 50}%`,
                    width: `${(((h.rankIcCiHigh as number) - (h.rankIcCiLow as number)) / maxAbs) * 50}%`,
                  }}
                />
              )}
              {ic != null && (
                <div
                  className={`absolute top-1/2 h-2.5 w-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full ${
                    ic >= 0 ? 'bg-red-600' : 'bg-blue-600'
                  }`}
                  style={{ left: `${50 + (ic / maxAbs) * 50}%` }}
                />
              )}
            </div>
            <span className="w-20 shrink-0 text-right text-xs font-medium text-gray-700">
              {ic != null ? ic.toFixed(3) : '-'}
              <span className="ml-1 text-[10px] font-normal text-gray-400">(N={h.sampleSize})</span>
            </span>
          </div>
        )
      })}
    </div>
  )
}
