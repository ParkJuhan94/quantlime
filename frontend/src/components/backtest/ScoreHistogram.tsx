const BIN_COUNT = 10
const BIN_WIDTH = 100 / BIN_COUNT

function buildBins(scores: number[]): number[] {
  const bins = new Array(BIN_COUNT).fill(0)
  scores.forEach((score) => {
    const idx = Math.min(Math.floor(score / BIN_WIDTH), BIN_COUNT - 1)
    bins[idx] += 1
  })
  return bins
}

interface ScoreHistogramProps {
  label: string
  color: string
  scores: (number | null)[]
}

export function ScoreHistogram({ label, color, scores }: ScoreHistogramProps) {
  const valid = scores.filter((s): s is number => s != null)
  if (valid.length === 0) return null
  const bins = buildBins(valid)
  const maxCount = Math.max(...bins)

  return (
    <div>
      <p className="mb-2 text-xs font-semibold text-gray-700">{label} 분포</p>
      <div className="flex h-24 items-end gap-0.5">
        {bins.map((count, i) => (
          <div
            key={i}
            className="flex h-full flex-1 flex-col justify-end"
            title={`${i * BIN_WIDTH}~${(i + 1) * BIN_WIDTH}점: ${count}일`}
          >
            <div
              className="rounded-t"
              style={{
                height: maxCount > 0 ? `${(count / maxCount) * 100}%` : '0%',
                backgroundColor: color,
                minHeight: count > 0 ? 2 : 0,
              }}
            />
          </div>
        ))}
      </div>
      <div className="mt-1 flex justify-between text-[10px] text-gray-400">
        <span>0점</span>
        <span>50점</span>
        <span>100점</span>
      </div>
    </div>
  )
}
