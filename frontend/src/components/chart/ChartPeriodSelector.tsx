const PERIOD_OPTIONS = [30, 90, 180, 365] as const

interface ChartPeriodSelectorProps {
  value: number
  onChange: (days: number) => void
}

export function ChartPeriodSelector({ value, onChange }: ChartPeriodSelectorProps) {
  return (
    <div className="flex gap-1">
      {PERIOD_OPTIONS.map((days) => (
        <button
          key={days}
          type="button"
          onClick={() => onChange(days)}
          className={`rounded-md px-3 py-1 text-xs font-medium transition ${
            value === days ? 'bg-gray-900 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
          }`}
        >
          {days}일
        </button>
      ))}
    </div>
  )
}
