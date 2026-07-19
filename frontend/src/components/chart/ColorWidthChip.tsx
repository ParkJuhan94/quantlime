import { useState } from 'react'
import { COLOR_PRESETS, LINE_STYLE_PRESETS, WIDTH_PRESETS } from './indicatorPalette'
import type { LineStyleSettings } from '../../utils/indicators'

interface ColorWidthChipProps {
  line: LineStyleSettings
  onChange: (next: LineStyleSettings) => void
}

// 참고 이미지의 색상+굵기 칩 - 현재 색상 동그라미와 "Npx" 라벨을 보여주는
// 작은 버튼이다. 탭하면 컬러 32색 프리셋 + 굵기(실제 선 두께 미리보기)를
// 고르는 바텀시트가 뜬다(ColorWidthBottomSheet).
export function ColorWidthChip({ line, onChange }: ColorWidthChipProps) {
  const [sheetOpen, setSheetOpen] = useState(false)

  return (
    <>
      <button
        type="button"
        onClick={() => setSheetOpen(true)}
        className="flex shrink-0 items-center gap-1.5 rounded-full border border-gray-200 bg-white px-2.5 py-1 text-xs font-medium text-gray-700 shadow-sm transition hover:border-gray-300 hover:bg-gray-50"
      >
        <span className="h-3.5 w-3.5 shrink-0 rounded-full ring-1 ring-black/5" style={{ backgroundColor: line.color }} />
        {line.width}px
      </button>
      <ColorWidthBottomSheet
        open={sheetOpen}
        line={line}
        onChange={onChange}
        onClose={() => setSheetOpen(false)}
      />
    </>
  )
}

function ColorWidthBottomSheet({
  open,
  line,
  onChange,
  onClose,
}: {
  open: boolean
  line: LineStyleSettings
  onChange: (next: LineStyleSettings) => void
  onClose: () => void
}) {
  if (!open) return null

  return (
    <div className="fixed inset-0 z-[80] flex items-end justify-center bg-black/35 sm:items-center" onClick={onClose}>
      <div
        onClick={(event) => event.stopPropagation()}
        className="w-full max-w-sm rounded-t-2xl bg-white p-5 shadow-2xl sm:rounded-2xl"
      >
        <p className="mb-2.5 text-sm font-semibold text-gray-900">컬러</p>
        <div className="mb-5 grid grid-cols-8 gap-2.5">
          {COLOR_PRESETS.map((color) => (
            <button
              key={color}
              type="button"
              aria-label={`색상 ${color}`}
              onClick={() => onChange({ ...line, color })}
              className={`flex h-7 w-7 items-center justify-center rounded-full ring-1 ring-black/5 transition ${
                line.color === color ? 'ring-2 ring-gray-900 ring-offset-2' : ''
              }`}
              style={{ backgroundColor: color }}
            >
              {line.color === color && (
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="3">
                  <path d="M5 13l4 4L19 7" />
                </svg>
              )}
            </button>
          ))}
        </div>

        {/* 굵기·선종류를 세로로 따로 쌓으면 시트가 불필요하게 길어져서,
            좌우 두 칼럼으로 나란히 배치해 높이를 줄인다(2026-07-20 피드백). */}
        <div className="mb-5 grid grid-cols-2 gap-3">
          <div>
            <p className="mb-2.5 text-sm font-semibold text-gray-900">굵기</p>
            <div className="flex flex-col gap-1.5">
              {WIDTH_PRESETS.map((width) => (
                <button
                  key={width}
                  type="button"
                  onClick={() => onChange({ ...line, width })}
                  className={`flex items-center gap-2.5 rounded-lg border px-2.5 py-2.5 text-left transition ${
                    line.width === width ? 'border-gray-900 bg-gray-50' : 'border-gray-200 hover:bg-gray-50'
                  }`}
                >
                  <svg width="36" height="12" viewBox="0 0 36 12" className="shrink-0">
                    <line x1="2" y1="6" x2="34" y2="6" stroke={line.color} strokeWidth={width} strokeLinecap="round" />
                  </svg>
                  <span className="text-sm text-gray-700">{width}px</span>
                </button>
              ))}
            </div>
          </div>

          <div>
            <p className="mb-2.5 text-sm font-semibold text-gray-900">선 종류</p>
            <div className="flex flex-col gap-1.5">
              {LINE_STYLE_PRESETS.map((preset) => (
                <button
                  key={preset.value}
                  type="button"
                  onClick={() => onChange({ ...line, lineStyle: preset.value })}
                  className={`flex items-center gap-2.5 rounded-lg border px-2.5 py-2.5 text-left transition ${
                    line.lineStyle === preset.value ? 'border-gray-900 bg-gray-50' : 'border-gray-200 hover:bg-gray-50'
                  }`}
                >
                  <svg width="36" height="12" viewBox="0 0 36 12" className="shrink-0">
                    <line
                      x1="2"
                      y1="6"
                      x2="34"
                      y2="6"
                      stroke={line.color}
                      strokeWidth={line.width}
                      strokeLinecap="round"
                      strokeDasharray={preset.dashArray}
                    />
                  </svg>
                  <span className="text-sm text-gray-700">{preset.label}</span>
                </button>
              ))}
            </div>
          </div>
        </div>

        <button
          type="button"
          onClick={onClose}
          className="w-full rounded-lg bg-gray-900 py-2.5 text-sm font-semibold text-white hover:bg-gray-800"
        >
          확인
        </button>
      </div>
    </div>
  )
}
