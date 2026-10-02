import type { ChartInterval } from '../../utils/candleAggregation'

const INTERVAL_OPTIONS: { key: ChartInterval; label: string }[] = [
  { key: 'daily', label: '일봉' },
  { key: 'weekly', label: '주봉' },
  { key: 'monthly', label: '월봉' },
]

// 분봉은 일봉 집계로 만들 수 없어 서버가 온디맨드로 내려주는 별도 데이터다 - 지원하는 화면(종목 상세)만
// allowMinute로 옵션을 켠다(지수 상세 등은 일/주/월봉만).
export type ChartIntervalOrMinute = ChartInterval | 'minute'

interface ChartIntervalSelectorProps<T extends ChartIntervalOrMinute> {
  value: T
  onChange: (interval: T) => void
  allowMinute?: boolean
}

// 제네릭이라 일/주/월봉만 쓰는 화면(지수 상세)은 ChartInterval 상태를 그대로 넘기고, 분봉을 켠
// 화면만 ChartIntervalOrMinute 상태를 넘긴다.
export function ChartIntervalSelector<T extends ChartIntervalOrMinute = ChartInterval>({
  value,
  onChange,
  allowMinute = false,
}: ChartIntervalSelectorProps<T>) {
  const options = (allowMinute ? [{ key: 'minute', label: '분봉' }, ...INTERVAL_OPTIONS] : INTERVAL_OPTIONS) as {
    key: T
    label: string
  }[]
  return (
    <div className="flex gap-1">
      {options.map((option) => (
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
