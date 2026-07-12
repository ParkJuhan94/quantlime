import type { IndicatorSettings } from '../../utils/indicators'

const OPTIONS: Array<{ key: keyof IndicatorSettings; label: string }> = [
  { key: 'volume', label: '거래량' },
  { key: 'ma', label: '이동평균(5/10/20/60/120)' },
  { key: 'bollingerBands', label: '볼린저밴드' },
  { key: 'ichimoku', label: '일목균형표' },
  { key: 'macd', label: 'MACD' },
]

interface IndicatorControlsProps {
  value: IndicatorSettings
  onChange: (next: IndicatorSettings) => void
}

export function IndicatorControls({ value, onChange }: IndicatorControlsProps) {
  return (
    <div className="flex flex-wrap gap-x-4 gap-y-1.5">
      {OPTIONS.map((option) => (
        <label key={option.key} className="flex cursor-pointer items-center gap-1.5 text-xs text-gray-600">
          <input
            type="checkbox"
            checked={value[option.key]}
            onChange={(event) => onChange({ ...value, [option.key]: event.target.checked })}
            className="h-3.5 w-3.5 rounded border-gray-300 text-gray-900 focus:ring-gray-400"
          />
          {option.label}
        </label>
      ))}
    </div>
  )
}
