import { useState } from 'react'
import type { IndicatorSettings } from '../../utils/indicators'

interface LegendItem {
  key: string
  label: string
  color: string
}

// key는 CandleChart의 lineRegistryRef가 시리즈를 등록할 때 쓰는 키와
// 정확히 같은 규칙을 따라야 한다(하이라이트 클릭이 올바른 라인을
// 찾도록) - CandleChart.tsx의 lineRegistryRef.current[...] 대입부 참고.
function buildOverlayItems(indicators: IndicatorSettings): LegendItem[] {
  const items: LegendItem[] = []

  if (indicators.ma) {
    indicators.maLines.forEach((line, index) => {
      if (line.visible) items.push({ key: `ma-${index}`, label: `MA${line.period}`, color: line.color })
    })
  }
  if (indicators.bollingerBands) {
    const { upper, middle, lower } = indicators.bollingerBandsLines
    if (upper.visible) items.push({ key: 'bb-upper', label: 'BB상단', color: upper.color })
    if (middle.visible) items.push({ key: 'bb-middle', label: 'BB중단', color: middle.color })
    if (lower.visible) items.push({ key: 'bb-lower', label: 'BB하단', color: lower.color })
  }
  if (indicators.ichimoku) {
    const { tenkan, kijun, senkouA, senkouB, chikou } = indicators.ichimokuLines
    if (tenkan.visible) items.push({ key: 'ichimoku-tenkan', label: '전환선', color: tenkan.color })
    if (kijun.visible) items.push({ key: 'ichimoku-kijun', label: '기준선', color: kijun.color })
    if (senkouA.visible) items.push({ key: 'ichimoku-senkouA', label: '선행스팬A', color: senkouA.color })
    if (senkouB.visible) items.push({ key: 'ichimoku-senkouB', label: '선행스팬B', color: senkouB.color })
    if (chikou.visible) items.push({ key: 'ichimoku-chikou', label: '후행스팬', color: chikou.color })
  }

  return items
}

function buildSubPanelItems(indicators: IndicatorSettings): LegendItem[] {
  const items: LegendItem[] = []
  if (indicators.macd) {
    const { macd, signal } = indicators.macdLines
    if (macd.visible) items.push({ key: 'macd-macd', label: 'MACD', color: macd.color })
    if (signal.visible) items.push({ key: 'macd-signal', label: 'Signal', color: signal.color })
  }
  return items
}

// 예전엔 각 지표 시리즈의 title이 그대로 y축에 색상 라벨로 찍혀 지표를
// 여러 개 켜면 축이 어지러워졌다(CandleChart는 이제 title을 비워서 그
// 라벨을 그리지 않는다) - 대신 여기서 이름·색상을 별도의 접이식 범례로
// 모아 보여준다. 기본은 접힌 상태로 시작해 평소엔 차트가 깔끔하다.
// 상단 지표(캔들 위 오버레이)는 차트 위쪽에, 하단 지표(MACD, 별도
// 패널)는 차트 아래쪽에 따로 둔다(2026-07-16, "하단 지표는 라벨을
// 아래에" 피드백) - 상단 지표 칩은 클릭하면 해당 라인이 잠깐 굵어지며
// 깜빡이는 하이라이트 애니메이션으로 "이게 이 라인이에요"를 알려준다.
export function OverlayIndicatorLegend({
  indicators,
  onItemClick,
}: {
  indicators: IndicatorSettings
  onItemClick: (key: string) => void
}) {
  const [open, setOpen] = useState(false)
  const items = buildOverlayItems(indicators)

  if (items.length === 0) {
    return <div />
  }

  return (
    <div>
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        className="flex items-center gap-1 text-xs font-medium text-gray-400 hover:text-gray-600"
      >
        지표 라벨
        <svg
          width="12"
          height="12"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          className={`transition-transform duration-200 ${open ? 'rotate-180' : ''}`}
        >
          <path d="m6 9 6 6 6-6" />
        </svg>
      </button>
      {open && (
        <div className="mt-1.5 flex flex-wrap gap-x-1 gap-y-1">
          {items.map((item) => (
            <button
              key={item.key}
              type="button"
              onClick={() => onItemClick(item.key)}
              title="눌러서 차트에서 확인하기"
              className="flex items-center gap-1 rounded-lg px-1 py-0.5 text-xs text-gray-600 transition hover:bg-gray-100"
            >
              <span className="h-2 w-2 shrink-0 rounded-full" style={{ backgroundColor: item.color }} />
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

// 하단 패널(MACD) 지표 라벨 - 차트 바로 아래에 항상 펼쳐서 보여준다(상단
// 지표만큼 개수가 많지 않아 접을 필요가 없다). 클릭 하이라이트는 상단
// 지표에만 적용한다는 요청이라 여기는 클릭 불가능한 정적 라벨로 둔다.
export function SubPanelIndicatorLegend({ indicators }: { indicators: IndicatorSettings }) {
  const items = buildSubPanelItems(indicators)

  if (items.length === 0) return null

  return (
    <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1 border-t border-gray-100 pt-2">
      {items.map((item) => (
        <span key={item.key} className="flex items-center gap-1 text-xs text-gray-500">
          <span className="h-2 w-2 shrink-0 rounded-full" style={{ backgroundColor: item.color }} />
          {item.label}
        </span>
      ))}
    </div>
  )
}
