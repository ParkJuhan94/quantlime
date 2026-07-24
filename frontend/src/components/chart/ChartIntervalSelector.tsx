import type { ChartInterval } from '../../utils/candleAggregation'

const INTERVAL_OPTIONS: { key: ChartInterval; label: string }[] = [
  { key: 'daily', label: '일봉' },
  { key: 'weekly', label: '주봉' },
  { key: 'monthly', label: '월봉' },
]

interface ChartIntervalSelectorProps {
  value: ChartInterval
  onChange: (interval: ChartInterval) => void
}

// 분봉은 백엔드가 일봉만 수집해서(§6) 프론트 집계로 만들 수 없다 -
// 별도 수집 파이프라인이 필요해 이번 범위에서 제외했다.
export function ChartIntervalSelector({ value, onChange }: ChartIntervalSelectorProps) {
  return (
    <div className="flex gap-1">
      {INTERVAL_OPTIONS.map((option) => (
        <button
          key={option.key}
          type="button"
          onClick={() => onChange(option.key)}
          className={`rounded-lg px-3 py-1 text-xs font-medium transition ${
            value === option.key ? 'bg-gray-900 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
          }`}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
