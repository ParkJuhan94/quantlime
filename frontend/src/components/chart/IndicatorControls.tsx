import { useState } from 'react'
import type { IndicatorSettings } from '../../utils/indicators'
import { IndicatorSettingsModal } from './IndicatorSettingsModal'

const TOGGLE_KEYS: Array<keyof Pick<IndicatorSettings, 'volume' | 'ma' | 'bollingerBands' | 'ichimoku' | 'macd'>> = [
  'volume', 'ma', 'bollingerBands', 'ichimoku', 'macd',
]

interface IndicatorControlsProps {
  value: IndicatorSettings
  onChange: (next: IndicatorSettings) => void
}

// 예전엔 체크박스 5개짜리 드롭다운을 먼저 열고 거기서 다시 "커스터마이즈"로
// 들어가는 2단 구조였다 - 참고 이미지의 지표 설정 화면 자체가 이미 목록 +
// 토글 + 상세 설정 진입점을 다 갖추고 있어서, 버튼을 누르면 그 화면을
// 곧장 연다(중간 드롭다운 제거).
export function IndicatorControls({ value, onChange }: IndicatorControlsProps) {
  const [settingsOpen, setSettingsOpen] = useState(false)
  const activeCount = TOGGLE_KEYS.filter((key) => value[key]).length

  return (
    <>
      <button
        type="button"
        onClick={() => setSettingsOpen(true)}
        className="flex items-center gap-1.5 rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-600 hover:bg-gray-50"
      >
        보조지표
        <span className="rounded-full bg-gray-100 px-1.5 text-[11px] font-semibold text-gray-500">
          {activeCount}
        </span>
      </button>

      <IndicatorSettingsModal
        open={settingsOpen}
        value={value}
        onClose={() => setSettingsOpen(false)}
        onSave={onChange}
      />
    </>
  )
}
