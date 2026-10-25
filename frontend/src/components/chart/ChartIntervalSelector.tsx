import { MINUTE_UNITS, minuteUnitOf, type ChartInterval, type MinuteInterval } from '../../utils/candleAggregation'

const INTERVAL_OPTIONS: { key: ChartInterval; label: string }[] = [
  { key: 'daily', label: '일봉' },
  { key: 'weekly', label: '주봉' },
  { key: 'monthly', label: '월봉' },
]

const DEFAULT_MINUTE_INTERVAL: MinuteInterval = 'minute:1'

// 분봉은 일봉 집계로 만들 수 없어 서버가 온디맨드로 내려주는 별도 데이터다 - 지원하는 화면(종목 상세, 코스피/코스닥,
// 비트코인)만 allowMinute로 옵션을 켠다. 분봉 단위(1·3·5·15·30·60분)는 'minute:5'처럼 한 문자열로 함께 다룬다.
export type ChartIntervalOrMinute = ChartInterval | MinuteInterval

interface ChartIntervalSelectorProps<T extends ChartIntervalOrMinute> {
  value: T
  onChange: (interval: T) => void
  allowMinute?: boolean
}

const BUTTON_BASE = 'rounded-lg px-3 py-1 text-xs font-medium transition'
const ACTIVE = 'bg-gray-900 text-white'
const INACTIVE = 'bg-gray-100 text-gray-600 hover:bg-gray-200'

// 제네릭이라 일/주/월봉만 쓰는 화면은 ChartInterval 상태를 그대로 넘기고, 분봉을 켠 화면만
// ChartIntervalOrMinute 상태를 넘긴다. 분봉을 고르면 단위 선택 줄이 아래에 붙는다.
export function ChartIntervalSelector<T extends ChartIntervalOrMinute = ChartInterval>({
  value,
  onChange,
  allowMinute = false,
}: ChartIntervalSelectorProps<T>) {
  const minuteUnit = minuteUnitOf(value)
  const isMinute = minuteUnit != null

  return (
    <div className="flex flex-col items-end gap-1.5">
      <div className="flex gap-1">
        {allowMinute && (
          <button
            type="button"
            onClick={() => onChange((isMinute ? value : DEFAULT_MINUTE_INTERVAL) as T)}
            className={`${BUTTON_BASE} ${isMinute ? ACTIVE : INACTIVE}`}
          >
            분봉
          </button>
        )}
        {INTERVAL_OPTIONS.map((option) => (
          <button
            key={option.key}
            type="button"
            onClick={() => onChange(option.key as T)}
            className={`${BUTTON_BASE} ${value === option.key ? ACTIVE : INACTIVE}`}
          >
            {option.label}
          </button>
        ))}
      </div>
      {allowMinute && isMinute && (
        <div className="flex gap-1" role="group" aria-label="분봉 단위">
          {MINUTE_UNITS.map((unit) => (
            <button
              key={unit}
              type="button"
              onClick={() => onChange(`minute:${unit}` as T)}
              className={`rounded-md px-2 py-0.5 text-[11px] font-medium transition ${
                minuteUnit === unit ? 'bg-gray-200 text-gray-900' : 'text-gray-500 hover:bg-gray-100'
              }`}
            >
              {unit === 60 ? '1시간' : `${unit}분`}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
